package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.GatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import java.util.function.Function;

import static fj.data.Either.reduce;

abstract public class BasePaymentProvider<T extends BaseResponse> implements PaymentProvider<T> {

    private GatewayClient client;
    protected boolean isNotificationEndpointSecured;
    protected String notificationDomain;

    public BasePaymentProvider(GatewayClient client) {
        this(client, false, null);
    }

    public BasePaymentProvider(GatewayClient client, boolean isNotificationEndpointSecured, String notificationDomain) {
        this.client = client;
        this.isNotificationEndpointSecured = isNotificationEndpointSecured;
        this.notificationDomain = notificationDomain;
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(U request, Function<U, GatewayOrder> order, Class<? extends BaseResponse> clazz) {
        return sendReceive(null, request, order, clazz);
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(String target, U request, Function<U, GatewayOrder> order, Class<? extends BaseResponse> clazz) {
        return reduce(
                client
                        .postRequestFor(target, request.getGatewayAccount(), order.apply(request))
                        .bimap(
                                GatewayResponse::with,
                                r -> mapToResponse(r, clazz)
                        )
        );
    }

    private GatewayResponse mapToResponse(GatewayClient.Response response, Class<? extends BaseResponse> clazz) {
        return reduce(
                client.unmarshallResponse(response, clazz)
                        .bimap(
                                GatewayResponse::with,
                                GatewayResponse::with
                        )
        );
    }
}
