package uk.gov.pay.connector.service.worldpay;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseInquiryResponse;
import uk.gov.pay.connector.service.BaseResponse;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.smartpay.BasePaymentProvider;
import uk.gov.pay.connector.util.XMLUnmarshallerException;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static fj.data.Either.reduce;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aWorldpayOrderCaptureRequest;
import static uk.gov.pay.connector.service.OrderRefundRequestBuilder.aWorldpayOrderRefundRequest;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aWorldpayOrderSubmitRequest;
import static uk.gov.pay.connector.service.worldpay.OrderInquiryRequestBuilder.anOrderInquiryRequest;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderCancelRequestBuilder.aWorldpayOrderCancelRequest;
import static uk.gov.pay.connector.util.XMLUnmarshaller.unmarshall;

public class WorldpayPaymentProvider<T extends BaseResponse> extends BasePaymentProvider implements PaymentProvider<T> {
    public static final String NOTIFICATION_ACKNOWLEDGED = "[OK]";
    public static final StatusUpdates NO_UPDATE = StatusUpdates.noUpdate(NOTIFICATION_ACKNOWLEDGED);
    public static final StatusUpdates FAILED = StatusUpdates.failed();
    private final Logger logger = LoggerFactory.getLogger(WorldpayPaymentProvider.class);
    private final GatewayClient client;

    public WorldpayPaymentProvider(GatewayClient client) {
        super(client);
        this.client = client;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.of(randomUUID().toString());
    }


    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        return sendReceive(request, buildAuthoriseOrderFor(), WorldpayOrderStatusResponse.class);
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {
        return sendReceive(request, buildCaptureOrderFor(), WorldpayCaptureResponse.class);
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        return sendReceive(request, buildRefundOrderFor(), WorldpayRefundResponse.class);
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) {
        return sendReceive(request, buildCancelOrderFor(), WorldpayCancelResponse.class);

    }

    @Override
    public GatewayResponse inquire(String transactionId, GatewayAccountEntity gatewayAccount) {
        return reduce(
                client
                        .postXMLRequestFor(gatewayAccount, buildInquiryOrderFor(gatewayAccount, transactionId))
                        .bimap(
                                GatewayResponse::with,
                                this::mapToInquiryResponse
                        )
        );
    }

    private GatewayResponse mapToInquiryResponse(GatewayClient.Response response) {
        return reduce(
                client.unmarshallResponse(response, WorldpayOrderStatusResponse.class)
                        .bimap(
                                GatewayResponse::with,
                                GatewayResponse::with

                        )
        );
    }

    @Override
    public StatusUpdates handleNotification(String notificationPayload,
                                            Function<ChargeStatusRequest, Boolean> payloadChecks,
                                            Function<String, Optional<GatewayAccountEntity>> accountFinder,
                                            Consumer<StatusUpdates> accountUpdater) {

        Optional<WorldpayNotification> notificationMaybe = parseNotification(notificationPayload);

        return notificationMaybe
                .map(notification -> {
                    if (!payloadChecks.apply(notification)) {
                        return NO_UPDATE;
                    }

                    return accountFinder.apply(notification.getTransactionId())
                            .map(gatewayAccount -> {
                                StatusUpdates statusUpdates = confirmStatus(gatewayAccount, notification.getTransactionId());
                                processInquiryStatus(accountUpdater, notification, statusUpdates);
                                return statusUpdates;
                            })
                            .orElseGet(() -> FAILED);
                })
                .orElseGet(() -> FAILED);
    }

    private Optional<WorldpayNotification> parseNotification(String inboundNotification) {
        try {
            WorldpayNotification chargeNotification = unmarshall(inboundNotification, WorldpayNotification.class);

            return Optional.ofNullable(chargeNotification)
                    .map(notification -> {
                        notification.setChargeStatus(WorldpayStatusesMapper.mapToChargeStatus(notification.getStatus()));
                        return notification;
                    });
        } catch (XMLUnmarshallerException e) {
            logger.error(format("Could not deserialise worldpay response %s", inboundNotification), e);
            return Optional.empty();
        }
    }

    private void processInquiryStatus(Consumer<StatusUpdates> accountUpdater, WorldpayNotification notification, StatusUpdates statusUpdates) {
        if (statusUpdates.successful()) {
            logMismatchingStatuses(notification, statusUpdates);
            accountUpdater.accept(statusUpdates);
        }
    }

    private void logMismatchingStatuses(WorldpayNotification notification, StatusUpdates statusUpdates) {
        statusUpdates.getStatusUpdates()
                .stream()
                .findFirst()
                .ifPresent(status -> {
                    if (!notification.getChargeStatus().isPresent() ||
                            status.getValue() != notification.getChargeStatus().get()) {
                        logger.error(format("Inquiry status '%s' did not match notification status '%s'",
                                status.getValue(),
                                notification.getStatus()));
                    }
                });
    }

    //todo :(
    private StatusUpdates confirmStatus(GatewayAccountEntity gatewayAccount, String transactionId) {
        GatewayResponse<BaseInquiryResponse> inquiryGatewayResponse = inquire(transactionId, gatewayAccount);

        String worldpayStatus = null;
        Optional<BaseInquiryResponse> baseResponse = inquiryGatewayResponse.getBaseResponse();
        if (baseResponse.isPresent()) {
            worldpayStatus = baseResponse.get().getLastEvent();
        }

        if (StringUtils.isBlank(worldpayStatus)) {
            logger.error("Could not look up status from worldpay for worldpay charge id " + transactionId);
            return StatusUpdates.failed();
        }
        final String status = worldpayStatus;

        return WorldpayStatusesMapper
                .mapToChargeStatus(worldpayStatus)
                .map(chargeStatus -> {
                    Pair<String, ChargeStatus> update = Pair.of(baseResponse.get().getTransactionId(), chargeStatus);
                    return StatusUpdates.withUpdate(NOTIFICATION_ACKNOWLEDGED, ImmutableList.of(update));
                })
                .orElseGet(() -> {
                    logger.error(format("Could not map worldpay status %s to our internal status.", status));
                    return FAILED;
                });
    }

    private Function<AuthorisationGatewayRequest, String> buildAuthoriseOrderFor() {
        return request -> aWorldpayOrderSubmitRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId().orElse(""))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();
    }

    private Function<CaptureGatewayRequest, String> buildCaptureOrderFor() {
        return request -> aWorldpayOrderCaptureRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .withDate(DateTime.now(DateTimeZone.UTC))
                .build();
    }

    private Function<RefundGatewayRequest, String> buildRefundOrderFor() {
        return request -> aWorldpayOrderRefundRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .build();
    }

    private Function<CancelGatewayRequest, String> buildCancelOrderFor() {
        return request -> aWorldpayOrderCancelRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private String buildInquiryOrderFor(GatewayAccountEntity gatewayAccount, String transactionId) {
        return anOrderInquiryRequest()
                .withMerchantCode(gatewayAccount.getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(transactionId)
                .build();
    }
}
