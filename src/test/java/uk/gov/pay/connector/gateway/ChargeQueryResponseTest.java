package uk.gov.pay.connector.gateway;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.worldpay.WorldpayQueryResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;

class ChargeQueryResponseTest {

    @Test
    void getRawGatewayResponseOrErrorMessage_shouldReturnGatewayResponseIfAvailable() {
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_ERROR, new WorldpayQueryResponse());
        assertEquals("Worldpay query response ()", chargeQueryResponse.getRawGatewayResponseOrErrorMessage());
    }

    @Test
    void getRawGatewayResponseOrErrorMessage_shouldReturnErrorMessageWhenAvailableAndGatewayResponseIsNotAvailable() {
        GatewayError gatewayError = GatewayError.genericGatewayError("order not found");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(gatewayError);
        assertEquals("order not found", chargeQueryResponse.getRawGatewayResponseOrErrorMessage());
    }
}
