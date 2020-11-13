package uk.gov.pay.connector.wallets;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.commons.model.CardExpiryDate;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static java.lang.String.format;

public class WalletAuthoriseService {
    private static final DateTimeFormatter EXPIRY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/yy");
    private final AuthorisationService authorisationService;
    private final ChargeService chargeService;
    private final PaymentProviders paymentProviders;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private MetricRegistry metricRegistry;

    @Inject
    public WalletAuthoriseService(PaymentProviders paymentProviders,
                                  ChargeService chargeService,
                                  AuthorisationService authorisationService,
                                  Environment environment) {
        this.paymentProviders = paymentProviders;
        this.authorisationService = authorisationService;
        this.chargeService = chargeService;
        this.metricRegistry = environment.metrics();
    }

    public GatewayResponse<BaseAuthoriseResponse> doAuthorise(String chargeId, WalletAuthorisationData walletAuthorisationData) {
        return authorisationService.executeAuthorise(chargeId, () -> {
            final ChargeEntity charge = prepareChargeForAuthorisation(chargeId);
            GatewayResponse<BaseAuthoriseResponse> operationResponse;
            Optional<String> transactionId = Optional.empty();
            Optional<ProviderSessionIdentifier> sessionIdentifier = Optional.empty();
            Optional<Auth3dsRequiredEntity> auth3dsDetailsEntity = Optional.empty();
            ChargeStatus chargeStatus = null;
            String requestStatus = "failure";

            try {
                operationResponse = authorise(charge, walletAuthorisationData);

                if (operationResponse.getBaseResponse().isPresent()) {
                    requestStatus = "success";
                    chargeStatus = operationResponse.getBaseResponse().get().authoriseStatus().getMappedChargeStatus();
                    transactionId = authorisationService.extractTransactionId(charge.getExternalId(), operationResponse);
                    sessionIdentifier = operationResponse.getSessionIdentifier();
                    auth3dsDetailsEntity = operationResponse.getBaseResponse().get().extractAuth3dsRequiredDetails();
                } else {
                    operationResponse.throwGatewayError();
                }

            } catch (GatewayException e) {

                logger.error("Error occurred authorising charge. Charge external id: {}; message: {}", charge.getExternalId(), e.getMessage());

                if (e instanceof GatewayErrorException) {
                    logger.error("Response from gateway: {}", ((GatewayErrorException) e).getResponseFromGateway());
                }

                chargeStatus = AuthorisationService.mapFromGatewayErrorException(e);
                operationResponse = GatewayResponse.GatewayResponseBuilder.responseBuilder().withGatewayError(e.toGatewayError()).build();
            }

            logMetrics(charge, operationResponse, requestStatus, walletAuthorisationData.getWalletType());

            processGatewayAuthorisationResponse(
                    charge.getExternalId(),
                    walletAuthorisationData,
                    transactionId.orElse(null),
                    sessionIdentifier.orElse(null),
                    chargeStatus,
                    auth3dsDetailsEntity);

            // Used by Splunk saved search
            logger.info("Authorisation for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    charge.getExternalId(), charge.getPaymentGatewayName().getName(),
                    transactionId.orElse("missing transaction ID"),
                    charge.getGatewayAccount().getAnalyticsId(), charge.getGatewayAccount().getId(),
                    operationResponse, ChargeStatus.fromString(charge.getStatus()), chargeStatus);

            return operationResponse;
        });
    }

    private void logMetrics(ChargeEntity chargeEntity,
                            GatewayResponse<BaseAuthoriseResponse> operationResponse,
                            String successOrFailure,
                            WalletType walletType) {

        logger.info("{} authorisation {} - charge_external_id={}, payment provider response={}",
                walletType.toString(), successOrFailure, chargeEntity.getExternalId(), operationResponse.toString());
        metricRegistry.counter(format("gateway-operations.%s.%s.%s.authorise.%s.result.%s",
                chargeEntity.getGatewayAccount().getGatewayName(),
                chargeEntity.getGatewayAccount().getType(),
                chargeEntity.getGatewayAccount().getId(),
                walletType.equals(WalletType.GOOGLE_PAY) ? "google-pay" : "apple-pay",
                successOrFailure)).inc();
    }

    @Transactional
    private ChargeEntity prepareChargeForAuthorisation(String chargeId) {
        ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION);
        getPaymentProviderFor(charge)
                .generateTransactionId()
                .ifPresent(charge::setGatewayTransactionId);
        return charge;
    }

    private void processGatewayAuthorisationResponse(
            String chargeExternalId,
            WalletAuthorisationData walletAuthorisationData,
            String transactionId,
            ProviderSessionIdentifier sessionIdentifier,
            ChargeStatus status,
            Optional<Auth3dsRequiredEntity> auth3dsRequiredDetails) {

        logger.info("Processing gateway auth response for {}", walletAuthorisationData.getWalletType().toString());
        AuthCardDetails authCardDetailsToBePersisted = authCardDetailsFor(walletAuthorisationData);
        ChargeEntity updatedCharge = chargeService.updateChargePostWalletAuthorisation(
                chargeExternalId,
                status,
                transactionId,
                sessionIdentifier,
                authCardDetailsToBePersisted,
                walletAuthorisationData.getWalletType(),
                walletAuthorisationData.getPaymentInfo().getEmail(),
                auth3dsRequiredDetails);

        metricRegistry.counter(String.format(
                "gateway-operations.%s.%s.%s.authorise.result.%s",
                updatedCharge.getGatewayAccount().getGatewayName(),
                updatedCharge.getGatewayAccount().getType(),
                updatedCharge.getGatewayAccount().getId(),
                status.toString())).inc();
    }

    private GatewayResponse<BaseAuthoriseResponse> authorise(ChargeEntity chargeEntity, WalletAuthorisationData walletAuthorisationData)
            throws GatewayException {

        logger.info("Authorising charge for {}", walletAuthorisationData.getWalletType().toString());
        var authorisationGatewayRequest = WalletAuthorisationGatewayRequest.valueOf(chargeEntity, walletAuthorisationData);
        return getPaymentProviderFor(chargeEntity).authoriseWallet(authorisationGatewayRequest);
    }

    private AuthCardDetails authCardDetailsFor(WalletAuthorisationData walletAuthorisationData) {
        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardHolder(walletAuthorisationData.getPaymentInfo().getCardholderName());
        authCardDetails.setCardNo(walletAuthorisationData.getPaymentInfo().getLastDigitsCardNumber());
        authCardDetails.setPayersCardType(walletAuthorisationData.getPaymentInfo().getCardType());
        authCardDetails.setCardBrand(walletAuthorisationData.getPaymentInfo().getBrand());
        walletAuthorisationData.getCardExpiryDate().map(EXPIRY_DATE_FORMAT::format).map(CardExpiryDate::valueOf).ifPresent(authCardDetails::setEndDate);
        authCardDetails.setCorporateCard(false);
        return authCardDetails;
    }

    private PaymentProvider getPaymentProviderFor(ChargeEntity chargeEntity) {
        return paymentProviders.byName(chargeEntity.getPaymentGatewayName());
    }
}
