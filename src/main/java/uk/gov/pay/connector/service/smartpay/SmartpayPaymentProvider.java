package uk.gov.pay.connector.service.smartpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseResponse;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aSmartpayOrderCaptureRequest;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aSmartpayOrderSubmitRequest;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderCancelRequestBuilder.aSmartpayOrderCancelRequest;

public class SmartpayPaymentProvider extends BasePaymentProvider implements PaymentProvider<BaseResponse> {
    private static final String MERCHANT_CODE = "MerchantAccount";
    public static final String ACCEPTED = "[accepted]";
    private final Logger logger = LoggerFactory.getLogger(SmartpayPaymentProvider.class);
    private final ObjectMapper objectMapper;

    public SmartpayPaymentProvider(GatewayClient client, ObjectMapper objectMapper) {
        super(client);
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        return sendReceive(request, buildSubmitOrderFor(), SmartpayAuthorisationResponse.class);
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {
        return sendReceive(request, buildCaptureOrderFor(), SmartpayCaptureResponse.class);
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) {
        return sendReceive(request, buildCancelOrderFor(), SmartpayCancelResponse.class);

    }

    @Override
    public GatewayResponse inquire(String transactionId, GatewayAccountEntity gatewayAccount) {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public StatusUpdates handleNotification(String inboundNotification,
                                            Function<ChargeStatusRequest, Boolean> payloadChecks,
                                            Function<String, Optional<GatewayAccountEntity>> accountFinder,
                                            Consumer<StatusUpdates> accountUpdater) {
        try {
            List<SmartpayNotification> notifications = objectMapper.readValue(inboundNotification, SmartpayNotificationList.class).getNotifications();

            List<Pair<String, ChargeStatus>> updates = notifications.stream()
                    .sorted()
                    .map(this::extendInternalStatus)
                    .peek(this::logIfChargeStatusNotFound)
                    .filter(notification -> notification.getChargeStatus().isPresent())
                    .filter(payloadChecks::apply)
                    .map(this::toPair)
                    .collect(Collectors.toList());

            if (updates.size() > 0) {
                StatusUpdates statusUpdates = StatusUpdates.withUpdate(ACCEPTED, updates);
                accountUpdater.accept(statusUpdates);
                return statusUpdates;
            }
        } catch (IllegalArgumentException | IOException e) {
            // If we've failed to parse the message, we don't want it to be resent - there's no reason to believe our
            // deterministic computer code could successfully parse the same message if it arrived a second time.
            // Barclays also mandate that acknowledging notifications should be unconditional.
            // See http://www.barclaycard.co.uk/business/files/SmartPay_Notifications_Guide.pdf for further details.
            logger.error(format("Could not deserialise smartpay notification:\n %s", inboundNotification), e);
        }
        return StatusUpdates.noUpdate(ACCEPTED);
    }

    private void logIfChargeStatusNotFound(SmartpayNotification notification) {
        if (!notification.getChargeStatus().isPresent()) {
            logger.error(format("No matching ChargeStatus found for status on notification: %s", notification.getEventCode()));
        }
    }

    private SmartpayNotification extendInternalStatus(SmartpayNotification notification) {
        notification.setChargeStatus(SmartpayStatusMapper.mapToChargeStatus(
                notification.getEventCode(), notification.isSuccessFull()));
        return notification;
    }

    private Pair<String, ChargeStatus> toPair(SmartpayNotification notification) {
        return Pair.of(notification.getTransactionId(), notification.getChargeStatus().get());
    }

    private Function<AuthorisationGatewayRequest, String> buildSubmitOrderFor() {
        return request -> aSmartpayOrderSubmitRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withPaymentPlatformReference(request.getChargeId())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();
    }

    private Function<CaptureGatewayRequest, String> buildCaptureOrderFor() {
        return request -> aSmartpayOrderCaptureRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .build();
    }

    private Function<CancelGatewayRequest, String> buildCancelOrderFor() {
        return request -> aSmartpayOrderCancelRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withTransactionId(request.getTransactionId())
                .build();
    }
}
