package uk.gov.pay.connector.paymentprocessor.service;

import jakarta.inject.Inject;
import net.logstash.logback.argument.StructuredArgument;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.Authorisation3dsConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;

import java.util.Locale;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.paymentprocessor.model.OperationType.AUTHORISATION_3DS;
import static uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService.TypeOf3dsRequest.NONE_OF_THE_ABOVE;
import static uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService.TypeOf3dsRequest.WORLDPAY_3DS_CLASSIC;
import static uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService.TypeOf3dsRequest.WORLDPAY_3DS_FLEX;

public class Card3dsResponseAuthService {

    enum TypeOf3dsRequest {
        WORLDPAY_3DS_CLASSIC(" using 3DS"), WORLDPAY_3DS_FLEX(" using 3DS Flex"), NONE_OF_THE_ABOVE("");

        public final String logMessage;

        TypeOf3dsRequest(String logMessage) {
            this.logMessage = logMessage;
        }

        public String getLogMessage() {
            return logMessage;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Card3dsResponseAuthService.class);
    public static final String INTEGRATION_3DS_VERSION = "integration_3ds_version";

    private final ChargeService chargeService;
    private final AuthorisationService authorisationService;
    private final PaymentProviders providers;
    private final Authorisation3dsConfig authorisation3dsConfig;

    @Inject
    public Card3dsResponseAuthService(PaymentProviders providers,
                                      ChargeService chargeService,
                                      AuthorisationService authorisationService,
                                      ConnectorConfiguration config) {
        this.providers = providers;
        this.chargeService = chargeService;
        this.authorisationService = authorisationService;
        this.authorisation3dsConfig = config.getAuthorisation3dsConfig();
    }

    public Gateway3DSAuthorisationResponse process3DSecureAuthorisation(String chargeId, Auth3dsResult auth3DsResult) {
        return authorisationService.executeAuthorise(chargeId, () -> {

            final ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, AUTHORISATION_3DS);
            return authoriseAndProcess3DS(auth3DsResult, charge);
        });
    }

    public Gateway3DSAuthorisationResponse process3DSecureAuthorisationWithoutLocking(String chargeId, Auth3dsResult auth3DsResult) {
        return authorisationService.executeAuthorise(chargeId, () -> {
            final ChargeEntity charge = chargeService.findChargeByExternalId(chargeId);
            return authoriseAndProcess3DS(auth3DsResult, charge);
        });
    }

    private Gateway3DSAuthorisationResponse authoriseAndProcess3DS(Auth3dsResult auth3dsResult, ChargeEntity charge) {
        Gateway3DSAuthorisationResponse gateway3DSAuthorisationResponse = providers
                .byName(charge.getPaymentGatewayName())
                .authorise3dsResponse(Auth3dsResponseGatewayRequest.valueOf(charge, auth3dsResult));

        if (auth3dsResult != null && StringUtils.isNotBlank(auth3dsResult.getPaResponse())) {
            if (auth3dsResult.getPaResponse().length() <= 50) {
                LOGGER.info("3DS authorisation - PaRes '{}'", auth3dsResult.getPaResponse());
            } else {
                LOGGER.info("3DS authorisation - PaRes starts with '{}' and ending '{}'",
                        auth3dsResult.getPaResponse().substring(0, 50),
                        auth3dsResult.getPaResponse().substring(auth3dsResult.getPaResponse().length() - 50));
            }
        }

        processGateway3DSecureResponse(charge.getExternalId(), ChargeStatus.fromString(charge.getStatus()), gateway3DSAuthorisationResponse, auth3dsResult);
        return gateway3DSAuthorisationResponse;
    }

    private void processGateway3DSecureResponse(String chargeExternalId, ChargeStatus oldChargeStatus, Gateway3DSAuthorisationResponse operationResponse, Auth3dsResult auth3dsResult) {
        Optional<String> transactionId = operationResponse.getTransactionId();

        ChargeStatus newStatus = operationResponse.getMappedChargeStatus();
        if (newStatus == AUTHORISATION_3DS_REQUIRED) {
            int numberOf3dsRequiredEventsRecorded = chargeService.count3dsRequiredEvents(chargeExternalId);
            if (numberOf3dsRequiredEventsRecorded < authorisation3dsConfig.getMaximumNumberOfTimesToAllowUserToAttempt3ds()) {
                LOGGER.info("Gateway instructed us to send the user through 3DS again for {} — this will be attempt {} of {}",
                        chargeExternalId, numberOf3dsRequiredEventsRecorded + 1, authorisation3dsConfig.getMaximumNumberOfTimesToAllowUserToAttempt3ds());
            } else {
                newStatus = AUTHORISATION_REJECTED;
                LOGGER.info("Gateway instructed us to send the user through 3DS again for {} but there have already been {} attempts in total, "
                        + "so treating authorisation as rejected", chargeExternalId, numberOf3dsRequiredEventsRecorded);
            }
        }

        LOGGER.info("3DS response authorisation for {} - {} .'. about to attempt charge update from {} -> {}",
                chargeExternalId,
                operationResponse,
                oldChargeStatus,
                newStatus);

        ChargeEntity updatedCharge = chargeService.updateChargePost3dsAuthorisation(
                chargeExternalId,
                newStatus,
                AUTHORISATION_3DS,
                transactionId.orElse(null),
                operationResponse.getGateway3dsRequiredParams().map(Gateway3dsRequiredParams::toAuth3dsRequiredEntity).orElse(null),
                operationResponse.getProviderSessionIdentifier().orElse(null),
                operationResponse.getGatewayRecurringAuthToken().orElse(null)
        );

        var worldPay3dsOrFlexLogMessage = integration3dsType(auth3dsResult, updatedCharge);
        var structuredLoggingArguments = updatedCharge.getStructuredLoggingArgs();
        if (worldPay3dsOrFlexLogMessage == WORLDPAY_3DS_CLASSIC) {
            structuredLoggingArguments = ArrayUtils.addAll(structuredLoggingArguments, new StructuredArgument[]{kv(INTEGRATION_3DS_VERSION, "3DS")});
        } else if (worldPay3dsOrFlexLogMessage == WORLDPAY_3DS_FLEX) {
            structuredLoggingArguments = ArrayUtils.addAll(structuredLoggingArguments, new StructuredArgument[]{kv(INTEGRATION_3DS_VERSION, "3DS Flex")});
        }

        var logMessage = String.format(Locale.UK, "3DS authentication result%s authorisation for %s (%s %s) for %s (%s) - %s .'. %s -> %s",
                worldPay3dsOrFlexLogMessage.getLogMessage(),
                updatedCharge.getExternalId(),
                updatedCharge.getPaymentGatewayName().getName(),
                updatedCharge.getGatewayTransactionId(),
                updatedCharge.getGatewayAccount().getAnalyticsId(),
                updatedCharge.getGatewayAccount().getId(),
                operationResponse,
                oldChargeStatus,
                updatedCharge.getStatus());

        LOGGER.info(logMessage, structuredLoggingArguments);

        authorisationService.emitAuthorisationMetric(updatedCharge, "authorise-3ds");
    }

    private TypeOf3dsRequest integration3dsType(Auth3dsResult auth3dsResult, ChargeEntity charge) {

        if (PaymentGatewayName.WORLDPAY.equals(charge.getPaymentGatewayName())) {
            return auth3dsResult.getPaResponse() != null ? WORLDPAY_3DS_CLASSIC : WORLDPAY_3DS_FLEX;
        }
        return NONE_OF_THE_ABOVE;
    }
}
