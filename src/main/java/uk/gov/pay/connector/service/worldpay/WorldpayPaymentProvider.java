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
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.util.XMLUnmarshallerException;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static fj.data.Either.reduce;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.AuthorisationGatewayResponse.*;
import static uk.gov.pay.connector.model.CancelGatewayResponse.cancelFailureResponse;
import static uk.gov.pay.connector.model.CancelGatewayResponse.successfulCancelResponse;
import static uk.gov.pay.connector.model.CaptureGatewayResponse.captureFailureResponse;
import static uk.gov.pay.connector.model.CaptureGatewayResponse.successfulCaptureResponse;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.InquiryGatewayResponse.*;
import static uk.gov.pay.connector.model.RefundGatewayResponse.failureResponse;
import static uk.gov.pay.connector.model.RefundGatewayResponse.successfulResponse;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aWorldpayOrderCaptureRequest;
import static uk.gov.pay.connector.service.OrderRefundRequestBuilder.aWorldpayOrderRefundRequest;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aWorldpayOrderSubmitRequest;
import static uk.gov.pay.connector.service.worldpay.OrderInquiryRequestBuilder.anOrderInquiryRequest;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderCancelRequestBuilder.aWorldpayOrderCancelRequest;
import static uk.gov.pay.connector.util.XMLUnmarshaller.unmarshall;

public class WorldpayPaymentProvider implements PaymentProvider {
    public static final String NOTIFICATION_ACKNOWLEDGED = "[OK]";
    public static final StatusUpdates NO_UPDATE = StatusUpdates.noUpdate(NOTIFICATION_ACKNOWLEDGED);
    public static final StatusUpdates FAILED = StatusUpdates.failed();
    private final Logger logger = LoggerFactory.getLogger(WorldpayPaymentProvider.class);

    private final GatewayClient client;

    public WorldpayPaymentProvider(GatewayClient client) {
        this.client = client;
    }

    @Override
    public AuthorisationGatewayResponse authorise(AuthorisationGatewayRequest request) {
        String gatewayTransactionId = generateTransactionId();
        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), buildOrderSubmitFor(request, gatewayTransactionId))
                        .bimap(
                                AuthorisationGatewayResponse::authorisationFailureResponse,
                                (response) -> mapToCardAuthorisationResponse(response, gatewayTransactionId)
                        )
        );
    }

    @Override
    public CaptureGatewayResponse capture(CaptureGatewayRequest request) {
        String requestString = buildOrderCaptureFor(request);
        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), requestString)
                        .bimap(
                                CaptureGatewayResponse::captureFailureResponse,
                                this::mapToCaptureResponse
                        )
        );
    }

    public RefundGatewayResponse refund(RefundGatewayRequest request) {
        String requestString = buildOrderRefundFor(request);
        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), requestString)
                        .bimap(
                                RefundGatewayResponse::failureResponse,
                                this::mapToRefundResponse
                        )
        );
    }

    @Override
    public CancelGatewayResponse cancel(CancelGatewayRequest request) {
        String requestString = buildCancelOrderFor(request);
        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), requestString)
                        .bimap(
                                CancelGatewayResponse::cancelFailureResponse,
                                this::mapToCancelResponse
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
                                notification.getChargeStatus()));
                    }
                });
    }

    private StatusUpdates confirmStatus(GatewayAccountEntity gatewayAccount, String transactionId) {
        InquiryGatewayResponse inquiryGatewayResponse = inquire(transactionId, gatewayAccount);
        String worldpayStatus = inquiryGatewayResponse.getNewStatus();
        if (!inquiryGatewayResponse.isSuccessful() || StringUtils.isBlank(worldpayStatus)) {
            logger.error("Could not look up status from worldpay for worldpay charge id " + transactionId);
            return StatusUpdates.failed();
        }

        return WorldpayStatusesMapper
                .mapToChargeStatus(worldpayStatus)
                .map(chargeStatus -> {
                    Pair<String, ChargeStatus> update = Pair.of(inquiryGatewayResponse.getTransactionId(), chargeStatus);
                    return StatusUpdates.withUpdate(NOTIFICATION_ACKNOWLEDGED, ImmutableList.of(update));
                })
                .orElseGet(() -> {
                    logger.error(format("Could not map worldpay status %s to our internal status.", worldpayStatus));
                    return FAILED;
                });
    }


    private InquiryGatewayResponse inquire(String transactionId, GatewayAccountEntity gatewayAccount) {
        return reduce(
                client
                        .postXMLRequestFor(gatewayAccount, buildOrderInquiryFor(gatewayAccount, transactionId))
                        .bimap(
                                InquiryGatewayResponse::inquiryFailureResponse,
                                (response) -> response.getStatus() == OK.getStatusCode() ?
                                        mapToInquiryResponse(response) :
                                        errorInquiryResponse(response)
                        )
        );
    }

    private String buildOrderCaptureFor(CaptureGatewayRequest request) {
        return aWorldpayOrderCaptureRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .withDate(DateTime.now(DateTimeZone.UTC))
                .build();
    }

    private String buildOrderRefundFor(RefundGatewayRequest request) {
        return aWorldpayOrderRefundRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .build();
    }

    private String buildOrderSubmitFor(AuthorisationGatewayRequest request, String gatewayTransactionId) {
        return aWorldpayOrderSubmitRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(gatewayTransactionId)
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();
    }

    private String buildCancelOrderFor(CancelGatewayRequest request) {
        return aWorldpayOrderCancelRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private String buildOrderInquiryFor(GatewayAccountEntity gatewayAccount, String transactionId) {
        return anOrderInquiryRequest()
                .withMerchantCode(gatewayAccount.getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(transactionId)
                .build();
    }

    private AuthorisationGatewayResponse mapToCardAuthorisationResponse(GatewayClient.Response response, String gatewayTransactionId) {
        return reduce(
                client.unmarshallResponse(response, WorldpayOrderStatusResponse.class)
                        .bimap(
                                AuthorisationGatewayResponse::authorisationFailureResponse,
                                (wResponse) -> {
                                    if (wResponse.isError()) {
                                        return authorisationFailureNotUpdateResponse(gatewayTransactionId, wResponse.getErrorMessage());
                                    }
                                    return wResponse.isAuthorised() ?
                                            successfulAuthorisationResponse(AUTHORISATION_SUCCESS, gatewayTransactionId) :
                                            authorisationFailureResponse(gatewayTransactionId, "Unauthorised");
                                }
                        )
        );
    }

    private CaptureGatewayResponse mapToCaptureResponse(GatewayClient.Response response) {
        return reduce(
                client.unmarshallResponse(response, WorldpayCaptureResponse.class)
                        .bimap(
                                CaptureGatewayResponse::captureFailureResponse,
                                (wResponse) -> wResponse.isCaptured() ?
                                        successfulCaptureResponse(CAPTURE_SUBMITTED) :
                                        captureFailureResponse(wResponse.getErrorMessage())
                        )
        );
    }

    private RefundGatewayResponse mapToRefundResponse(GatewayClient.Response response) {
        return reduce(
                client.unmarshallResponse(response, WorldpayRefundResponse.class)
                        .bimap(
                                RefundGatewayResponse::failureResponse,
                                (wResponse) -> wResponse.isRefunded() ?
                                        successfulResponse(REFUND_SUBMITTED) :
                                        failureResponse(wResponse.getErrorMessage())
                        )
        );
    }

    private InquiryGatewayResponse mapToInquiryResponse(GatewayClient.Response response) {
        return reduce(
                client.unmarshallResponse(response, WorldpayOrderStatusResponse.class)
                        .bimap(
                                InquiryGatewayResponse::inquiryFailureResponse,
                                (wResponse) -> wResponse.isError() ?
                                        inquiryFailureResponse(baseError(wResponse.getErrorMessage())) :
                                        inquiryStatusUpdate(wResponse.getTransactionId(), wResponse.getLastEvent())

                        )
        );
    }

    private CancelGatewayResponse mapToCancelResponse(GatewayClient.Response response) {
        return reduce(
                client.unmarshallResponse(response, WorldpayCancelResponse.class)
                        .bimap(
                                CancelGatewayResponse::cancelFailureResponse,
                                (wResponse) -> {
                                    if (wResponse.isCancelled()) {
                                        return successfulCancelResponse(SYSTEM_CANCELLED);
                                    } else {
                                        logger.error(format("Failed to cancel charge: %s", wResponse.getErrorMessage()));
                                        return cancelFailureResponse(baseError(wResponse.getErrorMessage()));
                                    }
                                }
                        )
        );
    }

    private String generateTransactionId() {
        return randomUUID().toString();
    }
}
