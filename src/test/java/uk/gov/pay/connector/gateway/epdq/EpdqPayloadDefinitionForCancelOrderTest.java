package uk.gov.pay.connector.gateway.epdq;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCancelOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_REQUEST_WITH_ORDERID;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_REQUEST_WITH_PAYID;

class EpdqPayloadDefinitionForCancelOrderTest {

    @Test
    void payloadIsAsExpectedWithPayId() {
        var epdqPayloadDefinitionForCancelOrder = new EpdqPayloadDefinitionForCancelOrder();
        epdqPayloadDefinitionForCancelOrder.setPassword("password");
        epdqPayloadDefinitionForCancelOrder.setUserId("username");
        epdqPayloadDefinitionForCancelOrder.setPspId("merchant-id");
        epdqPayloadDefinitionForCancelOrder.setPayId("payId");
        epdqPayloadDefinitionForCancelOrder.setShaInPassphrase("sha-passphrase");
        GatewayOrder gatewayOrder = epdqPayloadDefinitionForCancelOrder.createGatewayOrder();

        assertEquals(TestTemplateResourceLoader.load(EPDQ_CANCEL_REQUEST_WITH_PAYID), gatewayOrder.getPayload());
        assertEquals(OrderRequestType.CANCEL, gatewayOrder.getOrderRequestType());
    }

    @Test
    void payloadIsAsExpectedWithOrderId() {
        var epdqPayloadDefinitionForCancelOrder = new EpdqPayloadDefinitionForCancelOrder();
        epdqPayloadDefinitionForCancelOrder.setPassword("password");
        epdqPayloadDefinitionForCancelOrder.setUserId("username");
        epdqPayloadDefinitionForCancelOrder.setPspId("merchant-id");
        epdqPayloadDefinitionForCancelOrder.setOrderId("Order-Id");
        epdqPayloadDefinitionForCancelOrder.setShaInPassphrase("sha-passphrase");
        GatewayOrder gatewayOrder = epdqPayloadDefinitionForCancelOrder.createGatewayOrder();

        assertEquals(TestTemplateResourceLoader.load(EPDQ_CANCEL_REQUEST_WITH_ORDERID), gatewayOrder.getPayload());
        assertEquals(OrderRequestType.CANCEL, gatewayOrder.getOrderRequestType());
    }

}
