package uk.gov.pay.connector.service.smartpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fj.data.Either.reduce;
import static uk.gov.pay.connector.model.AuthorisationResponse.authorisationFailureResponse;
import static uk.gov.pay.connector.model.AuthorisationResponse.successfulAuthorisationResponse;
import static uk.gov.pay.connector.model.CancelResponse.aSuccessfulCancelResponse;
import static uk.gov.pay.connector.model.CancelResponse.cancelFailureResponse;
import static uk.gov.pay.connector.model.CaptureResponse.captureFailureResponse;
import static uk.gov.pay.connector.model.CaptureResponse.successfulCaptureResponse;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aSmartpayOrderCaptureRequest;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aSmartpayOrderSubmitRequest;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderCancelRequestBuilder.aSmartpayOrderCancelRequest;

public class SmartpayPaymentProvider implements PaymentProvider {
    private static final String MERCHANT_CODE = "MerchantAccount";
    public static final String ACCEPTED = "[accepted]";
    private final Logger logger = LoggerFactory.getLogger(SmartpayPaymentProvider.class);

    private final GatewayClient client;
    private ObjectMapper objectMapper;

    public SmartpayPaymentProvider(GatewayClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {
        String requestString = buildOrderSubmitFor(request);

        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), requestString)
                        .bimap(
                                AuthorisationResponse::authorisationFailureResponse,
                                this::mapToCardAuthorisationResponse
                        )
        );
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {
        String captureRequestString = buildOrderCaptureFor(request);

        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), captureRequestString)
                        .bimap(
                                CaptureResponse::captureFailureResponse,
                                this::mapToCaptureResponse
                        )
        );
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), buildCancelOrderFor(request))
                        .bimap(
                                CancelResponse::cancelFailureResponse,
                                this::mapToCancelResponse
                        )
        );
    }

    @Override
    public StatusUpdates handleNotification(String inboundNotification,
                                            Function<ChargeStatusRequest, Boolean> payloadChecks,
                                            Function<String, Optional<GatewayAccountEntity>> accountFinder,
                                            Consumer<StatusUpdates> accountUpdater) {
        try {
            List<SmartpayNotification> notifications = objectMapper.readValue(inboundNotification, SmartpayNotificationList.class).getNotifications();
            Collections.sort(notifications);

            List<Pair<String, ChargeStatus>> updates = notifications.stream()
                    .map(this::extendInternalStatus)
                    .filter((notification) -> notification.getChargeStatus().isPresent())
                    .filter((notification) -> payloadChecks.apply(notification))
                    .map(this::toPair)
                    .collect(Collectors.toList());

            StatusUpdates statusUpdates = StatusUpdates.withUpdate(ACCEPTED, updates);
            accountUpdater.accept(statusUpdates);
            return statusUpdates;
        } catch (IllegalArgumentException | IOException e) {
            // If we've failed to parse the message, we don't want it to be resent - there's no reason to believe our
            // deterministic computer code could successfully parse the same message if it arrived a second time.
            // Barclays also mandate that acknowledging notifications should be unconditional.
            // See http://www.barclaycard.co.uk/business/files/SmartPay_Notifications_Guide.pdf for further details.
            logger.error(String.format("Could not deserialise smartpay notification:\n %s", inboundNotification), e);
        }
        return StatusUpdates.noUpdate(ACCEPTED);
    }

    private SmartpayNotification extendInternalStatus(SmartpayNotification notification) {
        notification.setChargeStatus(SmartpayStatusMapper.mapToChargeStatus(
                notification.getEventCode(), notification.isSuccessFull()));
        return notification;
    }

    private Pair<String, ChargeStatus> toPair(SmartpayNotification notification) {
        return Pair.of(notification.getTransactionId(), notification.getChargeStatus().get());
    }

    private AuthorisationResponse mapToCardAuthorisationResponse(Response response) {
        return reduce(
                client.unmarshallResponse(response, SmartpayAuthorisationResponse.class)
                        .bimap(
                                AuthorisationResponse::authorisationFailureResponse,
                                (sResponse) -> sResponse.isAuthorised() ?
                                        successfulAuthorisationResponse(AUTHORISATION_SUCCESS, sResponse.getPspReference()) :
                                        authorisationFailureResponse(logger, sResponse.getPspReference(), sResponse.getErrorMessage())
                        )
        );
    }

    private CaptureResponse mapToCaptureResponse(Response response) {
        return reduce(
                client.unmarshallResponse(response, SmartpayCaptureResponse.class)
                        .bimap(
                                CaptureResponse::captureFailureResponse,
                                (sResponse) -> sResponse.isCaptured() ?
                                        successfulCaptureResponse(CAPTURE_SUBMITTED) :
                                        captureFailureResponse(logger, sResponse.getErrorMessage(), sResponse.getPspReference())
                        )
        );
    }

    private CancelResponse mapToCancelResponse(Response response) {
        return reduce(
                client.unmarshallResponse(response, SmartpayCancelResponse.class)
                        .bimap(
                                CancelResponse::cancelFailureResponse,
                                (sResponse) -> sResponse.isCancelled() ?
                                        aSuccessfulCancelResponse() :
                                        cancelFailureResponse(logger, sResponse.getErrorMessage())
                        )
        );
    }

    private String buildOrderSubmitFor(AuthorisationRequest request) {
        return aSmartpayOrderSubmitRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withPaymentPlatformReference(request.getChargeId())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();
    }

    private String buildCancelOrderFor(CancelRequest request) {
        return aSmartpayOrderCancelRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private String buildOrderCaptureFor(CaptureRequest request) {
        return aSmartpayOrderCaptureRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .build();
    }
}
