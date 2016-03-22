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

import javax.ws.rs.core.Response;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static fj.data.Either.reduce;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.AuthorisationResponse.*;
import static uk.gov.pay.connector.model.CancelResponse.aSuccessfulCancelResponse;
import static uk.gov.pay.connector.model.CancelResponse.cancelFailureResponse;
import static uk.gov.pay.connector.model.CaptureResponse.captureFailureResponse;
import static uk.gov.pay.connector.model.CaptureResponse.successfulCaptureResponse;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.InquiryResponse.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aWorldpayOrderCaptureRequest;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aWorldpayOrderSubmitRequest;
import static uk.gov.pay.connector.service.worldpay.OrderInquiryRequestBuilder.anOrderInquiryRequest;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderCancelRequestBuilder.aWorldpayOrderCancelRequest;
import static uk.gov.pay.connector.util.XMLUnmarshaller.unmarshall;

public class WorldpayPaymentProvider implements PaymentProvider {
    public static final String NOTIFICATION_ACKNOWLEDGED = "[OK]";
    public static final StatusUpdates NO_UPDATE = StatusUpdates.noUpdate(NOTIFICATION_ACKNOWLEDGED);
    private final Logger logger = LoggerFactory.getLogger(WorldpayPaymentProvider.class);

    private final GatewayClient client;

    public WorldpayPaymentProvider(GatewayClient client) {
        this.client = client;
    }

    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {
        String gatewayTransactionId = generateTransactionId();
        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), buildOrderSubmitFor(request, gatewayTransactionId))
                        .bimap(
                                AuthorisationResponse::authorisationFailureResponse,
                                (response) -> mapToCardAuthorisationResponse(response, gatewayTransactionId)
                        )
        );
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {
        String requestString = buildOrderCaptureFor(request);
        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), requestString)
                        .bimap(
                                CaptureResponse::captureFailureResponse,
                                this::mapToCaptureResponse
                        )
        );
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        String requestString = buildCancelOrderFor(request);
        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), requestString)
                        .bimap(
                                CancelResponse::cancelFailureResponse,
                                this::mapToCancelResponse
                        )
        );
    }

    public Optional<WorldpayNotification> parseNotification(String inboundNotification) {
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

    @Override
    public StatusUpdates handleNotification(String notificationPayload,
                                            Function<ChargeStatusRequest, Boolean> payloadChecks,
                                            Function<String, Optional<GatewayAccountEntity>> accountFinder,
                                            Consumer<StatusUpdates> accountUpdater) {

        Optional<WorldpayNotification> notificationMaybe = parseNotification(notificationPayload);
        return notificationMaybe.map(notification -> {

                    if (!notification.getChargeStatus().isPresent()) {
                        logger.error(format("Could not map worldpay status %s to our internal status.", notification.getStatus()));
                    }

                    if (!payloadChecks.apply(notification)) {
                        return NO_UPDATE;
                    }

                    Optional<GatewayAccountEntity> gatewayAccount = accountFinder.apply(notification.getTransactionId());

                    if (!gatewayAccount.isPresent()) {
                        return NO_UPDATE;
                    }

                    StatusUpdates statusUpdates = newStatusFromNotification(gatewayAccount.get(), notification.getTransactionId());

                    if (statusUpdates.successful()) {
                        logMismatchingStatuses(notification, statusUpdates);
                        accountUpdater.accept(statusUpdates);
                    }
                    return statusUpdates;
                }
        ).orElseGet(() -> NO_UPDATE);
    }

    private void logMismatchingStatuses(WorldpayNotification notification, StatusUpdates statusUpdates) {
        statusUpdates.getStatusUpdates()
                .stream()
                .findFirst()
                .ifPresent(status -> {
                    if(status.getValue().equals(notification.getChargeStatus().get())) {
                        logger.error(format("Inquiry status '%s' did not match notification status '%s'",
                                status.getValue(),
                                notification.getChargeStatus().get()));
                    }
        });
    }

    private StatusUpdates newStatusFromNotification(GatewayAccountEntity gatewayAccount, String transactionId) {
        InquiryResponse inquiryResponse = inquire(transactionId, gatewayAccount);
        String worldpayStatus = inquiryResponse.getNewStatus();
        if (!inquiryResponse.isSuccessful() || StringUtils.isBlank(worldpayStatus)) {
            logger.error("Could not look up status from worldpay for worldpay charge id " + transactionId);
            return StatusUpdates.failed();
        }

        Optional<ChargeStatus> newChargeStatus = WorldpayStatusesMapper.mapToChargeStatus(worldpayStatus);
        if (!newChargeStatus.isPresent()) {
            logger.error(format("Could not map worldpay status %s to our internal status.", worldpayStatus));
            return NO_UPDATE;
        }

        Pair<String, ChargeStatus> update = Pair.of(inquiryResponse.getTransactionId(), newChargeStatus.get());
        return StatusUpdates.withUpdate(NOTIFICATION_ACKNOWLEDGED, ImmutableList.of(update));
    }


    private InquiryResponse inquire(String transactionId, GatewayAccountEntity gatewayAccount) {
        return reduce(
                client
                        .postXMLRequestFor(gatewayAccount, buildOrderInquiryFor(gatewayAccount, transactionId))
                        .bimap(
                                InquiryResponse::inquiryFailureResponse,
                                (response) -> response.getStatus() == OK.getStatusCode() ?
                                        mapToInquiryResponse(response) :
                                        errorInquiryResponse(logger, response)
                        )
        );
    }

    private String buildOrderCaptureFor(CaptureRequest request) {
        return aWorldpayOrderCaptureRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .withDate(DateTime.now(DateTimeZone.UTC))
                .build();
    }

    private String buildOrderSubmitFor(AuthorisationRequest request, String gatewayTransactionId) {
        return aWorldpayOrderSubmitRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(gatewayTransactionId)
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();
    }

    private String buildCancelOrderFor(CancelRequest request) {
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

    private AuthorisationResponse mapToCardAuthorisationResponse(Response response, String gatewayTransactionId) {
        return reduce(
                client.unmarshallResponse(response, WorldpayOrderStatusResponse.class)
                        .bimap(
                                AuthorisationResponse::authorisationFailureResponse,
                                (wResponse) -> {
                                    if (wResponse.isError()) {
                                        return authorisationFailureNotUpdateResponse(logger, gatewayTransactionId, wResponse.getErrorMessage());
                                    }
                                    return wResponse.isAuthorised() ?
                                            successfulAuthorisationResponse(AUTHORISATION_SUCCESS, gatewayTransactionId) :
                                            authorisationFailureResponse(logger, gatewayTransactionId, "Unauthorised");
                                }
                        )
        );
    }

    private CaptureResponse mapToCaptureResponse(Response response) {
        return reduce(
                client.unmarshallResponse(response, WorldpayCaptureResponse.class)
                        .bimap(
                                CaptureResponse::captureFailureResponse,
                                (wResponse) -> wResponse.isCaptured() ?
                                        successfulCaptureResponse(CAPTURE_SUBMITTED) :
                                        captureFailureResponse(logger, wResponse.getErrorMessage())
                        )
        );
    }

    private InquiryResponse mapToInquiryResponse(Response response) {
        return reduce(
                client.unmarshallResponse(response, WorldpayOrderStatusResponse.class)
                        .bimap(
                                InquiryResponse::inquiryFailureResponse,
                                (wResponse) -> wResponse.isError() ?
                                        inquiryFailureResponse(baseError(wResponse.getErrorMessage())) :
                                        inquiryStatusUpdate(wResponse.getTransactionId(), wResponse.getLastEvent())

                        )
        );
    }

    private CancelResponse mapToCancelResponse(Response response) {
        return reduce(
                client.unmarshallResponse(response, WorldpayCancelResponse.class)
                        .bimap(
                                CancelResponse::cancelFailureResponse,
                                (wResponse) -> wResponse.isCancelled() ?
                                        aSuccessfulCancelResponse() :
                                        cancelFailureResponse(logger, wResponse.getErrorMessage())
                        )
        );
    }

    private String generateTransactionId() {
        return randomUUID().toString();
    }
}
