package uk.gov.pay.connector.gateway;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderInquiryResponse;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISED_INQUIRY_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CANCELLED_INQUIRY_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CAPTURED_INQUIRY_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_REJECTED_INQUIRY_RESPONSE;

public class ChargeQueryResponseTest {
    @Test 
    public void fromWorldpayResponse_shouldCorrectlyConvertFromWorldpayAuthorisedToAuthorised() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_AUTHORISED_INQUIRY_RESPONSE);
        WorldpayOrderInquiryResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderInquiryResponse.class);
        
        ChargeQueryResponse chargeQueryResponse = ChargeQueryResponse.from(response);
        
        assertThat(chargeQueryResponse.getMappedStatus().get(), is(ChargeStatus.AUTHORISATION_SUCCESS));
        assertNotNull(chargeQueryResponse.getRawGatewayResponse());
    }

    @Test
    public void fromWorldpayResponse_shouldCorrectlyConvertFromWorldpayCapturedToCaptured() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_CAPTURED_INQUIRY_RESPONSE);
        WorldpayOrderInquiryResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderInquiryResponse.class);

        ChargeQueryResponse chargeQueryResponse = ChargeQueryResponse.from(response);

        assertThat(chargeQueryResponse.getMappedStatus().get(), is(ChargeStatus.CAPTURED));
        assertNotNull(chargeQueryResponse.getRawGatewayResponse());
    }
    @Test
    public void fromWorldpayResponse_shouldCorrectlyConvertFromWorldpayRefusedToAuthorisationRejected() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_REJECTED_INQUIRY_RESPONSE);
        WorldpayOrderInquiryResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderInquiryResponse.class);

        ChargeQueryResponse chargeQueryResponse = ChargeQueryResponse.from(response);

        assertThat(chargeQueryResponse.getMappedStatus().get(), is(ChargeStatus.AUTHORISATION_REJECTED));
        assertNotNull(chargeQueryResponse.getRawGatewayResponse());
    }
    @Test
    public void fromWorldpayResponse_shouldCorrectlyConvertFromWorldpayCancelledToCancelled() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_CANCELLED_INQUIRY_RESPONSE);
        WorldpayOrderInquiryResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderInquiryResponse.class);

        ChargeQueryResponse chargeQueryResponse = ChargeQueryResponse.from(response);

        assertThat(chargeQueryResponse.getMappedStatus().get(), is(ChargeStatus.AUTHORISATION_CANCELLED));
        assertNotNull(chargeQueryResponse.getRawGatewayResponse());
    }
    
    
    @Test
    public void fromWorldpayResponse_shouldReturnEmptyOptionalIfUnknownStatus() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_ERROR_RESPONSE);
        WorldpayOrderInquiryResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderInquiryResponse.class);

        ChargeQueryResponse chargeQueryResponse = ChargeQueryResponse.from(response);

        assertFalse(chargeQueryResponse.getMappedStatus().isPresent());
    }
}
