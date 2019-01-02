package uk.gov.pay.connector.it.util;

import com.google.common.collect.Lists;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.gateway.epdq.EpdqSha512SignatureGenerator;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class NotificationUtils {

    public static String epdqNotificationPayload(String transactionId, String status, String shaOutPassphrase) {
        List<NameValuePair> payloadParameters = buildEpdqPayload(transactionId, status);
        return epdqNotificationPayload(payloadParameters, shaOutPassphrase);
    }
    
    public static String epdqNotificationPayload(String transactionId, String payIdSub, String status, String shaOutPassphrase) {
        List<NameValuePair> payloadParameters = buildEpdqPayload(transactionId, status);
        payloadParameters.add(new BasicNameValuePair("PAYIDSUB", payIdSub));
        return epdqNotificationPayload(payloadParameters, shaOutPassphrase);
    }

    private static List<NameValuePair> buildEpdqPayload(String transactionId, String status) {
        return Lists.newArrayList(
                new BasicNameValuePair("orderID", "order-id"),
                new BasicNameValuePair("STATUS", status),
                new BasicNameValuePair("PAYID", transactionId));
    }

    private static String epdqNotificationPayload(List<NameValuePair> payloadParameters, String shaOutPassphrase) {
        String signature = new EpdqSha512SignatureGenerator().sign(payloadParameters, shaOutPassphrase);
        payloadParameters.add(new BasicNameValuePair("SHASIGN", signature));
        return URLEncodedUtils.format(payloadParameters, StandardCharsets.UTF_8.toString());
    }
}
