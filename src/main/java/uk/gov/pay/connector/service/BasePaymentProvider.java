package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.GatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder;

import java.util.EnumMap;
import java.util.Optional;
import java.util.function.Function;

import static fj.data.Either.reduce;

abstract public class BasePaymentProvider<T extends BaseResponse> implements PaymentProvider<T> {

    protected boolean isNotificationEndpointSecured;
    protected String notificationDomain;
    protected EnumMap<GatewayOperation, GatewayClient> gatewayOperationClientMap;

    public BasePaymentProvider(EnumMap<GatewayOperation, GatewayClient> clients) {
        this(clients, false, null);
    }

    public BasePaymentProvider(EnumMap<GatewayOperation, GatewayClient> operationClients, boolean isNotificationEndpointSecured, String notificationDomain) {
        this.gatewayOperationClientMap = operationClients;
        this.isNotificationEndpointSecured = isNotificationEndpointSecured;
        this.notificationDomain = notificationDomain;
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(U request, Function<U, GatewayOrder> order,
                                                                     Class<? extends BaseResponse> clazz,
                                                                     Function<GatewayClient.Response, Optional<String>> responseIdentifier) {
        return sendReceive(null, request, order, clazz, responseIdentifier);
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(String target, U request,
                                                                     Function<U, GatewayOrder> order,
                                                                     Class<? extends BaseResponse> clazz,
                                                                     Function<GatewayClient.Response, Optional<String>> responseIdentifier) {
        GatewayClient gatewayClient = gatewayOperationClientMap.get(request.getRequestType());
        return reduce(
                gatewayClient
                        .postRequestFor(target, request.getGatewayAccount(), order.apply(request))
                        .bimap(
                                GatewayResponse::with,
                                r -> mapToResponse(r, clazz, responseIdentifier, gatewayClient)
                        )
        );
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(U request, GatewayOperation operation, Function<U, GatewayOrder> order,
                                                                     Class<? extends BaseResponse> clazz,
                                                                     Function<GatewayClient.Response, Optional<String>> responseIdentifier) {

        GatewayClient gatewayClient = gatewayOperationClientMap.get(operation);
        return reduce(
                gatewayClient
                        .postRequestFor(null, request.getGatewayAccount(), order.apply(request))
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
