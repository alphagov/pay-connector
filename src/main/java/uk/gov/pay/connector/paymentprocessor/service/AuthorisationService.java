package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.commons.model.CardExpiryDate;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;

import javax.inject.Inject;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.String.format;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus;

public class AuthorisationService {

    private static final DateTimeFormatter EXPIRY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/yy");

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final CardExecutorService cardExecutorService;
    private final MetricRegistry metricRegistry;
    private final ChargeService chargeService;
    private final PaymentProviders paymentProviders;

    @Inject
    public AuthorisationService(CardExecutorService cardExecutorService,
                                Environment environment,
                                ChargeService chargeService,
                                PaymentProviders paymentProviders) {
        this.cardExecutorService = cardExecutorService;
        this.metricRegistry = environment.metrics();
        this.chargeService = chargeService;
        this.paymentProviders = paymentProviders;
    }

    public GatewayResponse<BaseAuthoriseResponse> authoriseWalletPayment(String chargeId, WalletAuthorisationData walletAuthorisationData) {
        return executeAuthorise(chargeId, () -> {
            final ChargeEntity charge = prepareChargeForAuthorisation(chargeId);
            GatewayResponse<BaseAuthoriseResponse> operationResponse;
            ChargeStatus newStatus = null;
            String requestStatus = "failure";

            try {
                LOGGER.info("Authorising charge for {}", walletAuthorisationData.getWalletType().toString());
                var authorisationGatewayRequest = WalletAuthorisationGatewayRequest.valueOf(charge, walletAuthorisationData);
                operationResponse = getPaymentProviderFor(charge).authoriseWallet(authorisationGatewayRequest);
                Optional<BaseAuthoriseResponse> baseResponse = operationResponse.getBaseResponse();

                if (baseResponse.isPresent()) {
                    requestStatus = "success";
                    newStatus = baseResponse.get().authoriseStatus().getMappedChargeStatus();
                } else {
                    operationResponse.throwGatewayError();
                }
            } catch (GatewayException e) {

                LOGGER.error("Error occurred authorising charge. Charge external id: {}; message: {}", charge.getExternalId(), e.getMessage());

                if (e instanceof GatewayException.GatewayErrorException) {
                    LOGGER.error("Response from gateway: {}", ((GatewayException.GatewayErrorException) e).getResponseFromGateway());
                }

                newStatus = mapFromGatewayErrorException(e);
                operationResponse = GatewayResponse.GatewayResponseBuilder.responseBuilder().withGatewayError(e.toGatewayError()).build();
            }

            Optional<String> transactionId = extractTransactionId(charge.getExternalId(), operationResponse);
            Optional<ProviderSessionIdentifier> sessionIdentifier = operationResponse.getSessionIdentifier();
            Optional<Auth3dsRequiredEntity> auth3dsDetailsEntity = extractAuth3dsRequiredDetails(operationResponse);

            LOGGER.info("{} authorisation {} - charge_external_id={}, payment provider response={}",
                    walletAuthorisationData.getWalletType().toString(), requestStatus, charge.getExternalId(), operationResponse.toString());

            metricRegistry.counter(format("gateway-operations.%s.%s.%s.authorise.%s.result.%s",
                    charge.getGatewayAccount().getGatewayName(),
                    charge.getGatewayAccount().getType(),
                    charge.getGatewayAccount().getId(),
                    walletAuthorisationData.getWalletType().equals(WalletType.GOOGLE_PAY) ? "google-pay" : "apple-pay",
                    requestStatus)).inc();

            LOGGER.info("Processing gateway auth response for {}", walletAuthorisationData.getWalletType().toString());

            ChargeEntity updatedCharge = chargeService.updateChargePostWalletAuthorisation(
                    charge.getExternalId(),
                    newStatus,
                    transactionId.orElse(null),
                    sessionIdentifier.orElse(null),
                    authCardDetailsFor(walletAuthorisationData),
                    walletAuthorisationData.getWalletType(),
                    walletAuthorisationData.getPaymentInfo().getEmail(),
                    auth3dsDetailsEntity);

            metricRegistry.counter(String.format(
                    "gateway-operations.%s.%s.%s.authorise.result.%s",
                    updatedCharge.getGatewayAccount().getGatewayName(),
                    updatedCharge.getGatewayAccount().getType(),
                    updatedCharge.getGatewayAccount().getId(),
                    newStatus.toString())).inc();

            // Used by saved search
            LOGGER.info("Authorisation for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    charge.getExternalId(), charge.getPaymentGatewayName().getName(),
                    transactionId.orElse("missing transaction ID"),
                    charge.getGatewayAccount().getAnalyticsId(), charge.getGatewayAccount().getId(),
                    operationResponse, ChargeStatus.fromString(charge.getStatus()), newStatus);

            return operationResponse;
        });
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

    private PaymentProvider getPaymentProviderFor(ChargeEntity charge) {
        return paymentProviders.byName(charge.getPaymentGatewayName());
    }

    @Transactional
    private ChargeEntity prepareChargeForAuthorisation(String chargeId) {
        ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, OperationType.AUTHORISATION);
        getPaymentProviderFor(charge).generateTransactionId().ifPresent(charge::setGatewayTransactionId);
        return charge;
    }

    private Optional<Auth3dsRequiredEntity> extractAuth3dsRequiredDetails(GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        return operationResponse.getBaseResponse()
                .flatMap(BaseAuthoriseResponse::getGatewayParamsFor3ds)
                .map(Gateway3dsRequiredParams::toAuth3dsRequiredEntity);
    }

    Optional<String> extractTransactionId(String chargeExternalId, GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        Optional<String> transactionId = operationResponse.getBaseResponse()
                .map(BaseAuthoriseResponse::getTransactionId);
        if (transactionId.isEmpty() || StringUtils.isBlank(transactionId.get())) {
            LOGGER.warn("AuthCardDetails authorisation response received with no transaction id. -  charge_external_id={}",
                    chargeExternalId);
        }
        return transactionId;
    }

    ChargeStatus mapFromGatewayErrorException(GatewayException e) {
        if (e instanceof GatewayException.GenericGatewayException) {
            return AUTHORISATION_ERROR;
        }
        if (e instanceof GatewayException.GatewayConnectionTimeoutException) {
            return AUTHORISATION_TIMEOUT;
        }
        if (e instanceof GatewayException.GatewayErrorException) {
            return AUTHORISATION_UNEXPECTED_ERROR;
        }
        throw new RuntimeException("Unrecognised GatewayException instance " + e.getClass());
    }

    public <T> T executeAuthorise(String chargeId, Supplier<T> authorisationSupplier) {
        Pair<ExecutionStatus, T> executeResult = cardExecutorService.execute(authorisationSupplier);

        switch (executeResult.getLeft()) {
            case COMPLETED:
                return executeResult.getRight();
            case IN_PROGRESS:
                throw new OperationAlreadyInProgressRuntimeException(OperationType.AUTHORISATION.getValue(), chargeId);
            default:
                throw new GenericGatewayRuntimeException("Exception occurred while doing authorisation");
        }
    }

    //TODO move to Card3dsResponseAuthService
    void emitAuthorisationMetric(ChargeEntity charge, String operation) {
        metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.%s.result.%s",
                charge.getGatewayAccount().getGatewayName(),
                charge.getGatewayAccount().getType(),
                charge.getGatewayAccount().getId(),
                operation,
                charge.getStatus())
        ).inc();
    }
}
