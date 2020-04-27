package uk.gov.pay.connector.gateway.epdq;

import org.junit.Test;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCaptureOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CAPTURE_REQUEST;

public class EpdqPayloadDefinitionForCaptureOrderTest {

    @Test
    public void assert_payload_and_order_request_type_are_as_expected() {
        EpdqTemplateData templateData = new EpdqTemplateData();
        templateData.setPassword("password");
        templateData.setUserId("username");
        templateData.setShaInPassphrase("sha-passphrase");
        templateData.setMerchantCode("merchant-id");
        templateData.setTransactionId("payId");

        GatewayOrder gatewayOrder = new EpdqPayloadDefinitionForCaptureOrder().createGatewayOrder(templateData);
        assertEquals(TestTemplateResourceLoader.load(EPDQ_CAPTURE_REQUEST), gatewayOrder.getPayload());
        assertEquals(OrderRequestType.CAPTURE, gatewayOrder.getOrderRequestType());
    }
}
