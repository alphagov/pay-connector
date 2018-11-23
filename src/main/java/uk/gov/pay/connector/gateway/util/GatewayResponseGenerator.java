package uk.gov.pay.connector.gateway.util;

import fj.data.Either;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;

public class GatewayResponseGenerator {

    public static GatewayResponse getSmartpayGatewayResponse(GatewayClient client, Either<GatewayError, GatewayClient.Response> response, Class<? extends BaseResponse> responseClass) {
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

    public static GatewayResponse getEpdqGatewayResponse(GatewayClient client, Either<GatewayError, GatewayClient.Response> response, Class<? extends BaseResponse> responseClass) {
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

    public static GatewayResponse getWorldpayGatewayResponse(GatewayClient client, Either<GatewayError, GatewayClient.Response> response, Class<? extends BaseResponse> responseClass
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

    public static GatewayResponse getSandboxGatewayResponse(boolean isAuthorised) {
        GatewayResponse.GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
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
            public Optional<GatewayParamsFor3ds> getGatewayParamsFor3ds() {
                return Optional.empty();
            }

            @Override
            public String toString() {
                return "Sandbox authorisation response (transactionId: " + getTransactionId()
                        + ", isAuthorised: " + isAuthorised + ')';
            }
        }).build();
    }
}
