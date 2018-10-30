package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;

import java.util.EnumMap;
import java.util.Optional;
import java.util.function.Function;

import static fj.data.Either.reduce;

abstract public class BasePaymentProvider<T extends BaseResponse, R> implements PaymentProvider<T, R> {

    protected boolean isNotificationEndpointSecured;
    protected String notificationDomain;
    protected ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    protected EnumMap<GatewayOperation, GatewayClient> gatewayOperationClientMap;

    public BasePaymentProvider(EnumMap<GatewayOperation, GatewayClient> clients, ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator) {
        this(clients, false, null, externalRefundAvailabilityCalculator);
    }

    public BasePaymentProvider(EnumMap<GatewayOperation, GatewayClient> operationClients, boolean isNotificationEndpointSecured, String notificationDomain, ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator) {
        this.gatewayOperationClientMap = operationClients;
        this.isNotificationEndpointSecured = isNotificationEndpointSecured;
        this.notificationDomain = notificationDomain;
        this.externalRefundAvailabilityCalculator = externalRefundAvailabilityCalculator;
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(U request, 
                                                                     Function<U, GatewayOrder> order,
                                                                     Class<? extends BaseResponse> clazz,
                                                                     Function<GatewayClient.Response, Optional<String>> responseIdentifier) {

        return sendReceive(null, request, order, clazz, responseIdentifier);
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(String route, 
                                                                     U request, 
                                                                     Function<U, GatewayOrder> order,
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
