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
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;

import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;
import java.util.Optional;

import static fj.data.Either.reduce;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.AuthorisationResponse.*;
import static uk.gov.pay.connector.model.CancelResponse.aSuccessfulCancelResponse;
import static uk.gov.pay.connector.model.CancelResponse.cancelFailureResponse;
import static uk.gov.pay.connector.model.CaptureResponse.aSuccessfulCaptureResponse;
import static uk.gov.pay.connector.model.CaptureResponse.captureFailureResponse;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;
import static uk.gov.pay.connector.model.InquiryResponse.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
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
    private final GatewayAccount gatewayAccount;

    public WorldpayPaymentProvider(GatewayClient client, GatewayAccount gatewayAccount) {
        this.client = client;
        this.gatewayAccount = gatewayAccount;
    }

    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {
        String gatewayTransactionId = generateTransactionId();
        return reduce(
                client
                        .postXMLRequestFor(gatewayAccount, buildOrderSubmitFor(request, gatewayTransactionId))
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
                        .postXMLRequestFor(gatewayAccount, requestString)
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
                        .postXMLRequestFor(gatewayAccount, requestString)
                        .bimap(
                                CancelResponse::cancelFailureResponse,
                                this::mapToCancelResponse
                        )
        );
    }

    @Override
    public StatusUpdates newStatusFromNotification(String notification) {
        try {
            WorldpayNotification chargeNotification = unmarshall(notification, WorldpayNotification.class);
            InquiryResponse inquiryResponse = inquire(chargeNotification);

            String worldpayStatus = inquiryResponse.getNewStatus();
            if (!inquiryResponse.isSuccessful() || StringUtils.isBlank(worldpayStatus)) {
                logger.error("Could not look up status from worldpay for worldpay charge id " + chargeNotification.getTransactionId());
                return StatusUpdates.failed();
            }

            Optional<ChargeStatus> newChargeStatus = WorldpayStatusesMapper.mapToChargeStatus(worldpayStatus);
            if (!newChargeStatus.isPresent()) {
                logger.error(format("Could not map worldpay status %s to our internal status.", worldpayStatus));
                return NO_UPDATE;
            }

            Pair<String, ChargeStatus> update = Pair.of(inquiryResponse.getTransactionId(), newChargeStatus.get());
            return StatusUpdates.withUpdate(NOTIFICATION_ACKNOWLEDGED, ImmutableList.of(update));
        } catch (JAXBException e) {
            logger.error(format("Could not deserialise worldpay response %s", notification), e);
            return NO_UPDATE;
        }
    }

    private InquiryResponse inquire(ChargeStatusRequest request) {
        return reduce(
                client
                        .postXMLRequestFor(gatewayAccount, buildOrderInquiryFor(request))
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
                .withMerchantCode(gatewayAccount.getUsername())
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .withDate(DateTime.now(DateTimeZone.UTC))
                .build();
    }

    private String buildOrderSubmitFor(AuthorisationRequest request, String gatewayTransactionId) {
        return aWorldpayOrderSubmitRequest()
                .withMerchantCode(gatewayAccount.getUsername())
                .withTransactionId(gatewayTransactionId)
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();
    }

    private String buildCancelOrderFor(CancelRequest request) {
        return aWorldpayOrderCancelRequest()
                .withMerchantCode(gatewayAccount.getUsername())
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private String buildOrderInquiryFor(ChargeStatusRequest request) {
        return anOrderInquiryRequest()
                .withMerchantCode(gatewayAccount.getUsername()) //TODO: map to the merchant code, not the username!
                .withTransactionId(request.getTransactionId())
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
                                            successfulAuthorisation(AUTHORISATION_SUCCESS, gatewayTransactionId) :
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
                                        aSuccessfulCaptureResponse() :
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
                                        inquiryFailureResponse(baseGatewayError(wResponse.getErrorMessage())) :
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
