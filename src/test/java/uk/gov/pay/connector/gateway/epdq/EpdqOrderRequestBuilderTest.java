package uk.gov.pay.connector.gateway.epdq;

import org.junit.Test;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.util.AuthUtils;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqRefundOrderRequestBuilder;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CAPTURE_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_REFUND_REQUEST;

public class EpdqOrderRequestBuilderTest {

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestForAddressWithAllFields()  {
        Address address = new Address("41", "Scala Street", "EC2A 1AE", "London", "London", "GB");
        AuthCardDetails authCardDetails = AuthUtils.buildAuthCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", address);

        GatewayOrder actualRequest = anEpdqAuthoriseOrderRequestBuilder()
                .withOrderId("mq4ht90j2oir6am585afk58kml")
                .withPassword("password")
                .withUserId("username")
                .withShaInPassphrase("sha-passphrase")
                .withMerchantCode("merchant-id")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertEquals(TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCaptureOrderRequest() throws Exception {
        GatewayOrder actualRequest = anEpdqCaptureOrderRequestBuilder()
                .withPassword("password")
                .withUserId("username")
                .withShaInPassphrase("sha-passphrase")
                .withMerchantCode("merchant-id")
                .withTransactionId("payId")
                .build();

        assertEquals(TestTemplateResourceLoader.load(EPDQ_CAPTURE_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.CAPTURE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCancelOrderRequest() throws Exception {
        GatewayOrder actualRequest = anEpdqCancelOrderRequestBuilder()
                .withPassword("password")
                .withUserId("username")
                .withShaInPassphrase("sha-passphrase")
                .withMerchantCode("merchant-id")
                .withTransactionId("payId")
                .build();

        assertEquals(TestTemplateResourceLoader.load(EPDQ_CANCEL_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.CANCEL, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidRefundOrderRequest() {

        GatewayOrder gatewayOrder = anEpdqRefundOrderRequestBuilder()
                .withPassword("password")
                .withUserId("username")
                .withShaInPassphrase("sha-passphrase")
                .withMerchantCode("merchant-id")
                .withTransactionId("payId")
                .withAmount("400")
                .build();

        assertEquals(TestTemplateResourceLoader.load(EPDQ_REFUND_REQUEST), gatewayOrder.getPayload());
        assertEquals(OrderRequestType.REFUND, gatewayOrder.getOrderRequestType());
    }
}
