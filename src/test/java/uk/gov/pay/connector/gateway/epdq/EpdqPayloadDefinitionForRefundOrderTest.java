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
        EpdqTemplateData templateData = new EpdqTemplateData();
        templateData.setPassword("password");
        templateData.setUserId("username");
        templateData.setShaInPassphrase("sha-passphrase");
        templateData.setMerchantCode("merchant-id");
        templateData.setTransactionId("payId");
        templateData.setAmount("400");

        GatewayOrder gatewayOrder = new EpdqPayloadDefinitionForRefundOrder().createGatewayOrder(templateData);
        assertEquals(TestTemplateResourceLoader.load(EPDQ_REFUND_REQUEST), gatewayOrder.getPayload());
        assertEquals(OrderRequestType.REFUND, gatewayOrder.getOrderRequestType());
    }
}
