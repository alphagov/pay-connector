package uk.gov.pay.connector.model.gateway;

import org.junit.Test;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.service.BaseResponse;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;

public class GatewayResponseTest {

    @Test
    public void shouldHandleAGatewayError() {
        GatewayError error = new GatewayError(
                "an error message",
                GENERIC_GATEWAY_ERROR);

        GatewayResponse<BaseResponse> gatewayResponse = GatewayResponse.with(error);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getGatewayError().get(), is(error));
    }


    @Test
    public void shouldHandleAValidGatewayResponse() {
        BaseResponse baseResponse = createBaseResponseWith(null, null);
        GatewayResponse<BaseResponse> gatewayResponse = GatewayResponse.with(baseResponse);
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get(), is(baseResponse));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
    }

    @Test
    public void shouldHandleAGatewayResponseWithAnErrorCode() {
        GatewayError error = new GatewayError(
                "[errorCode]",
                GENERIC_GATEWAY_ERROR);

        BaseResponse baseResponse = createBaseResponseWith("errorCode", null);
        GatewayResponse<BaseResponse> gatewayResponse = GatewayResponse.with(baseResponse);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), is(error.getMessage()));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldHandleAGatewayResponseWithAnErrorMessage() {
        GatewayError error = new GatewayError(
                "an error",
                GENERIC_GATEWAY_ERROR);

        BaseResponse baseResponse = createBaseResponseWith(null, "an error");
        GatewayResponse<BaseResponse> gatewayResponse = GatewayResponse.with(baseResponse);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), is(error.getMessage()));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldHandleAGatewayResponseWithAnErrorCodeAndMessage() {
        GatewayError error = new GatewayError(
                "[123] oops, something went wrong",
                GENERIC_GATEWAY_ERROR);

        BaseResponse baseResponse = createBaseResponseWith("123", "oops, something went wrong");
        GatewayResponse<BaseResponse> gatewayResponse = GatewayResponse.with(baseResponse);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), is(error.getMessage()));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    private static BaseResponse createBaseResponseWith(String errorCode, String errorMessage) {
        return new BaseResponse() {
            @Override
            public String getTransactionId() {
                return null;
            }

            @Override
            public String getErrorCode() {
                return errorCode;
            }

            @Override
            public String getErrorMessage() {
                return errorMessage;
            }
        };
    }
}