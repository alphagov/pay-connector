package uk.gov.pay.connector.service.smartpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayCaptureResponse;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;

public class SmartpayPaymentProviderTest {

    private Client client;
    private SmartpayPaymentProvider provider;

    @Before
    public void setup() throws Exception {
        client = mock(Client.class);
        mockSmartpaySuccessfulOrderSubmitResponse();
        provider = new SmartpayPaymentProvider(createGatewayClient(client, ImmutableMap.of(TEST.toString(), "http://smartpay.url")), new ObjectMapper());
    }

    @Test
    public void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName(), is("smartpay"));
    }

    @Test
    public void shouldGetStatusMapper() {
        assertThat(provider.getStatusMapper(), sameInstance(SmartpayStatusMapper.get()));
    }

    @Test
    public void shouldGenerateTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Test
    public void shouldGenerateRefundReference() {
        Assert.assertThat(provider.generateRefundReference().isPresent(), is(false));
    }

    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {

        Card card = getValidTestCard();

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();

        GatewayResponse<SmartpayAuthorisationResponse> response = provider.authorise(new AuthorisationGatewayRequest(chargeEntity, card));

        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().isPresent(), CoreMatchers.is(true));
        String transactionId = response.getBaseResponse().get().getPspReference();
        assertThat(transactionId, CoreMatchers.is(not(nullValue())));
    }

    @Test
    public void shouldCaptureAPaymentSuccessfully() throws Exception {

        mockSmartpaySuccessfulCaptureResponse();

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();

        GatewayResponse<WorldpayCaptureResponse> response = provider.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertTrue(response.isSuccessful());
    }

    @Test
    public void parseNotification_shouldReturnErrorIfUnparseableSoapMessage() {
        Either<String, Notifications<Pair<String, Boolean>>> response = provider.parseNotification("not valid soap message");
        assertThat(response.isLeft(), is(true));
        assertThat(response.left().value(), containsString("not valid soap message"));
    }

    @Test
    public void parseNotification_shouldReturnNotificationsIfValidSoapMessage() throws IOException {
        String originalReference = "originalReference";
        String pspReference = "pspReference";
        String merchantReference = "merchantReference";

        Either<String, Notifications<Pair<String, Boolean>>> response = provider.parseNotification(
                notificationPayloadForTransaction(originalReference, pspReference, merchantReference, "notification-capture"));

        assertThat(response.isRight(), is(true));
        ImmutableList<Notification<Pair<String, Boolean>>> notifications = response.right().value().get();

        assertThat(notifications.size(), is(1));

        Notification<Pair<String, Boolean>> smartpayNotification = notifications.get(0);

        assertThat(smartpayNotification.getTransactionId(), is(pspReference));
        assertThat(smartpayNotification.getReference(), is(pspReference));

        Pair<String, Boolean> status = smartpayNotification.getStatus();
        assertThat(status.getLeft(), is("CAPTURE"));
        assertThat(status.getRight(), is(true));
    }

    private GatewayAccountEntity aServiceAccount() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("smartpay");
        gatewayAccount.setCredentials(ImmutableMap.of(
                "username", "theUsername",
                "password", "thePassword",
                "merchant_id", "theMerchantCode"
        ));
        gatewayAccount.setType(TEST);

        return gatewayAccount;
    }

    private void mockSmartpaySuccessfulOrderSubmitResponse() {
        mockSmartpayResponse(200, successAuthoriseResponse());
    }

    private void mockSmartpaySuccessfulCaptureResponse() {
        mockSmartpayResponse(200, successCaptureResponse());
    }

    private void mockSmartpayResponse(int httpStatus, String responsePayload) {
        WebTarget mockTarget = mock(WebTarget.class);
        when(client.target(anyString())).thenReturn(mockTarget);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        when(mockTarget.request(APPLICATION_XML)).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), anyObject())).thenReturn(mockBuilder);

        Response response = mock(Response.class);
        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(mockBuilder.post(Matchers.any(Entity.class))).thenReturn(response);

        when(response.getStatus()).thenReturn(httpStatus);
    }

    private String successAuthoriseResponse() {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
                "               xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "    <soap:Body>\n" +
                "        <ns1:authoriseResponse xmlns:ns1=\"http://payment.services.adyen.com\">\n" +
                "            <ns1:paymentResult>\n" +
                "                <additionalData xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <authCode xmlns=\"http://payment.services.adyen.com\">87802</authCode>\n" +
                "                <dccAmount xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <dccSignature xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <fraudResult xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <issuerUrl xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <md xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <paRequest xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <pspReference xmlns=\"http://payment.services.adyen.com\">12345678</pspReference>\n" +
                "                <refusalReason xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <resultCode xmlns=\"http://payment.services.adyen.com\">Authorised</resultCode>\n" +
                "            </ns1:paymentResult>\n" +
                "        </ns1:authoriseResponse>\n" +
                "    </soap:Body>\n" +
                "</soap:Envelope>";
    }

    private String successCaptureResponse() {
        return "<ns0:Envelope xmlns:ns0=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ns1=\"http://payment.services.adyen.com\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "    <ns0:Body>\n" +
                "        <ns1:captureResponse>\n" +
                "            <ns1:captureResult>\n" +
                "                <ns1:additionalData xsi:nil=\"true\" />\n" +
                "                <ns1:pspReference>8614440510830227</ns1:pspReference>\n" +
                "                <ns1:response>[capture-received]</ns1:response>\n" +
                "            </ns1:captureResult>\n" +
                "        </ns1:captureResponse>\n" +
                "    </ns0:Body>\n" +
                "</ns0:Envelope>";
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

    private String notificationPayloadForTransaction(String originalReference, String pspReference, String merchantReference, String fileName) throws IOException {
        URL resource = getResource("templates/smartpay/" + fileName + ".json");
        return Resources.toString(resource, Charset.defaultCharset())
                .replace("{{originalReference}}", originalReference)
                .replace("{{pspReference}}", pspReference)
                .replace("{{merchantReference}}", merchantReference);
    }
}
