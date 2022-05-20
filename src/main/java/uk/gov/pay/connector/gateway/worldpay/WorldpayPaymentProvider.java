package uk.gov.pay.connector.gateway.worldpay;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.charge.Gateway3dsExemptionResultObtained;
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
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.worldpay.wallets.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayInquiryRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_HONOURED;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_NOT_REQUESTED;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_OUT_OF_SCOPE;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_REJECTED;

public class WorldpayPaymentProvider implements PaymentProvider, WorldpayGatewayResponseGenerator {

    static final String WORLDPAY_MACHINE_COOKIE_NAME = "machine";

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldpayPaymentProvider.class);

    private final GatewayClient authoriseClient;
    private final GatewayClient cancelClient;
    private final GatewayClient inquiryClient;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final WorldpayCaptureHandler worldpayCaptureHandler;
    private final WorldpayRefundHandler worldpayRefundHandler;
    private final WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler;
    private final WorldpayAuthoriseHandler worldpayAuthoriseHandler;
    private final Map<String, URI> gatewayUrlMap;
    private final AuthorisationService authorisationService;
    private final AuthorisationLogger authorisationLogger;
    private final ChargeDao chargeDao;
    private final EventService eventService;

    @Inject
    public WorldpayPaymentProvider(@Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap,
                                   @Named("WorldpayAuthoriseGatewayClient") GatewayClient authoriseClient,
                                   @Named("WorldpayCancelGatewayClient") GatewayClient cancelClient,
                                   @Named("WorldpayInquiryGatewayClient") GatewayClient inquiryClient,
                                   WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler,
                                   WorldpayAuthoriseHandler worldpayAuthoriseHandler,
                                   WorldpayCaptureHandler worldpayCaptureHandler,
                                   WorldpayRefundHandler worldpayRefundHandler,
                                   AuthorisationService authorisationService,
                                   AuthorisationLogger authorisationLogger,
                                   ChargeDao chargeDao,
                                   EventService eventService) {

        this.gatewayUrlMap = gatewayUrlMap;
        this.cancelClient = cancelClient;
        this.inquiryClient = inquiryClient;
        this.authoriseClient = authoriseClient;
        this.worldpayCaptureHandler = worldpayCaptureHandler;
        this.worldpayRefundHandler = worldpayRefundHandler;
        this.worldpayWalletAuthorisationHandler = worldpayWalletAuthorisationHandler;
        this.worldpayAuthoriseHandler = worldpayAuthoriseHandler;
        this.authorisationService = authorisationService;
        this.authorisationLogger = authorisationLogger;
        this.chargeDao = chargeDao;
        this.eventService = eventService;
        externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
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
    public ChargeQueryResponse queryPaymentStatus(ChargeQueryGatewayRequest chargeQueryGatewayRequest) throws GatewayException {
        GatewayClient.Response response = inquiryClient.postRequestFor(
                gatewayUrlMap.get(chargeQueryGatewayRequest.getGatewayAccount().getType()),
                WORLDPAY,
                chargeQueryGatewayRequest.getGatewayAccount().getType(),
                buildQuery(chargeQueryGatewayRequest),
                getGatewayAccountCredentialsAsAuthHeader(chargeQueryGatewayRequest.getGatewayCredentials())
        );
        GatewayResponse<WorldpayQueryResponse> worldpayGatewayResponse = getWorldpayGatewayResponse(response, WorldpayQueryResponse.class);

        return worldpayGatewayResponse.getBaseResponse()
                .map(worldpayQueryResponse -> {
                    ChargeStatus mappedStatus = WorldpayStatus.fromString(worldpayQueryResponse.getLastEvent())
                            .map(WorldpayStatus::getPayStatus)
                            .orElse(null);

                    return new ChargeQueryResponse(mappedStatus, worldpayQueryResponse);
                })
                .orElseThrow(() ->
                        new WebApplicationException(format(
                                "Unable to query charge %s - an error occurred: %s",
                                chargeQueryGatewayRequest.getChargeExternalId(),
                                worldpayGatewayResponse
                        )));
    }

    @Override
    public boolean canQueryPaymentStatus() {
        return true;
    }

    private GatewayOrder buildQuery(ChargeQueryGatewayRequest chargeQueryGatewayRequest) {
        return aWorldpayInquiryRequestBuilder()
                .withTransactionId(chargeQueryGatewayRequest.getTransactionId())
                .withMerchantCode(chargeQueryGatewayRequest.getGatewayCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    @Override
    public GatewayResponse<WorldpayOrderStatusResponse> authorise(CardAuthorisationGatewayRequest request, ChargeEntity charge) {

        boolean exemptionEngineEnabled = isExemptionEngineEnabled(request);
        GatewayResponse<WorldpayOrderStatusResponse> response;
        
        if (!exemptionEngineEnabled) {
            response = worldpayAuthoriseHandler.authoriseWithoutExemption(request);
        } else {
            response = worldpayAuthoriseHandler.authoriseWithExemption(request);
        }
        
        calculateAndStoreExemption(exemptionEngineEnabled, charge, response);
        
        if (response.getBaseResponse().map(WorldpayOrderStatusResponse::isSoftDecline).orElse(false)) {
            
            var authorisationRequestSummary = generateAuthorisationRequestSummary(request.getGatewayAccount(), request.getAuthCardDetails());

            authorisationLogger.logChargeAuthorisation(
                    LOGGER,
                    authorisationRequestSummary,
                    charge,
                    authorisationService.extractTransactionId(charge.getExternalId(), response)
                            .orElse("missing transaction ID"),
                    response,
                    charge.getChargeStatus(),
                    charge.getChargeStatus());

            CardAuthorisationGatewayRequest newRequest = request.withNewTransactionId(newTransactionId());
            response = worldpayAuthoriseHandler.authoriseWithoutExemption(newRequest);
        } 

        return response;
    }

    /**
     * IMPORTANT: this method should not attempt to update the Charge in the database. This is because it is executed
     * on a worker thread and the initiating thread can attempt to update the Charge status while it is still being
     * executed.
     */
    @Override
    public GatewayResponse authoriseMotoApi(CardAuthorisationGatewayRequest request) {
        return worldpayAuthoriseHandler.authoriseWithoutExemption(request);
    }

    private void calculateAndStoreExemption(boolean exemptionEngineEnabled, ChargeEntity charge, GatewayResponse<WorldpayOrderStatusResponse> response) {
        if (!exemptionEngineEnabled) {
            updateChargeWithExemption3ds(EXEMPTION_NOT_REQUESTED, charge);
        } else {
            calculateAndStoreExemption(charge, response);
        }
    }

    private void calculateAndStoreExemption(ChargeEntity charge, GatewayResponse<WorldpayOrderStatusResponse> response) {
        response.getBaseResponse().flatMap(WorldpayOrderStatusResponse::getExemptionResponseResult).ifPresent(exemption3ds -> {
            switch (exemption3ds) {
                case "HONOURED":
                    updateChargeWithExemption3ds(EXEMPTION_HONOURED, charge);
                    break;
                case "REJECTED":
                    updateChargeWithExemption3ds(EXEMPTION_REJECTED, charge);
                    break;
                case "OUT_OF_SCOPE":
                    updateChargeWithExemption3ds(EXEMPTION_OUT_OF_SCOPE, charge);
                    break;
                default:
                    LOGGER.warn("Received unrecognised exemption 3ds response result {} from Worldpay - " +
                            "charge_external_id={}", exemption3ds, charge.getExternalId());
            }
        });
    }

    @Transactional
    public void updateChargeWithExemption3ds(Exemption3ds exemption3ds, ChargeEntity charge) {
        charge.setExemption3ds(exemption3ds);
        LOGGER.info("Updated exemption_3ds of charge to {} - charge_external_id={}", exemption3ds, charge.getExternalId());
        chargeDao.merge(charge);
        eventService.emitAndRecordEvent(Gateway3dsExemptionResultObtained.from(charge, now(UTC)));
    }
    
    private boolean isExemptionEngineEnabled(CardAuthorisationGatewayRequest request) {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        return gatewayAccount.isRequires3ds() && gatewayAccount.getWorldpay3dsFlexCredentials()
                .map(Worldpay3dsFlexCredentials::isExemptionEngineEnabled)
                .orElse(false);
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
                    getGatewayAccountCredentialsAsAuthHeader(request.getGatewayCredentials()));
            GatewayResponse<WorldpayOrderStatusResponse> gatewayResponse = getWorldpayGatewayResponse(response);
            
            calculateAndStoreExemption(request.getCharge(), gatewayResponse);

            LOGGER.info(format("Worldpay 3ds authorisation response for %s : %s", request.getChargeExternalId(), sanitiseMessage(response.getEntity())));

            if (gatewayResponse.getBaseResponse().isEmpty()) {
                gatewayResponse.throwGatewayError();
            }

            BaseAuthoriseResponse authoriseResponse = gatewayResponse.getBaseResponse().get();

            return Gateway3DSAuthorisationResponse.of(gatewayResponse.toString(), authoriseResponse.authoriseStatus(), authoriseResponse.getTransactionId(),
                    authoriseResponse.getGatewayParamsFor3ds().orElse(null), gatewayResponse.getSessionIdentifier().orElse(null));
        } catch (GatewayException e) {
            return Gateway3DSAuthorisationResponse.of(e.getMessage(), BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION);
        }
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return worldpayCaptureHandler.capture(request);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletAuthorisationGatewayRequest request) throws GatewayException {
        return worldpayWalletAuthorisationHandler.authorise(request);
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
                getGatewayAccountCredentialsAsAuthHeader(request.getGatewayCredentials()));
        return getWorldpayGatewayResponse(response);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(Charge charge, List<Refund> refundList) {
        return externalRefundAvailabilityCalculator.calculate(charge, refundList);
    }

    @Override
    public WorldpayAuthorisationRequestSummary generateAuthorisationRequestSummary(GatewayAccountEntity gatewayAccount, AuthCardDetails authCardDetails) {
        return new WorldpayAuthorisationRequestSummary(gatewayAccount, authCardDetails);
    }

    private GatewayOrder build3dsResponseAuthOrder(Auth3dsResponseGatewayRequest request) {
        return aWorldpay3dsResponseAuthOrderRequestBuilder()
                .withPaResponse3ds(request.getAuth3dsResult().getPaResponse())
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getChargeExternalId()))
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private GatewayOrder buildCancelOrder(CancelGatewayRequest request) {
        return aWorldpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(request.getGatewayCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private String sanitiseMessage(String message) {
        return message.replaceAll("<cardHolderName>.*</cardHolderName>", "<cardHolderName>REDACTED</cardHolderName>");
    }
}
