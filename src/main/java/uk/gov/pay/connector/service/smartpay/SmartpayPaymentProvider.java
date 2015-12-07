package uk.gov.pay.connector.service.smartpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.ServiceAccount;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static fj.data.Either.reduce;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.AuthorisationResponse.authorisationFailureResponse;
import static uk.gov.pay.connector.model.AuthorisationResponse.successfulAuthorisation;
import static uk.gov.pay.connector.model.CancelResponse.aSuccessfulCancelResponse;
import static uk.gov.pay.connector.model.CancelResponse.cancelFailureResponse;
import static uk.gov.pay.connector.model.CaptureResponse.aSuccessfulCaptureResponse;
import static uk.gov.pay.connector.model.CaptureResponse.captureFailureResponse;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
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
                        .postXMLRequestFor(request.getServiceAccount(), requestString)
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
                        .postXMLRequestFor(request.getServiceAccount(), captureRequestString)
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
                        .postXMLRequestFor(request.getServiceAccount(), buildCancelOrderFor(request))
                        .bimap(
                                CancelResponse::cancelFailureResponse,
                                this::mapToCancelResponse
                        )
        );
    }

    @Override
    public StatusUpdates newStatusFromNotification(ServiceAccount serviceAccount, String inboundNotification) {
        try {
            List<SmartpayNotification> notifications = objectMapper.readValue(inboundNotification, SmartpayNotificationList.class).getNotifications();
            Collections.sort(notifications);

            List<Pair<String, ChargeStatus>> updates = notifications.stream()
                    .filter(this::definedStatuses)
                    .map(this::toInternalStatus)
                    .collect(Collectors.toList());

            return StatusUpdates.withUpdate(ACCEPTED, updates);
        } catch (IllegalArgumentException | IOException e) {
            // If we've failed to parse the message, we don't want it to be resent - there's no reason to believe our
            // deterministic computer code could successfully parse the same message if it arrived a second time.
            // Barclays also mandate that acknowledging notifications should be unconditional.
            // See http://www.barclaycard.co.uk/business/files/SmartPay_Notifications_Guide.pdf for further details.
            logger.error(String.format("Could not deserialise smartpay notification:\n %s", inboundNotification), e);
        }
        return StatusUpdates.noUpdate(ACCEPTED);
    }

    @Override
    public Optional<String> getNotificationTransactionId(String inboundNotification) {
        return null;
    }

    private boolean definedStatuses(SmartpayNotification notification) {
        String smartpayStatus = notification.getEventCode();
        Optional<ChargeStatus> newChargeStatus = SmartpayStatusMapper.mapToChargeStatus(smartpayStatus, notification.isSuccessFull());
        if (!newChargeStatus.isPresent()) {
            logger.error(format("Could not map Smartpay status %s to our internal status.", notification.getEventCode()));
        }
        return newChargeStatus.isPresent();
    }

    private Pair<String, ChargeStatus> toInternalStatus(SmartpayNotification notification) {
        String smartpayStatus = notification.getEventCode();
        Optional<ChargeStatus> newChargeStatus = SmartpayStatusMapper.mapToChargeStatus(smartpayStatus, notification.isSuccessFull());
        return Pair.of(notification.getTransactionId(), newChargeStatus.get());
    }

    private AuthorisationResponse mapToCardAuthorisationResponse(Response response) {
        return reduce(
                client.unmarshallResponse(response, SmartpayAuthorisationResponse.class)
                        .bimap(
                                AuthorisationResponse::authorisationFailureResponse,
                                (sResponse) -> sResponse.isAuthorised() ?
                                        successfulAuthorisation(AUTHORISATION_SUCCESS, sResponse.getPspReference()) :
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
                                        aSuccessfulCaptureResponse() :
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
