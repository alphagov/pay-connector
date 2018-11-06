package uk.gov.pay.connector.gateway.util;

import fj.data.Either;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;

public class GatewayResponseGenerator {

    public static GatewayResponse<BaseResponse> getSmartpayGatewayResponse(GatewayClient client, Either<GatewayError, GatewayClient.Response> response, Class<? extends BaseResponse> responseClass) {
        if (response.isLeft()) {
            return GatewayResponse.with(response.left().value());
        } else {
            GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
            client.unmarshallResponse(response.right().value(), responseClass)
                    .bimap(
                            responseBuilder::withGatewayError,
                            responseBuilder::withResponse
                    );
            return responseBuilder.build();
        }
    }

    public static GatewayResponse<BaseResponse> getEpdqGatewayResponse(GatewayClient client, Either<GatewayError, GatewayClient.Response> response, Class<? extends BaseResponse> responseClass) {
        if (response.isLeft()) {
            return GatewayResponse.with(response.left().value());
        } else {
            GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
            client.unmarshallResponse(response.right().value(), responseClass)
                    .bimap(
                            responseBuilder::withGatewayError,
                            responseBuilder::withResponse
                    );
            return responseBuilder.build();
        }
    }

    public static GatewayResponse<BaseResponse> getWorldpayGatewayResponse(GatewayClient client, Either<GatewayError, GatewayClient.Response> response, Class<? extends BaseResponse> responseClass
                                                                   ) {
        if (response.isLeft()) {
            return GatewayResponse.with(response.left().value());
        } else {
            GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
            client.unmarshallResponse(response.right().value(), responseClass)
                    .bimap(
                            responseBuilder::withGatewayError,
                            responseBuilder::withResponse
                    );
            Optional.ofNullable(response.right().value().getResponseCookies().get(WORLDPAY_MACHINE_COOKIE_NAME))
                    .ifPresent(responseBuilder::withSessionIdentifier);
            return responseBuilder.build();
        }
    }
}
