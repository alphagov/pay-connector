package uk.gov.pay.connector.service.epdq;

import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.io.IOException;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_NOTIFICATION_TEMPLATE;

public class EpdqNotificationTest {

  public static final String ORDER_ID = "2jhqgrb71f47ftq9u1t5c1143o";
  public static final String STATUS = "9";
  public static final String PAY_ID = "3020450409";
  public static final String SHA_SIGN = "9537B9639F108CDF004459D8A690C598D97506CDF072C3926A60E39759A6402C5089161F6D7A8EA12BBC0FD6F899CE72D5A0C4ACC2913C56ACF6D01B034EEC32";

  @Test
  public void shouldParsePayload() throws IOException {
    String payload = notificationPayloadForTransaction(ORDER_ID, STATUS, PAY_ID, SHA_SIGN);

    EpdqNotification epdqNotification = new EpdqNotification(payload);

    assertThat(epdqNotification.getOrderId(), is(ORDER_ID));
    assertThat(epdqNotification.getStatus(), is(STATUS));
    assertThat(epdqNotification.getPayId(), is(PAY_ID));
    assertThat(epdqNotification.getShaSign(), is(SHA_SIGN));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldFailToParseMalformedPayload() throws IOException {
    new EpdqNotification("malformed");
  }

  @Test
  public void shouldReturnParams() throws IOException {
    String payload = notificationPayloadForTransaction(ORDER_ID, STATUS, PAY_ID, SHA_SIGN);

    EpdqNotification epdqNotification = new EpdqNotification(payload);

    assertThat(epdqNotification.getParams(), is(Arrays.asList(
            new BasicNameValuePair("orderID", ORDER_ID),
            new BasicNameValuePair("currency", "GBP"),
            new BasicNameValuePair("amount", "5"),
            new BasicNameValuePair("PM", "CreditCard"),
            new BasicNameValuePair("ACCEPTANCE", "testoff"),
            new BasicNameValuePair("STATUS", STATUS),
            new BasicNameValuePair("CARDNO", "XXXXXXXXXXXX4242"),
            new BasicNameValuePair("ED", "0919"),
            new BasicNameValuePair("CN", ""),
            new BasicNameValuePair("TRXDATE", "05/23/17"),
            new BasicNameValuePair("PAYID", PAY_ID),
            new BasicNameValuePair("NCERROR", ""),
            new BasicNameValuePair("BRAND", "VISA"),
            new BasicNameValuePair("IP", ""),
            new BasicNameValuePair("SHASIGN", SHA_SIGN)
    )));
  }

  private String notificationPayloadForTransaction(String orderId, String status, String payId, String shaSign)
      throws IOException {
    return TestTemplateResourceLoader.load(EPDQ_NOTIFICATION_TEMPLATE)
        .replace("{{orderId}}", orderId)
        .replace("{{status}}", status)
        .replace("{{payId}}", payId)
        .replace("{{shaSign}}", shaSign);
  }

}
