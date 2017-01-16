package uk.gov.pay.connector.service.worldpay;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import fj.data.Either;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.*;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.GatewayOrder;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.Map;

import static com.google.common.io.Resources.getResource;
import static fj.data.Either.left;
import static java.lang.String.*;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.ErrorType.UNEXPECTED_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.model.GatewayError.unexpectedStatusCodeFromGateway;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.GatewayAccount.*;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayPaymentProviderTest {

    private WorldpayPaymentProvider provider;
    private Client client;

    @Mock
    private GatewayClient mockGatewayClient;
    @Mock
    GatewayAccountEntity mockGatewayAccountEntity;
    @Mock
    MetricRegistry mockMetricRegistry;
    @Mock
    Histogram mockHistogram;
    @Mock
    Counter mockCounter;

    @Before
    public void setup() throws Exception {
        client = mock(Client.class);
        mockWorldpaySuccessfulOrderSubmitResponse();
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        provider = new WorldpayPaymentProvider(
                createGatewayClient(client, ImmutableMap.of(TEST.toString(), "http://worldpay.url"), mockMetricRegistry));
    }

    @Test
    public void shouldGetPaymentProviderName() {
        Assert.assertThat(provider.getPaymentGatewayName(), is("worldpay"));
    }

    @Test
    public void shouldGetStatusMapper() {
        Assert.assertThat(provider.getStatusMapper(), sameInstance(WorldpayStatusMapper.get()));
    }

    @Test
    public void shouldGenerateTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(true));
        Assert.assertThat(provider.generateTransactionId().get(), is(instanceOf(String.class)));
    }

    @Test
    public void shouldGenerateRefundReference() {
        Assert.assertThat(provider.generateRefundReference().isPresent(), is(true));
        Assert.assertThat(provider.generateRefundReference().get(), is(instanceOf(String.class)));
    }

    @Test
    public void testRefundRequestContainsReference() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().build();
        chargeEntity.setGatewayTransactionId("transaction-id");
        chargeEntity.setGatewayAccount(mockGatewayAccountEntity);
        refundEntity.setChargeEntity(chargeEntity);

        Map<String, String> credentialsMap = ImmutableMap.of("merchant_id", "MERCHANTCODE");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialsMap);
        when(mockGatewayClient.postXMLRequestFor(any(GatewayAccountEntity.class), any(GatewayOrder.class))).thenReturn(left(unexpectedStatusCodeFromGateway("Unexpected Response Code From Gateway")));

        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(mockGatewayClient);
        worldpayPaymentProvider.refund(RefundGatewayRequest.valueOf(refundEntity));

        String expectedRefundRequest =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <modify>\n" +
                "        <orderModification orderCode=\"transaction-id\">\n" +
                "            <refund reference=\"" +refundEntity.getExternalId()+ "\">\n" +
                "                <amount currencyCode=\"GBP\" exponent=\"2\" value=\"500\"/>\n" +
                "            </refund>\n" +
                "        </orderModification>\n" +
                "    </modify>\n" +
                "</paymentService>\n" +
                "";

        verify(mockGatewayClient).postXMLRequestFor(eq(mockGatewayAccountEntity), argThat(new ArgumentMatcher<GatewayOrder>() {
            @Override
            public boolean matches(Object argument) {
                return ((GatewayOrder) argument).getPayload().equals(expectedRefundRequest) &&
                        ((GatewayOrder) argument).getType().equals("refund");
            }
        }));
    }

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        GatewayResponse<WorldpayOrderStatusResponse> response = provider.authorise(getCardAuthorisationRequest());
        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldCaptureAPaymentSuccessfully() throws Exception {
        mockWorldpaySuccessfulCaptureResponse();

        GatewayResponse response = provider.capture(getCaptureRequest());
        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldErrorIfAuthorisationIsUnsuccessful() {
        mockWorldpayErrorResponse(401);
        GatewayResponse<WorldpayOrderStatusResponse> response = provider.authorise(getCardAuthorisationRequest());

        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected Response Code From Gateway", UNEXPECTED_STATUS_CODE_FROM_GATEWAY));
    }

    @Test
    public void shouldErrorIfOrderReferenceNotKnownInCapture() {
        mockWorldpayErrorResponse(200);
        GatewayResponse<WorldpayCaptureResponse> response = provider.capture(getCaptureRequest());

        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("[5] Order has already been paid", GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldErrorIfWorldpayResponseIsNot200() {
        mockWorldpayErrorResponse(400);
        GatewayResponse<WorldpayCaptureResponse> response = provider.capture(getCaptureRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected Response Code From Gateway", UNEXPECTED_STATUS_CODE_FROM_GATEWAY));
    }

    @Test
    public void parseNotification_shouldReturnErrorIfUnparseableXml() {
        Either<String, Notifications<String>> response = provider.parseNotification("not valid xml");
        assertThat(response.isLeft(), is(true));
        assertThat(response.left().value(), startsWith("javax.xml.bind.UnmarshalException"));
    }

    @Test
    public void parseNotification_shouldReturnNotificationsIfValidXml() throws IOException {
        String transactionId = "transaction-id";
        String referenceId = "reference-id";
        String status = "CHARGED";
        String bookingDateDay = "10";
        String bookingDateMonth = "03";
        String bookingDateYear = "2017";
        Either<String, Notifications<String>> response = provider.parseNotification(notificationPayloadForTransaction(transactionId, referenceId, status, bookingDateDay, bookingDateMonth, bookingDateYear));
        assertThat(response.isRight(), is(true));

        ImmutableList<Notification<String>> notifications = response.right().value().get();

        assertThat(notifications.size(), is(1));

        Notification<String> worldpayNotification = notifications.get(0);

        assertThat(worldpayNotification.getTransactionId(), is(transactionId));
        assertThat(worldpayNotification.getReference(), is(referenceId));
        assertThat(worldpayNotification.getStatus(), is(status));
        assertThat(worldpayNotification.getGatewayEventDate(), is(ZonedDateTime.parse(format("%s-%s-%sT00:00Z", bookingDateYear, bookingDateMonth, bookingDateDay))));
    }

    private String notificationPayloadForTransaction(
            String transactionId,
            String referenceId,
            String status,
            String bookingDateDay,
            String bookingDateMonth,
            String bookingDateYear) throws IOException {
        URL resource = getResource("templates/worldpay/notification.xml");
        return Resources.toString(resource, Charset.defaultCharset())
                .replace("{{transactionId}}", transactionId)
                .replace("{{refund-ref}}", referenceId)
                .replace("{{status}}", status)
                .replace("{{bookingDateDay}}", bookingDateDay)
                .replace("{{bookingDateMonth}}", bookingDateMonth)
                .replace("{{bookingDateYear}}", bookingDateYear);
    }

    private AuthorisationGatewayRequest getCardAuthorisationRequest() {
        Card card = getValidTestCard();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();
        return new AuthorisationGatewayRequest(chargeEntity, card);
    }

    private GatewayAccountEntity aServiceAccount() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("worldpay");
        gatewayAccount.setCredentials(ImmutableMap.of(
                CREDENTIALS_MERCHANT_ID, "worlpay-merchant",
                CREDENTIALS_USERNAME, "worldpay-password",
                CREDENTIALS_PASSWORD, "password"
        ));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }

    private void assertEquals(GatewayError actual, GatewayError expected) {
        assertNotNull(actual);
        assertThat(actual.getMessage(), is(expected.getMessage()));
        assertThat(actual.getErrorType(), is(expected.getErrorType()));
    }

    private CaptureGatewayRequest getCaptureRequest() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();
        return CaptureGatewayRequest.valueOf(chargeEntity);
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

        return buildCardDetails("Mr. Payment", "4111111111111111", "123", "12/15", "visa", address);
    }

}
