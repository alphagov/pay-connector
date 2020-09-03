package uk.gov.pay.connector.gateway.worldpay;

import io.dropwizard.setup.Environment;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
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
import uk.gov.pay.connector.gateway.worldpay.applepay.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.gateway.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.gateway.GatewayOperation.QUERY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayInquiryRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayPaymentProvider implements PaymentProvider, WorldpayGatewayResponseGenerator {

    public static final String WORLDPAY_MACHINE_COOKIE_NAME = "machine";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GatewayClient authoriseClient;
    private final GatewayClient cancelClient;
    private final GatewayClient inquiryClient;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final WorldpayCaptureHandler worldpayCaptureHandler;
    private final WorldpayRefundHandler worldpayRefundHandler;
    private final WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler;
    private final Map<String, URI> gatewayUrlMap;

    @Inject
    public WorldpayPaymentProvider(ConnectorConfiguration configuration,
                                   GatewayClientFactory gatewayClientFactory,
                                   Environment environment) {

        gatewayUrlMap = configuration.getGatewayConfigFor(WORLDPAY).getUrls().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, v -> URI.create(v.getValue())));
        authoriseClient = gatewayClientFactory.createGatewayClient(WORLDPAY, AUTHORISE, environment.metrics());
        cancelClient = gatewayClientFactory.createGatewayClient(WORLDPAY, CANCEL, environment.metrics());
        inquiryClient = gatewayClientFactory.createGatewayClient(WORLDPAY, QUERY, environment.metrics());
        GatewayClient captureClient = gatewayClientFactory.createGatewayClient(WORLDPAY, CAPTURE, environment.metrics());
        externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        worldpayCaptureHandler = new WorldpayCaptureHandler(captureClient, gatewayUrlMap);
        worldpayRefundHandler = new WorldpayRefundHandler(captureClient, gatewayUrlMap);
        worldpayWalletAuthorisationHandler = new WorldpayWalletAuthorisationHandler(authoriseClient, gatewayUrlMap);
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
                getGatewayAccountCredentialsAsAuthHeader(charge.getGatewayAccount())
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
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request) throws GatewayException {
        GatewayClient.Response response = authoriseClient.postRequestFor(
                gatewayUrlMap.get(request.getGatewayAccount().getType()),
                request.getGatewayAccount(),
                buildAuthoriseOrder(request),
                getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount()));

        if (response.getEntity().contains("request3DSecure")) {
            logger.info(format("Worldpay authorisation response when 3ds required for %s: %s", request.getChargeExternalId(), sanitiseMessage(response.getEntity())));
        }
        return getWorldpayGatewayResponse(response);
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
                    getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount()));
            GatewayResponse<BaseAuthoriseResponse> gatewayResponse = getWorldpayGatewayResponse(response);

            logger.info(format("Worldpay 3ds authorisation response for %s : %s", request.getChargeExternalId(), sanitiseMessage(response.getEntity())));

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
                getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount()));
        return getWorldpayGatewayResponse(response);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(Charge charge, List<RefundEntity> refundEntityList) {
        return externalRefundAvailabilityCalculator.calculate(charge, refundEntityList);
    }

    private GatewayOrder buildAuthoriseOrder(CardAuthorisationGatewayRequest request) {
        logMissingDdcResultFor3dsFlexIntegration(request);

        boolean is3dsRequired = request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().isPresent() ||
                request.getGatewayAccount().isRequires3ds();

        var builder = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getChargeExternalId()))
                .with3dsRequired(is3dsRequired)
                .withDate(DateTime.now(DateTimeZone.UTC));

        if (request.getGatewayAccount().isSendPayerIpAddressToGateway()) {
            request.getAuthCardDetails().getIpAddress().ifPresent(builder::withPayerIpAddress);
        }

        return builder
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .build();
    }

    private void logMissingDdcResultFor3dsFlexIntegration(CardAuthorisationGatewayRequest request) {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        if (gatewayAccount.isRequires3ds() && gatewayAccount.getIntegrationVersion3ds() == 2 &&
                request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().isEmpty()) {
            logger.info("[3DS Flex] Missing device data collection result for {}", gatewayAccount.getId());
        }
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
