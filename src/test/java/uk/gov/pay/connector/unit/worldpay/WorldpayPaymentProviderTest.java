package uk.gov.pay.connector.unit.worldpay;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.GatewayErrorType.GenericGatewayError;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;

public class WorldpayPaymentProviderTest {
    private WorldpayPaymentProvider connector;
    private Client client;

    @Before
    public void setup() throws Exception {
        client = mock(Client.class);
        mockWorldpaySuccessfulOrderSubmitResponse();

        connector = new WorldpayPaymentProvider(
                createGatewayClient(client, "http://smartpay.url"),
                gatewayAccountFor("theUsername", "thePassword")
        );
    }

    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {
        mockWorldpaySuccessfulOrderSubmitResponse();

        AuthorisationResponse response = connector.authorise(getCardAuthorisationRequest());
        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldCaptureAPaymentSuccessfully() throws Exception {
        mockWorldpaySuccessfulCaptureResponse();

        CaptureResponse response = connector.capture(getCaptureRequest());
        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldErrorIfAuthorisationIsUnsuccessful() {
        mockWorldpayErrorResponse(401);
        AuthorisationResponse response = connector.authorise(getCardAuthorisationRequest());

        assertThat(response.isSuccessful(), is(false));
        assertEquals(response.getError(), new GatewayError("Error processing request", GenericGatewayError));
    }

    @Test
    public void shouldErrorIfOrderReferenceNotKnownInCapture() {
        mockWorldpayErrorResponse(200);
        CaptureResponse response = connector.capture(getCaptureRequest());

        assertThat(response.isSuccessful(), is(false));
        assertEquals(response.getError(), new GatewayError("Order has already been paid", GenericGatewayError));
    }

    @Test
    public void shouldErrorIfWorldpayResponseIsNot200() {
        mockWorldpayErrorResponse(400);
        CaptureResponse response = connector.capture(getCaptureRequest());

        assertThat(response.isSuccessful(), is(false));
        assertEquals(response.getError(), new GatewayError("Error processing capture request", GenericGatewayError));
    }

    private AuthorisationRequest getCardAuthorisationRequest() {
        Card card = getValidTestCard();
        String amount = "500";

        String description = "This is the description";
        return new AuthorisationRequest(card, amount, description);
    }

    private void assertEquals(GatewayError actual, GatewayError expected) {
        assertNotNull(actual);
        assertThat(actual.getMessage(), is(expected.getMessage()));
        assertThat(actual.getErrorType(), is(expected.getErrorType()));

    }

    private CaptureRequest getCaptureRequest() {
        return new CaptureRequest("500", randomUUID().toString());
    }

    private void mockWorldpayErrorResponse(int httpStatus) {
        mockWorldpayResponse(httpStatus, errorResponse());
    }

    private void mockWorldpaySuccessfulCaptureResponse() {
        mockWorldpayResponse(200, successCaptureResponse());
    }

    private void mockWorldpaySuccessfulOrderSubmitResponse() {
        mockWorldpayResponse(200, successAuthoriseResponse());
    }

    private void mockWorldpayResponse(int httpStatus, String responsePayload) {
        WebTarget mockTarget = mock(WebTarget.class);
        when(client.target(anyString())).thenReturn(mockTarget);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        when(mockTarget.request(APPLICATION_XML)).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), anyObject())).thenReturn(mockBuilder);

        Response response = mock(Response.class);
        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(mockBuilder.post(any(Entity.class))).thenReturn(response);

        when(response.getStatus()).thenReturn(httpStatus);
    }

    private String successCaptureResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <reply>\n" +
                "        <ok>\n" +
                "            <captureReceived orderCode=\"MyUniqueTransactionId!\">\n" +
                "                <amount value=\"500\" currencyCode=\"GBP\" exponent=\"2\" debitCreditIndicator=\"credit\"/>\n" +
                "            </captureReceived>\n" +
                "        </ok>\n" +
                "    </reply>\n" +
                "</paymentService>";
    }

    private String errorResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <reply>\n" +
                "        <error code=\"5\">\n" +
                "            <![CDATA[Order has already been paid]]>\n" +
                "        </error>\n" +
                "    </reply>\n" +
                "</paymentService>";
    }

    private String successAuthoriseResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <reply>\n" +
                "        <orderStatus orderCode=\"MyUniqueTransactionId!22233\">\n" +
                "            <payment>\n" +
                "                <paymentMethod>VISA-SSL</paymentMethod>\n" +
                "                <paymentMethodDetail>\n" +
                "                    <card number=\"4444********1111\" type=\"creditcard\">\n" +
                "                        <expiryDate>\n" +
                "                            <date month=\"11\" year=\"2099\"/>\n" +
                "                        </expiryDate>\n" +
                "                    </card>\n" +
                "                </paymentMethodDetail>\n" +
                "                <amount value=\"500\" currencyCode=\"GBP\" exponent=\"2\" debitCreditIndicator=\"credit\"/>\n" +
                "                <lastEvent>AUTHORISED</lastEvent>\n" +
                "                <AuthorisationId id=\"666\"/>\n" +
                "                <CVCResultCode description=\"NOT SENT TO ACQUIRER\"/>\n" +
                "                <AVSResultCode description=\"NOT SENT TO ACQUIRER\"/>\n" +
                "                <cardHolderName>\n" +
                "                    <![CDATA[Coucou]]>\n" +
                "                </cardHolderName>\n" +
                "                <issuerCountryCode>N/A</issuerCountryCode>\n" +
                "                <balance accountType=\"IN_PROCESS_AUTHORISED\">\n" +
                "                    <amount value=\"500\" currencyCode=\"GBP\" exponent=\"2\" debitCreditIndicator=\"credit\"/>\n" +
                "                </balance>\n" +
                "                <riskScore value=\"51\"/>\n" +
                "            </payment>\n" +
                "        </orderStatus>\n" +
                "    </reply>\n" +
                "</paymentService>";
    }

    private Card getValidTestCard() {
        Address address = anAddress();
        address.setLine1("123 My Street");
        address.setLine2("This road");
        address.setPostcode("SW8URR");
        address.setCity("London");
        address.setCounty("London state");
        address.setCountry("GB");

        return buildCardDetails("Mr. Payment", "4111111111111111", "123", "12/15", address);
    }

}