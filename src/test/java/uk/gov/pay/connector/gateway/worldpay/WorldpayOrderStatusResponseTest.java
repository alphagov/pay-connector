package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldpayOrderStatusResponseTest {

    @ParameterizedTest
    @ValueSource(strings = { "OUT_OF_SCOPE", "REJECTED", })
    void worldpay_response_should_be_soft_decline(String exemptionResponseResult) {
        var worldpayOrderStatusResponse = new WorldpayOrderStatusResponse();
        worldpayOrderStatusResponse.setLastEvent("REFUSED");
        worldpayOrderStatusResponse.setExemptionResponseResult(exemptionResponseResult);

        assertTrue(worldpayOrderStatusResponse.isSoftDecline());
    }

    @Test
    void worldpay_response_should_not_be_soft_decline() {
        var worldpayOrderStatusResponse = new WorldpayOrderStatusResponse();
        worldpayOrderStatusResponse.setLastEvent("REFUSED");
        worldpayOrderStatusResponse.setExemptionResponseResult("HONOURED");

        assertFalse(worldpayOrderStatusResponse.isSoftDecline());
    }
}
