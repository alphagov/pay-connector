package uk.gov.pay.connector.gateway.model.response;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;


 class GatewayResponseTest {

    @Test
     void shouldHandleAGatewayError() {
        GatewayError error = new GatewayError(
                "an error message",
                GENERIC_GATEWAY_ERROR);

        GatewayResponseBuilder<BaseResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse<BaseResponse> gatewayResponse = gatewayResponseBuilder
                .withGatewayError(error)
                .build();
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getGatewayError().get(), is(error));
    }


    @Test
     void shouldHandleAValidGatewayResponse() {
        BaseResponse baseResponse = createBaseResponseWith(null, null);
        GatewayResponseBuilder<BaseResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse<BaseResponse> gatewayResponse = gatewayResponseBuilder
                .withResponse(baseResponse)
                .build();
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get(), is(baseResponse));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
    }

    @Test
     void shouldHandleAGatewayResponseWithAnErrorCodeAndMessage() {
        BaseResponse baseResponse = createBaseResponseWith("123", "oops, something went wrong");
        GatewayResponseBuilder<BaseResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse<BaseResponse> gatewayResponse = gatewayResponseBuilder
                .withResponse(baseResponse)
                .build();
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(),
                is("Randompay something response (errorCode: 123, errorMessage: oops, something went wrong)"));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    private static BaseResponse createBaseResponseWith(String errorCode, String errorMessage) {
        return new BaseResponse() {

            @Override
            public String getErrorCode() {
                return errorCode;
            }

            @Override
            public String getErrorMessage() {
                return errorMessage;
            }

            @Override
            public String toString() {
                return "Randompay something response (errorCode: " + errorCode + ", errorMessage: " + errorMessage + ')';
            }
        };
    }
}
