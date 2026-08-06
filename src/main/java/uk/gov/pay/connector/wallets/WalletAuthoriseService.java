package uk.gov.pay.connector.wallets;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import io.dropwizard.core.setup.Environment;
import io.prometheus.client.Counter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.adyen.AdyenPaymentProvider;
import uk.gov.pay.connector.gateway.model.ApplePayAuthoriseRequestFactory;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.request.records.AdyenApplePayAuthorisePayload;
import uk.gov.pay.connector.gateway.model.request.records.WalletAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Optional;

import static java.lang.String.format;

public class WalletAuthoriseService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WalletAuthoriseService.class);
    private final AuthorisationService authorisationService;
    private final ApplePayAuthoriseRequestFactory applePayAuthoriseRequestFactory;
    private final ChargeService chargeService;
    private final PaymentProviders paymentProviders;
    private final WalletPaymentInfoToAuthCardDetailsConverter walletPaymentInfoToAuthCardDetailsConverter;
    private final AuthorisationLogger authorisationLogger;
    private final MetricRegistry metricRegistry;
    
    private record RequestAndResponse(
            @Nullable WalletAuthoriseRequest request,
            @NonNull GatewayResponse<BaseAuthoriseResponse> response
    ) {}

    private static final Counter walletPaymentAuthorisationSuccessCounter = Counter.build()
            .name("wallet_payments_authorisation_total")
            .help("Count of wallet payments authorisation")
            .labelNames("gatewayName", "gatewayAccountType", "walletType", "successOrFailure")
            .register();

    @Inject
    public WalletAuthoriseService(PaymentProviders paymentProviders,
                                  ChargeService chargeService,
                                  AuthorisationService authorisationService,
                                  ApplePayAuthoriseRequestFactory applePayAuthoriseRequestFactory,
                                  WalletPaymentInfoToAuthCardDetailsConverter walletPaymentInfoToAuthCardDetailsConverter,
                                  AuthorisationLogger authorisationLogger,
                                  Environment environment) {
        this.paymentProviders = paymentProviders;
        this.authorisationService = authorisationService;
        this.applePayAuthoriseRequestFactory = applePayAuthoriseRequestFactory;
        this.walletPaymentInfoToAuthCardDetailsConverter = walletPaymentInfoToAuthCardDetailsConverter;
        this.chargeService = chargeService;
        this.authorisationLogger = authorisationLogger;
        this.metricRegistry = environment.metrics();
    }

    public GatewayResponse<BaseAuthoriseResponse> authorise(String chargeId, WalletAuthorisationRequest walletAuthorisationRequest) {
        return authorisationService.executeAuthorise(chargeId, () -> {
            final ChargeEntity charge = prepareChargeForAuthorisation(chargeId);
            RequestAndResponse requestAndResponse = null;
            ChargeStatus chargeStatus = null;
            String requestStatus = "failure";

            try {

                LOGGER.info("Authorising charge for {}", walletAuthorisationRequest.getWalletType().toString());
                requestAndResponse = switch (walletAuthorisationRequest.getWalletType()) {
                    case APPLE_PAY -> authoriseApplePay(charge, walletAuthorisationRequest);
                    case GOOGLE_PAY -> authoriseGooglePay(charge, walletAuthorisationRequest);
                };

                if (requestAndResponse.response().getBaseResponse().isPresent()) {
                    requestStatus = "success";
                    chargeStatus = requestAndResponse.response().getBaseResponse().get().authoriseStatus().getMappedChargeStatus();
                } else {
                    requestAndResponse.response().throwGatewayError();
                }

            } catch (GatewayException e) {

                LOGGER.info("Error occurred authorising charge. Charge external id: {}; message: {}", charge.getExternalId(), e.getMessage());

                if (e instanceof GatewayErrorException) {
                    LOGGER.error("Response from gateway: {}", ((GatewayErrorException) e).getResponseFromGateway());
                }

                chargeStatus = AuthorisationService.mapFromGatewayErrorException(e);
                requestAndResponse = new RequestAndResponse(
                        Optional.ofNullable(requestAndResponse).map(RequestAndResponse::request).orElse(null),
                        GatewayResponseBuilder.<BaseAuthoriseResponse>responseBuilder()
                                .withGatewayError(e.toGatewayError())
                                .build());
            }
            
            GatewayResponse<BaseAuthoriseResponse> operationResponse = requestAndResponse.response();

            Optional<String> transactionId = authorisationService.extractTransactionId(charge.getExternalId(), operationResponse, charge.getGatewayTransactionId());
            Optional<ProviderSessionIdentifier> sessionIdentifier = operationResponse.getSessionIdentifier();
            Optional<Auth3dsRequiredEntity> auth3dsDetailsEntity = operationResponse.getBaseResponse().flatMap(BaseAuthoriseResponse::extractAuth3dsRequiredDetails);
            CardExpiryDate cardExpiryDate = operationResponse.getBaseResponse().flatMap(BaseAuthoriseResponse::getCardExpiryDate).orElse(null);
            AuthCardDetails authCardDetails = walletPaymentInfoToAuthCardDetailsConverter.convert(walletAuthorisationRequest.getPaymentInfo(), cardExpiryDate);

            logMetrics(charge, operationResponse, requestStatus, chargeStatus, walletAuthorisationRequest.getWalletType());

            processGatewayAuthorisationResponse(
                    charge.getExternalId(),
                    walletAuthorisationRequest,
                    transactionId.orElse(null),
                    sessionIdentifier.orElse(null),
                    chargeStatus,
                    auth3dsDetailsEntity.orElse(null),
                    authCardDetails,
                    cardExpiryDate);
            
            if (requestAndResponse.request() == null) {
                authorisationLogger.logChargeAuthorisation(
                        LOGGER,
                        charge,
                        transactionId.orElse("missing transaction ID"),
                        operationResponse,
                        charge.getChargeStatus(),
                        chargeStatus
                );
            } else {
                authorisationLogger.logChargeAuthorisation(
                        LOGGER,
                        requestAndResponse.request(),
                        authCardDetails,
                        charge,
                        transactionId.orElse("missing transaction ID"),
                        operationResponse,
                        charge.getChargeStatus(),
                        chargeStatus
                );
            }

            return operationResponse;
        });
    }

    private void logMetrics(ChargeEntity chargeEntity,
                            GatewayResponse<BaseAuthoriseResponse> operationResponse,
                            String requestStatus,
                            ChargeStatus chargeStatus,
                            WalletType walletType) {

        String successOrFailureMetricLabel = switch (chargeStatus) {
            case AUTHORISATION_SUCCESS, AUTHORISATION_3DS_REQUIRED -> "success";
            default -> "failure";
        };

        String walletTypeMetricLabel = switch (walletType) {
            case APPLE_PAY -> "apple-pay";
            case GOOGLE_PAY -> "google-pay";
        };

        LOGGER.info("{} authorisation - charge status={}, request status={}, charge_external_id={}, payment provider response={}",
                walletType, chargeStatus, requestStatus, chargeEntity.getExternalId(), operationResponse.toString());
        metricRegistry.counter(format("gateway-operations.%s.%s.authorise.%s.result.%s",
                chargeEntity.getPaymentProvider(),
                chargeEntity.getGatewayAccount().getType(),
                walletTypeMetricLabel,
                successOrFailureMetricLabel)).inc();
        walletPaymentAuthorisationSuccessCounter
                .labels(
                    chargeEntity.getPaymentProvider(),
                    chargeEntity.getGatewayAccount().getType(),
                    walletTypeMetricLabel,
                    successOrFailureMetricLabel
                ).inc();
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
            WalletAuthorisationRequest walletAuthorisationRequest,
            String transactionId,
            ProviderSessionIdentifier sessionIdentifier,
            ChargeStatus status,
            Auth3dsRequiredEntity auth3dsRequiredDetails,
            AuthCardDetails authCardDetails,
            CardExpiryDate cardExpiryDate) {
        
        LOGGER.info("Processing gateway auth response for {}", walletAuthorisationRequest.getWalletType().toString());
        
        ChargeEntity updatedCharge = chargeService.updateChargePostWalletAuthorisation(
                chargeExternalId,
                status,
                transactionId,
                sessionIdentifier,
                authCardDetails,
                walletAuthorisationRequest.getWalletType(),
                walletAuthorisationRequest.getPaymentInfo().getEmail(),
                auth3dsRequiredDetails);

        metricRegistry.counter(String.format(
                "gateway-operations.%s.%s.%s.authorise.result.%s",
                updatedCharge.getPaymentProvider(),
                updatedCharge.getGatewayAccount().getType(),
                updatedCharge.getGatewayAccount().getId(),
                status.toString())).inc();
    }

    private RequestAndResponse authoriseApplePay(ChargeEntity chargeEntity, WalletAuthorisationRequest walletAuthorisationRequest)
            throws GatewayException {
        var authorisationGatewayRequest = ApplePayAuthorisationGatewayRequest.valueOf
                (chargeEntity, (ApplePayAuthRequest) walletAuthorisationRequest);

        
        var applePayAuthoriseRequest = applePayAuthoriseRequestFactory.create(authorisationGatewayRequest).orElse(null);
        PaymentProvider paymentProvider = getPaymentProviderFor(chargeEntity);
        
        GatewayResponse<BaseAuthoriseResponse> response = switch (applePayAuthoriseRequest) {
            case AdyenApplePayAuthorisePayload adyenApplePayAuthoriseRequest when paymentProvider instanceof AdyenPaymentProvider ->
                    paymentProvider.authoriseApplePay(
                            adyenApplePayAuthoriseRequest, 
                            authorisationGatewayRequest.getGatewayAccount().getGatewayAccountType());
            case null, default -> getPaymentProviderFor(chargeEntity).authoriseApplePay(authorisationGatewayRequest);
        };
        
        return new RequestAndResponse(applePayAuthoriseRequest, response);
    }

    private RequestAndResponse authoriseGooglePay(ChargeEntity chargeEntity, WalletAuthorisationRequest walletAuthorisationRequest)
            throws GatewayException {
        var authorisationGatewayRequest = GooglePayAuthorisationGatewayRequest.valueOf
                (chargeEntity, (GooglePayAuthRequest) walletAuthorisationRequest);
        GatewayResponse<BaseAuthoriseResponse> response = getPaymentProviderFor(chargeEntity).authoriseGooglePay(authorisationGatewayRequest);

        return new RequestAndResponse(null, response);
    }

    private PaymentProvider getPaymentProviderFor(ChargeEntity chargeEntity) {
        return paymentProviders.byName(chargeEntity.getPaymentGatewayName());
    }
}
