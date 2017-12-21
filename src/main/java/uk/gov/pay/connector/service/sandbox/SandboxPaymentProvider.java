package uk.gov.pay.connector.service.sandbox;

import fj.data.Either;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayRequest;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.service.BaseAuthoriseResponse;
import uk.gov.pay.connector.service.BaseCancelResponse;
import uk.gov.pay.connector.service.BaseCaptureResponse;
import uk.gov.pay.connector.service.BaseRefundResponse;
import uk.gov.pay.connector.service.BaseResponse;
import uk.gov.pay.connector.service.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.GatewayOperation;
import uk.gov.pay.connector.service.GatewayOrder;
import uk.gov.pay.connector.service.PaymentGatewayName;
import uk.gov.pay.connector.service.PaymentProviderNotificationHandler;
import uk.gov.pay.connector.service.PaymentProviderOperations;
import uk.gov.pay.connector.service.StatusMapper;

import java.util.EnumMap;
import java.util.Optional;
import java.util.function.Function;

import static fj.data.Either.reduce;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.cardErrorFor;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.isErrorCard;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.isRejectedCard;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.isValidCard;

public class SandboxPaymentProvider implements PaymentProviderOperations, PaymentProviderNotificationHandler<String> {

    protected boolean isNotificationEndpointSecured;
    protected String notificationDomain;
    protected ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    protected EnumMap<GatewayOperation, GatewayClient> gatewayOperationClientMap;

    public SandboxPaymentProvider(ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator) {
        this.gatewayOperationClientMap = null;
        this.isNotificationEndpointSecured = false;
        this.notificationDomain = null;
        this.externalRefundAvailabilityCalculator = externalRefundAvailabilityCalculator;
    }

    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        String cardNumber = request.getAuthCardDetails().getCardNo();
        GatewayResponseBuilder<BaseResponse> gatewayResponseBuilder = responseBuilder();

        if (isErrorCard(cardNumber)) {
            CardError errorInfo = cardErrorFor(cardNumber);
            return gatewayResponseBuilder
                    .withGatewayError(new GatewayError(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR))
                    .build();
        } else if (isRejectedCard(cardNumber)) {
            return createGatewayBaseAuthoriseResponse(false);
        } else if (isValidCard(cardNumber)) {
            return createGatewayBaseAuthoriseResponse(true);
        }

        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("Unsupported card details.", GENERIC_GATEWAY_ERROR))
                .build();
    }

    public GatewayResponse<BaseAuthoriseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("3D Secure not implemented for Sandbox", GENERIC_GATEWAY_ERROR))
                .build();
    }

    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.SANDBOX;
    }

    public Optional<String> generateTransactionId() {
        return Optional.of(randomUUID().toString());
    }

    public GatewayResponse capture(CaptureGatewayRequest request) {
        return createGatewayBaseCaptureResponse();
    }

    public GatewayResponse cancel(CancelGatewayRequest request) {
        return createGatewayBaseCancelResponse();
    }

    @Override
    public Boolean isNotificationEndpointSecured() {
        return false;
    }

    @Override
    public String getNotificationDomain() {
        return null;
    }

    @Override
    public boolean verifyNotification(Notification notification, GatewayAccountEntity gatewayAccountEntity) {
        return true;
    }

    public GatewayResponse refund(RefundGatewayRequest request) {
        return createGatewayBaseRefundResponse(request);
    }

    @Override
    public Either<String, Notifications<String>> parseNotification(String payload) {
        throw new UnsupportedOperationException("Sandbox account does not support notifications");
    }

    public StatusMapper<String> getStatusMapper() {
        return SandboxStatusMapper.get();
    }

    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    private GatewayResponse<BaseAuthoriseResponse> createGatewayBaseAuthoriseResponse(boolean isAuthorised) {
        GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseAuthoriseResponse() {

            private final String transactionId = randomUUID().toString();

            @Override
            public AuthoriseStatus authoriseStatus() {
                return isAuthorised ? AuthoriseStatus.AUTHORISED : AuthoriseStatus.REJECTED;
            }

            @Override
            public String getTransactionId() {
                return transactionId;
            }

            @Override
            public String getErrorCode() {
                return null;
            }

            @Override
            public String getErrorMessage() {
                return null;
            }

            @Override
            public String get3dsPaRequest() {
                return null;
            }

            @Override
            public String get3dsIssuerUrl() {
                return null;
            }

            @Override
            public String toString() {
                return "Sandbox authorisation response (transactionId: " + getTransactionId()
                        + ", isAuthorised: " + isAuthorised + ')';
            }
        }).build();
    }

    private GatewayResponse<BaseCancelResponse> createGatewayBaseCancelResponse() {
        GatewayResponseBuilder<BaseCancelResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseCancelResponse() {

            private final String transactionId = randomUUID().toString();

            @Override
            public String getErrorCode() {
                return null;
            }

            @Override
            public String getErrorMessage() {
                return null;
            }

            @Override
            public String getTransactionId() {
                return transactionId;
            }

            @Override
            public CancelStatus cancelStatus() {
                return CancelStatus.CANCELLED;
            }

            @Override
            public String toString() {
                return "Sandbox cancel response (transactionId: " + getTransactionId() + ')';
            }
        }).build();
    }

    private GatewayResponse<BaseCaptureResponse> createGatewayBaseCaptureResponse() {
        GatewayResponseBuilder<BaseCaptureResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseCaptureResponse() {

            private final String transactionId = randomUUID().toString();

            @Override
            public String getErrorCode() {
                return null;
            }

            @Override
            public String getErrorMessage() {
                return null;
            }

            @Override
            public String getTransactionId() {
                return transactionId;
            }

            @Override
            public String toString() {
                return "Sandbox capture response (transactionId: " + getTransactionId() + ')';
            }
        }).build();
    }

    private GatewayResponse<BaseRefundResponse> createGatewayBaseRefundResponse(RefundGatewayRequest request) {
        GatewayResponseBuilder<BaseRefundResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseRefundResponse() {
            @Override
            public Optional<String> getReference() {
                return Optional.of(request.getReference());
            }

            @Override
            public String getErrorCode() {
                return null;
            }

            @Override
            public String getErrorMessage() {
                return null;
            }

            @Override
            public String toString() {
                return getReference()
                        .map(reference -> "Sandbox refund response (reference: " + reference + ')')
                        .orElse("Sandbox refund response");
            }
        }).build();
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(U request, Function<U, GatewayOrder> order,
                                                                     Class<? extends BaseResponse> clazz,
                                                                     Function<GatewayClient.Response, Optional<String>> responseIdentifier) {

        return sendReceive(null, request, order, clazz, responseIdentifier);
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(String route, U request, Function<U, GatewayOrder> order,
                                                                     Class<? extends BaseResponse> clazz,
                                                                     Function<GatewayClient.Response, Optional<String>> responseIdentifier) {
        GatewayClient gatewayClient = gatewayOperationClientMap.get(request.getRequestType());
        return reduce(
                gatewayClient
                        .postRequestFor(route, request.getGatewayAccount(), order.apply(request))
                        .bimap(
                                GatewayResponse::with,
                                r -> mapToResponse(r, clazz, responseIdentifier, gatewayClient)
                        )
        );
    }

    private GatewayResponse mapToResponse(GatewayClient.Response response,
                                          Class<? extends BaseResponse> clazz,
                                          Function<GatewayClient.Response, Optional<String>> responseIdentifier,
                                          GatewayClient client) {
        GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponseBuilder.responseBuilder();

        reduce(
                client.unmarshallResponse(response, clazz)
                        .bimap(
                                responseBuilder::withGatewayError,
                                responseBuilder::withResponse
                        )
        );

        responseIdentifier.apply(response)
                .ifPresent(responseBuilder::withSessionIdentifier);

        return responseBuilder.build();

    }
}
