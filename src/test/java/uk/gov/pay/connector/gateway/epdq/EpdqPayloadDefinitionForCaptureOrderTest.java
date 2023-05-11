package uk.gov.pay.connector.gateway.epdq;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCaptureOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CAPTURE_REQUEST;

class EpdqPayloadDefinitionForCaptureOrderTest {

    @Test
    void assert_payload_and_order_request_type_are_as_expected() {
        var epdqPayloadDefinitionForCaptureOrder = new EpdqPayloadDefinitionForCaptureOrder();
        epdqPayloadDefinitionForCaptureOrder.setPassword("password");
        epdqPayloadDefinitionForCaptureOrder.setUserId("username");
        epdqPayloadDefinitionForCaptureOrder.setPspId("merchant-id");
        epdqPayloadDefinitionForCaptureOrder.setPayId("payId");
        epdqPayloadDefinitionForCaptureOrder.setShaInPassphrase("sha-passphrase");
        GatewayOrder gatewayOrder = epdqPayloadDefinitionForCaptureOrder.createGatewayOrder();

        assertEquals(TestTemplateResourceLoader.load(EPDQ_CAPTURE_REQUEST), gatewayOrder.getPayload());
        assertEquals(OrderRequestType.CAPTURE, gatewayOrder.getOrderRequestType());
    }

}
