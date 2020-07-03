package uk.gov.pay.connector.gateway.epdq;

import org.junit.Test;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForRefundOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_REFUND_REQUEST;

public class EpdqPayloadDefinitionForRefundOrderTest {

    @Test
    public void assert_payload_and_order_request_type_are_as_expected() {
        var epdqPayloadDefinitionForRefundOrder = new EpdqPayloadDefinitionForRefundOrder();
        epdqPayloadDefinitionForRefundOrder.setPassword("password");
        epdqPayloadDefinitionForRefundOrder.setUserId("username");
        epdqPayloadDefinitionForRefundOrder.setPspId("merchant-id");
        epdqPayloadDefinitionForRefundOrder.setPayId("payId");
        epdqPayloadDefinitionForRefundOrder.setAmount("400");
        epdqPayloadDefinitionForRefundOrder.setShaInPassphrase("sha-passphrase");
        GatewayOrder gatewayOrder = epdqPayloadDefinitionForRefundOrder.createGatewayOrder();

        assertEquals(TestTemplateResourceLoader.load(EPDQ_REFUND_REQUEST), gatewayOrder.getPayload());
        assertEquals(OrderRequestType.REFUND, gatewayOrder.getOrderRequestType());
    }

}
