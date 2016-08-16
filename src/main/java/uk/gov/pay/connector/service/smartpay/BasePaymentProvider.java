package uk.gov.pay.connector.service.smartpay;

import uk.gov.pay.connector.model.GatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseResponse;
import uk.gov.pay.connector.service.GatewayClient;

import java.util.function.Function;

import static fj.data.Either.reduce;

public class BasePaymentProvider {
    private GatewayClient client;

    public BasePaymentProvider(GatewayClient client) {
        this.client = client;
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(U request, Function<U, String> order, Class<? extends BaseResponse> clazz) {
        return reduce(
                client
                        .postXMLRequestFor(request.getGatewayAccount(), order.apply(request))
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
