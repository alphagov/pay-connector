package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;

public interface WorldpayGatewayResponseGenerator {

    default GatewayResponse getWorldpayGatewayResponse(GatewayClient.Response response) throws GatewayErrorException {
        return getWorldpayGatewayResponse(response, WorldpayOrderStatusResponse.class);
    }
    
    default <T extends BaseResponse> GatewayResponse<T> getWorldpayGatewayResponse(GatewayClient.Response response, Class<T> target) throws GatewayErrorException {
        GatewayResponse.GatewayResponseBuilder<T> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        responseBuilder.withResponse(unmarshallResponse(response, target));
        Optional.ofNullable(response.getResponseCookies().get(
                WORLDPAY_MACHINE_COOKIE_NAME))
                .map(ProviderSessionIdentifier::of).ifPresent(responseBuilder::withSessionIdentifier);
        return responseBuilder.build();
    }
}
