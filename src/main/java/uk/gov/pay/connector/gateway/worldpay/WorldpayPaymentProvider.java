package uk.gov.pay.connector.gateway.worldpay;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
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
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.worldpay.wallets.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayInquiryRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayPaymentProvider implements PaymentProvider, WorldpayGatewayResponseGenerator {

    static final String WORLDPAY_MACHINE_COOKIE_NAME = "machine";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldpayPaymentProvider.class);
    private static final List<String> SOFT_DECLINE_EXEMPTION_RESPONSE_RESULTS = List.of("REJECTED", "OUT_OF_SCOPE");
    
    private final GatewayClient authoriseClient;
    private final GatewayClient cancelClient;
    private final GatewayClient inquiryClient;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final WorldpayCaptureHandler worldpayCaptureHandler;
    private final WorldpayRefundHandler worldpayRefundHandler;
    private final WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler;
    private final WorldpayAuthoriseHandler worldpayAuthoriseHandler;
    private final Map<String, URI> gatewayUrlMap;
    private final AuthorisationRequestSummaryStringifier authorisationRequestSummaryStringifier;
    private final AuthorisationService authorisationService;
    private final AuthorisationRequestSummaryStructuredLogging authorisationRequestSummaryStructuredLogging;

    @Inject
    public WorldpayPaymentProvider(@Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap,
                                   @Named("WorldpayAuthoriseGatewayClient") GatewayClient authoriseClient,
                                   @Named("WorldpayCancelGatewayClient") GatewayClient cancelClient,
                                   @Named("WorldpayInquiryGatewayClient") GatewayClient inquiryClient,
                                   WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler,
                                   WorldpayAuthoriseHandler worldpayAuthoriseHandler,
                                   WorldpayCaptureHandler worldpayCaptureHandler,
                                   WorldpayRefundHandler worldpayRefundHandler,
                                   AuthorisationRequestSummaryStringifier authorisationRequestSummaryStringifier,
                                   AuthorisationService authorisationService, 
                                   AuthorisationRequestSummaryStructuredLogging authorisationRequestSummaryStructuredLogging) {

        this.gatewayUrlMap = gatewayUrlMap;
        this.cancelClient = cancelClient;
        this.inquiryClient = inquiryClient;
        this.authoriseClient = authoriseClient;
        this.worldpayCaptureHandler = worldpayCaptureHandler;
        this.worldpayRefundHandler = worldpayRefundHandler;
        this.worldpayWalletAuthorisationHandler = worldpayWalletAuthorisationHandler;
        this.worldpayAuthoriseHandler = worldpayAuthoriseHandler;
        this.authorisationRequestSummaryStringifier = authorisationRequestSummaryStringifier;
        this.authorisationService = authorisationService;
        this.authorisationRequestSummaryStructuredLogging = authorisationRequestSummaryStructuredLogging;
        externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return WORLDPAY;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.of(randomUUID().toString());
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeEntity charge) throws GatewayException {
        GatewayClient.Response response = inquiryClient.postRequestFor(
                gatewayUrlMap.get(charge.getGatewayAccount().getType()),
                charge.getGatewayAccount(),
                buildQuery(charge),
                getGatewayAccountCredentialsAsAuthHeader(charge.getGatewayAccount().getCredentials())
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
                                charge.getExternalId(),
                                worldpayGatewayResponse
                        )));
    }

    @Override
    public boolean canQueryPaymentStatus() {
        return true;
    }

    private GatewayOrder buildQuery(ChargeEntity charge) {
        return aWorldpayInquiryRequestBuilder()
                .withTransactionId(charge.getGatewayTransactionId())
                .withMerchantCode(charge.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    @Override
    public GatewayResponse<WorldpayOrderStatusResponse> authorise(CardAuthorisationGatewayRequest request) {
        
        GatewayResponse<WorldpayOrderStatusResponse> response = worldpayAuthoriseHandler.authorise(request);
        
        if (response.getBaseResponse().isPresent()) {
        
            var worldpayOrderResponse = response.getBaseResponse().get();
        
            if (worldpayOrderResponse.getLastEvent().isPresent() && 
                    worldpayOrderResponse.getLastEvent().get().equals("REFUSED") && 
                    worldpayOrderResponse.getExemptionResponseResult().isPresent() &&
                    SOFT_DECLINE_EXEMPTION_RESPONSE_RESULTS.contains(worldpayOrderResponse.getExemptionResponseResult().get())) {

                var authorisationRequestSummary = generateAuthorisationRequestSummary(request.getCharge(), request.getAuthCardDetails());

                Optional<String> transactionId = authorisationService.extractTransactionId(request.getCharge().getExternalId(), response);
                
                // Used by Splunk saved search
                var logMessage = String.format(Locale.UK, "Authorisation%s for %s (%s %s) for %s (%s) - %s .'. %s -> %s",
                        authorisationRequestSummaryStringifier.stringify(authorisationRequestSummary),
                        request.getCharge().getExternalId(), 
                        request.getCharge().getPaymentGatewayName().getName(),
                        transactionId.orElse("missing transaction ID"),
                        request.getCharge().getGatewayAccount().getAnalyticsId(),
                        request.getCharge().getGatewayAccount().getId(),
                        response, 
                        ChargeStatus.fromString(request.getCharge().getStatus()), request.getCharge().getStatus());

                var structuredLoggingArguments = ArrayUtils.addAll(
                        request.getCharge().getStructuredLoggingArgs(),
                        authorisationRequestSummaryStructuredLogging.createArgs(authorisationRequestSummary));

                LOGGER.info(logMessage, structuredLoggingArguments);
                
                return worldpayAuthoriseHandler.authorise(request, true);
            }
        }
        return response;
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        try {
            List<HttpCookie> cookies = request.getProviderSessionId()
                    .map(providerSessionId -> singletonList(new HttpCookie(WORLDPAY_MACHINE_COOKIE_NAME, providerSessionId.toString())))
                    .orElse(emptyList());

            GatewayClient.Response response = authoriseClient.postRequestFor(
                    gatewayUrlMap.get(request.getGatewayAccount().getType()),
                    request.getGatewayAccount(),
                    build3dsResponseAuthOrder(request),
                    cookies,
                    getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount().getCredentials()));
            GatewayResponse<BaseAuthoriseResponse> gatewayResponse = getWorldpayGatewayResponse(response);

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
        GatewayClient.Response response = cancelClient.postRequestFor(gatewayUrlMap.get(request.getGatewayAccount().getType()),
                request.getGatewayAccount(), buildCancelOrder(request),
                getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount().getCredentials()));
        return getWorldpayGatewayResponse(response);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(Charge charge, List<Refund> refundList) {
        return externalRefundAvailabilityCalculator.calculate(charge, refundList);
    }

    @Override
    public WorldpayAuthorisationRequestSummary generateAuthorisationRequestSummary(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        return new WorldpayAuthorisationRequestSummary(chargeEntity, authCardDetails);
    }

    private GatewayOrder build3dsResponseAuthOrder(Auth3dsResponseGatewayRequest request) {
        return aWorldpay3dsResponseAuthOrderRequestBuilder()
                .withPaResponse3ds(request.getAuth3dsResult().getPaResponse())
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getChargeExternalId()))
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private GatewayOrder buildCancelOrder(CancelGatewayRequest request) {
        return aWorldpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private String sanitiseMessage(String message) {
        return message.replaceAll("<cardHolderName>.*</cardHolderName>", "<cardHolderName>REDACTED</cardHolderName>");
    }
}
