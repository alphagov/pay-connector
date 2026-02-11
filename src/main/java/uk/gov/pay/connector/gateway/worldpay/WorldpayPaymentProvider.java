package uk.gov.pay.connector.gateway.worldpay;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.charge.Gateway3dsExemptionResultObtainedEvent;
import uk.gov.pay.connector.events.model.charge.Requested3dsExemption;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.DeleteStoredPaymentDetailsGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.GatewayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.Worldpay3dsEligibleAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayAuthoriseRequestWithOptional3dsExemption;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayCardNumberAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.requestfactory.WorldpayAuthoriseRequestFactory;
import uk.gov.pay.connector.gateway.requestfactory.WorldpayNo3dsExemptionAuthoriseRequestFactory;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.worldpay.wallets.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.MissingCredentialsForRecurringPaymentException;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.service.RefundEntityFactory;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.WebApplicationException;
import java.net.HttpCookie;
import java.net.URI;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeader;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeaderForManagingRecurringAuthTokens;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayDeleteTokenOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayInquiryRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_HONOURED;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_NOT_REQUESTED;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_OUT_OF_SCOPE;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_REJECTED;

public class WorldpayPaymentProvider implements PaymentProvider, WorldpayGatewayResponseGenerator {

    static final String WORLDPAY_MACHINE_COOKIE_NAME = "machine";

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldpayPaymentProvider.class);
    private static final String WORLDPAY_ORDER_INQUIRY_ERROR_CODE_FOR_INVALID_CONTENT = "error code: 5";
    /**
     * When a payment is not found for the order inquiry, Worldpay returns an error code ‘5’ with the error
     * message 'Could not find payment for order'.
     * See https://developer.worldpay.com/docs/wpg/manage/inquiryrequests for details (Getting an error? heading).
     * For other errors (ex: service unavailable), Worldpay returns a different error code (8) or a different error message
     */
    private static final String WORLDPAY_ORDER_INQUIRY_PAYMENT_NOT_FOUND_ERROR_MESSAGE = "Could not find payment for order";

    private final GatewayClient authoriseClient;
    private final GatewayClient cancelClient;
    private final GatewayClient inquiryClient;
    private final GatewayClient deleteTokenClient;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final WorldpayCaptureHandler worldpayCaptureHandler;
    private final WorldpayRefundHandler worldpayRefundHandler;
    private final WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler;
    private final WorldpayAuthoriseHandler worldpayAuthoriseHandler;
    private final RefundEntityFactory refundEntityFactory;
    private final Map<String, URI> gatewayUrlMap;
    private final AuthorisationService authorisationService;
    private final AuthorisationLogger authorisationLogger;
    private final ChargeDao chargeDao;
    private final EventService eventService;
    private final InstantSource instantSource;
    private final WorldpayAuthoriseRequestFactory worldpayAuthoriseRequestFactory;
    private final WorldpayNo3dsExemptionAuthoriseRequestFactory worldpayNo3dsExemptionAuthoriseRequestFactory;

    @Inject
    public WorldpayPaymentProvider(@Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap,
                                   @Named("WorldpayAuthoriseGatewayClient") GatewayClient authoriseClient,
                                   @Named("WorldpayCancelGatewayClient") GatewayClient cancelClient,
                                   @Named("WorldpayInquiryGatewayClient") GatewayClient inquiryClient,
                                   @Named("WorldpayDeleteTokenGatewayClient") GatewayClient deleteTokenClient,
                                   WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler,
                                   WorldpayAuthoriseHandler worldpayAuthoriseHandler,
                                   WorldpayCaptureHandler worldpayCaptureHandler,
                                   WorldpayRefundHandler worldpayRefundHandler,
                                   @Named("WorldpayRefundEntityFactory") RefundEntityFactory refundEntityFactory,
                                   AuthorisationService authorisationService,
                                   AuthorisationLogger authorisationLogger,
                                   ChargeDao chargeDao,
                                   EventService eventService,
                                   InstantSource instantSource,
                                   WorldpayAuthoriseRequestFactory worldpayAuthoriseRequestFactory,
                                   WorldpayNo3dsExemptionAuthoriseRequestFactory worldpayNo3dsExemptionAuthoriseRequestFactory
    ) {

        this.gatewayUrlMap = gatewayUrlMap;
        this.cancelClient = cancelClient;
        this.inquiryClient = inquiryClient;
        this.authoriseClient = authoriseClient;
        this.deleteTokenClient = deleteTokenClient;
        this.worldpayCaptureHandler = worldpayCaptureHandler;
        this.worldpayRefundHandler = worldpayRefundHandler;
        this.worldpayWalletAuthorisationHandler = worldpayWalletAuthorisationHandler;
        this.worldpayAuthoriseHandler = worldpayAuthoriseHandler;
        this.refundEntityFactory = refundEntityFactory;
        this.authorisationService = authorisationService;
        this.authorisationLogger = authorisationLogger;
        this.chargeDao = chargeDao;
        this.eventService = eventService;
        externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        this.instantSource = instantSource;
        this.worldpayAuthoriseRequestFactory = worldpayAuthoriseRequestFactory;
        this.worldpayNo3dsExemptionAuthoriseRequestFactory = worldpayNo3dsExemptionAuthoriseRequestFactory;
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return WORLDPAY;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.of(newTransactionId());
    }

    private String newTransactionId() {
        return randomUUID().toString();
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeQueryGatewayRequest request) throws GatewayException {
        GatewayClient.Response response = inquiryClient.postRequestFor(
                gatewayUrlMap.get(request.getGatewayAccount().getType()),
                WORLDPAY,
                request.getGatewayAccount().getType(),
                buildQuery(request),
                getWorldpayAuthHeader(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment())
        );
        GatewayResponse<WorldpayQueryResponse> worldpayGatewayResponse = getWorldpayGatewayResponse(response, WorldpayQueryResponse.class);

        return worldpayGatewayResponse.getBaseResponse()
                .map(worldpayQueryResponse -> {
                    ChargeStatus mappedStatus = WorldpayStatus.fromString(worldpayQueryResponse.getLastEvent())
                            .map(WorldpayStatus::getPayStatus)
                            .orElse(null);

                    return new ChargeQueryResponse(mappedStatus, worldpayQueryResponse);
                })
                .or(() -> {
                            return worldpayGatewayResponse.getGatewayError()
                                    .map(gatewayError -> {
                                                if (gatewayError.getMessage() != null
                                                        && gatewayError.getMessage().contains(WORLDPAY_ORDER_INQUIRY_ERROR_CODE_FOR_INVALID_CONTENT)
                                                        && gatewayError.getMessage().contains(WORLDPAY_ORDER_INQUIRY_PAYMENT_NOT_FOUND_ERROR_MESSAGE)
                                                ) {
                                                    return new ChargeQueryResponse(gatewayError);
                                                }
                                                return null;
                                            }
                                    );
                        }
                )
                .orElseThrow(() ->
                        new WebApplicationException(format(
                                "Unable to query charge %s - an error occurred: %s",
                                request.getChargeExternalId(),
                                worldpayGatewayResponse
                        )));
    }

    @Override
    public boolean canQueryPaymentStatus() {
        return true;
    }

    private GatewayOrder buildQuery(ChargeQueryGatewayRequest request) {
        return aWorldpayInquiryRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(),
                        request.getAuthorisationMode(), request.isForRecurringPayment()))
                .build();
    }

    @Override
    public WorldpayCardNumberAuthoriseRequest generateCardNumberAuthorisationRequest(CardAuthorisationGatewayRequest request) {
        return worldpayAuthoriseRequestFactory.newCardNumberRequest(request);
    }
    
    @Override
    public GatewayResponse<WorldpayOrderStatusResponse> authorise(GatewayAuthoriseRequest request, ChargeEntity charge) {
        // Do something nicer with generics to avoid this
        if (!(request instanceof WorldpayAuthoriseRequest req)) {
            throw new IllegalArgumentException();
        }

        GatewayResponse<WorldpayOrderStatusResponse> response  = worldpayAuthoriseHandler.authorise(req, charge.getGatewayAccount().getType());

        calculateAndStoreExemption(charge, req, response);
        
        if (request instanceof WorldpayAuthoriseRequestWithOptional3dsExemption req3dsEligible
                && authorisationWithExemptionRequestSoftDeclinedButRetryableWithoutExemption(req3dsEligible, response)) {
            
            authorisationLogger.logChargeAuthorisation(
                    LOGGER,
                    request,
                    charge,
                    authorisationService.extractTransactionId(charge.getExternalId(), response, charge.getGatewayTransactionId())
                            .orElse("missing transaction ID"),
                    response,
                    charge.getChargeStatus(),
                    charge.getChargeStatus());

            Worldpay3dsEligibleAuthoriseRequest requestWithoutExemption = worldpayNo3dsExemptionAuthoriseRequestFactory.create(req3dsEligible, newTransactionId());
            response = worldpayAuthoriseHandler.authorise(requestWithoutExemption, charge.getGatewayAccount().getType());
        }

        return response;
    }

    private static boolean authorisationWithExemptionRequestSoftDeclinedButRetryableWithoutExemption(WorldpayAuthoriseRequestWithOptional3dsExemption request,
                                                                                                     GatewayResponse<WorldpayOrderStatusResponse> response) {
        boolean corporateExemptionRequested = request.exemption() instanceof WorldpayExemptionRequest(
                WorldpayExemptionRequest.Type type, WorldpayExemptionRequest.Placement placement)
                && type == WorldpayExemptionRequest.Type.CP;

        return !corporateExemptionRequested &&
                response.getBaseResponse().map(WorldpayOrderStatusResponse::isSoftDecline).orElse(false) ;
    }

    /**
     * IMPORTANT: this method should not attempt to update the Charge in the database. This is because it is executed
     * on a worker thread and the initiating thread can attempt to update the Charge status while it is still being
     * executed.
     */
    @Override
    public GatewayResponse authoriseMotoApi(CardAuthorisationGatewayRequest request) {
        return worldpayAuthoriseHandler.authorise(request);
    }

    @Override
    public GatewayResponse authoriseUserNotPresent(RecurringPaymentAuthorisationGatewayRequest request) {
        return worldpayAuthoriseHandler.authoriseUserNotPresent(request);
    }

    private void calculateAndStoreExemption(ChargeEntity charge, WorldpayAuthoriseRequest request,
                                            GatewayResponse<WorldpayOrderStatusResponse> response) {
        boolean exemptionRequested = request instanceof WorldpayAuthoriseRequestWithOptional3dsExemption req3dsEligible && req3dsEligible.exemption() != null;
        if (exemptionRequested) {
            response.getBaseResponse().flatMap(WorldpayOrderStatusResponse::getExemptionResponse).ifPresent(exemptionResponse -> {
                switch (exemptionResponse.result()) {
                    case "HONOURED" ->
                            updateChargeWithExemption3ds(EXEMPTION_HONOURED, charge, exemptionResponse.reason());
                    case "REJECTED" ->
                            updateChargeWithExemption3ds(EXEMPTION_REJECTED, charge, exemptionResponse.reason());
                    case "OUT_OF_SCOPE" ->
                            updateChargeWithExemption3ds(EXEMPTION_OUT_OF_SCOPE, charge, exemptionResponse.reason());
                    default ->
                            LOGGER.warn("Received unrecognised exemption 3ds response result {} from Worldpay - " +
                                    "charge_external_id={}", exemptionResponse, charge.getExternalId());
                }
            });
        } else {
            updateChargeWithExemption3ds(EXEMPTION_NOT_REQUESTED, charge);
        }
    }

    public void updateChargeWithExemption3ds(Exemption3ds exemption3ds, ChargeEntity charge) {
       updateChargeWithExemption3ds(exemption3ds, charge, null);
    }

    @Transactional
    public void updateChargeWithExemption3ds(Exemption3ds exemption3ds, ChargeEntity charge, String reason) {
        charge.setExemption3ds(exemption3ds);
        if (reason == null) {
            LOGGER.info("Updated exemption_3ds of charge to {} - charge_external_id={}", exemption3ds.name(), charge.getExternalId());
        } else {
            LOGGER.info("Updated exemption_3ds of charge to {} (reason {}) - charge_external_id={}", exemption3ds.name(), reason, charge.getExternalId());
        }
        chargeDao.merge(charge);
        eventService.emitAndRecordEvent(Gateway3dsExemptionResultObtainedEvent.from(charge, instantSource.instant()));
    }

    @Transactional
    public ChargeEntity updateChargeWithRequested3dsExemption(ChargeEntity chargeEntity, Exemption3dsType exemption3dsType) {
        chargeEntity.setExemption3dsRequested(exemption3dsType);
        LOGGER.info("Requesting {} exemption - charge_external_id={}", exemption3dsType, chargeEntity.getExternalId());
        eventService.emitAndRecordEvent(Requested3dsExemption.from(chargeEntity, instantSource.instant()));

        return chargeDao.merge(chargeEntity);
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        try {
            List<HttpCookie> cookies = request.getProviderSessionId()
                    .map(providerSessionId -> singletonList(new HttpCookie(WORLDPAY_MACHINE_COOKIE_NAME, providerSessionId.toString())))
                    .orElse(emptyList());

            GatewayClient.Response response = authoriseClient.postRequestFor(
                    gatewayUrlMap.get(request.getGatewayAccount().getType()),
                    WORLDPAY,
                    request.getGatewayAccount().getType(),
                    build3dsResponseAuthOrder(request),
                    cookies,
                    getWorldpayAuthHeader(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()));
            GatewayResponse<WorldpayOrderStatusResponse> gatewayResponse = getWorldpayGatewayResponse(response);

            // calculateAndStoreExemption(request.getCharge(), gatewayResponse);

            LOGGER.info(format("Worldpay 3ds authorisation response for %s : %s", request.getChargeExternalId(), sanitiseMessage(response.getEntity())));

            if (gatewayResponse.getBaseResponse().isEmpty()) {
                gatewayResponse.throwGatewayError();
            }

            BaseAuthoriseResponse authoriseResponse = gatewayResponse.getBaseResponse().get();

            return Gateway3DSAuthorisationResponse.of(gatewayResponse.toString(), authoriseResponse.authoriseStatus(), authoriseResponse.getTransactionId(),
                    authoriseResponse.getGatewayParamsFor3ds().orElse(null), gatewayResponse.getSessionIdentifier().orElse(null),
                    authoriseResponse.getGatewayRecurringAuthToken().orElse(null));
        } catch (GatewayException e) {
            return Gateway3DSAuthorisationResponse.of(e.getMessage(), BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION);
        }
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return worldpayCaptureHandler.capture(request);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseApplePay(ApplePayAuthorisationGatewayRequest authorisationGatewayRequest) throws GatewayException {
        return worldpayWalletAuthorisationHandler.authoriseApplePay(authorisationGatewayRequest);
    }

    public GatewayResponse<BaseAuthoriseResponse> authoriseGooglePay(GooglePayAuthorisationGatewayRequest authorisationGatewayRequest) throws GatewayException {
        return worldpayWalletAuthorisationHandler.authoriseGooglePay(authorisationGatewayRequest);
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        return worldpayRefundHandler.refund(request);
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) throws GatewayException {
        GatewayClient.Response response = cancelClient.postRequestFor(
                gatewayUrlMap.get(request.getGatewayAccount().getType()),
                WORLDPAY,
                request.getGatewayAccount().getType(),
                buildCancelOrder(request),
                getWorldpayAuthHeader(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()));
        return getWorldpayGatewayResponse(response);
    }

    @Override
    public void deleteStoredPaymentDetails(DeleteStoredPaymentDetailsGatewayRequest request) throws GatewayException {
         deleteTokenClient.postRequestFor(
                gatewayUrlMap.get(request.getGatewayAccountType()),
                WORLDPAY,
                request.getGatewayAccountType(),
                buildDeleteTokenOrder(request),
                getWorldpayAuthHeaderForManagingRecurringAuthTokens(request.getGatewayCredentials()));
    }
    
    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(Charge charge, List<Refund> refundList) {
        return externalRefundAvailabilityCalculator.calculate(charge, refundList);
    }

    @Override
    public WorldpayAuthorisationRequestSummary generateAuthorisationRequestSummary(ChargeEntity chargeEntity, AuthCardDetails authCardDetails, boolean isSetupAgreement) {
        return new WorldpayAuthorisationRequestSummary(chargeEntity, authCardDetails, isSetupAgreement);
    }

    @Override
    public RefundEntityFactory getRefundEntityFactory() {
        return refundEntityFactory;
    }
    
    private GatewayOrder build3dsResponseAuthOrder(Auth3dsResponseGatewayRequest request) {
        return aWorldpay3dsResponseAuthOrderRequestBuilder()
                .withPaResponse3ds(request.getAuth3dsResult().getPaResponse())
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getChargeExternalId()))
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()))
                .build();
    }

    private GatewayOrder buildCancelOrder(CancelGatewayRequest request) {
        return aWorldpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()))
                .build();
    }

    private GatewayOrder buildDeleteTokenOrder(DeleteStoredPaymentDetailsGatewayRequest request) {
        WorldpayCredentials worldpayCredentials = (WorldpayCredentials) request.getGatewayCredentials();
        String merchantCode = worldpayCredentials.getRecurringCustomerInitiatedCredentials()
                .map(WorldpayMerchantCodeCredentials::getMerchantCode)
                .orElseThrow(MissingCredentialsForRecurringPaymentException::new);
        return aWorldpayDeleteTokenOrderRequestBuilder()
                .withAgreementId(request.getAgreementExternalId())
                .withPaymentTokenId(request.getRecurringAuthToken().get(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY))
                .withMerchantCode(merchantCode)
                .build();
    }
    private String sanitiseMessage(String message) {
        return message.replaceAll("<cardHolderName>.*</cardHolderName>", "<cardHolderName>REDACTED</cardHolderName>");
    }
}
