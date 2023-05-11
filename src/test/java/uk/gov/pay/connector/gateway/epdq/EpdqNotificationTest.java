package uk.gov.pay.connector.gateway.epdq;

import org.apache.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_NOTIFICATION_TEMPLATE;

class EpdqNotificationTest {
    private static final String CARDHOLDER_NAME = "mr payment";
    private static final String STATUS = "9";
    private static final String PAY_ID = "3020450409";
    private static final String PAY_ID_SUB = "2";
    private static final String SHA_SIGN = "9537B9639F108CDF004459D8A690C598D97506CDF072C3926A60E39759A6402C5089161F6D7A8EA12BBC0FD6F899CE72D5A0C4ACC2913C56ACF6D01B034EEC32";

    @Test
    void shouldParsePayload() throws EpdqParseException {
        String payload = notificationPayloadForTransaction(CARDHOLDER_NAME, STATUS, PAY_ID, PAY_ID_SUB, SHA_SIGN);

        EpdqNotification epdqNotification = new EpdqNotification(payload);

        assertThat(epdqNotification.getStatus(), is(STATUS));
        assertThat(epdqNotification.getPayId(), is(PAY_ID));
        assertThat(epdqNotification.getPayIdSub(), is(PAY_ID_SUB));
        assertThat(epdqNotification.getShaSign(), is(SHA_SIGN));
    }

    @Test
    void shouldHaveReferenceIfPayIdAndPaySubId() throws EpdqParseException {
        String payload = notificationPayloadForTransaction(CARDHOLDER_NAME, STATUS, PAY_ID, PAY_ID_SUB, SHA_SIGN);

        EpdqNotification epdqNotification = new EpdqNotification(payload);

        assertThat(epdqNotification.getReference(), is(PAY_ID + "/" + PAY_ID_SUB));
    }

    @Test
    void shouldHaveNoReferenceIfNoPayId() throws EpdqParseException {
        String payload = notificationPayloadForTransaction(CARDHOLDER_NAME, STATUS, "", PAY_ID_SUB, SHA_SIGN);

        EpdqNotification epdqNotification = new EpdqNotification(payload);

        assertThat(epdqNotification.getReference(), is(""));
    }

    @Test
    void shouldHaveNoReferenceIfNoPayIdSub() throws EpdqParseException {
        String payload = notificationPayloadForTransaction(CARDHOLDER_NAME, STATUS, PAY_ID, "", SHA_SIGN);

        EpdqNotification epdqNotification = new EpdqNotification(payload);

        assertThat(epdqNotification.getReference(), is(""));
    }

    @Test()
    void shouldFailToParseMalformedPayload() {
        Assertions.assertThrows(EpdqParseException.class, () -> new EpdqNotification("malformed") );
    }



    @Test
    void shouldReturnParams() throws EpdqParseException {
        String payload = notificationPayloadForTransaction(CARDHOLDER_NAME, STATUS, PAY_ID, PAY_ID_SUB, SHA_SIGN);

        EpdqNotification epdqNotification = new EpdqNotification(payload);

        assertThat(epdqNotification.getParams(), is(Arrays.asList(
                new BasicNameValuePair("orderID", "7256785436"),
                new BasicNameValuePair("currency", "GBP"),
                new BasicNameValuePair("amount", "5"),
                new BasicNameValuePair("PM", "CreditCard"),
                new BasicNameValuePair("ACCEPTANCE", "testoff"),
                new BasicNameValuePair("STATUS", STATUS),
                new BasicNameValuePair("CARDNO", "XXXXXXXXXXXX4242"),
                new BasicNameValuePair("ED", "0919"),
                new BasicNameValuePair("CN", CARDHOLDER_NAME),
                new BasicNameValuePair("TRXDATE", "05/23/17"),
                new BasicNameValuePair("PAYID", PAY_ID),
                new BasicNameValuePair("PAYIDSUB", PAY_ID_SUB),
                new BasicNameValuePair("NCERROR", ""),
                new BasicNameValuePair("BRAND", "VISA"),
                new BasicNameValuePair("IP", ""),
                new BasicNameValuePair("SHASIGN", SHA_SIGN)
        )));
    }

    @Test
    void shouldDecodeNotificationsAccordingToTheRightEpdqCharset() throws EpdqParseException {
        String cardHolderName = "Mr O%92Payment"; // %92 is encoded value for ’ (right single quotation mark)
        String payload = notificationPayloadForTransaction(cardHolderName, STATUS, PAY_ID, PAY_ID_SUB, SHA_SIGN);

        EpdqNotification epdqNotification = new EpdqNotification(payload);

        assertThat(epdqNotification.getParams(), hasItem(
                new BasicNameValuePair("CN", "Mr O’Payment")));
    }

    private String notificationPayloadForTransaction(String cardHolderName, String status, String payId, String payIdSub, String shaSign) {
        return TestTemplateResourceLoader.load(EPDQ_NOTIFICATION_TEMPLATE)
                .replace("{{cardHolderName}}", cardHolderName)
                .replace("{{status}}", status)
                .replace("{{payId}}", payId)
                .replace("{{payIdSub}}", payIdSub)
                .replace("{{shaSign}}", shaSign);
    }
}
