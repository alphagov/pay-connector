package uk.gov.pay.connector.gateway.stripe;

import com.stripe.net.Webhook;

public abstract class StripeNotificationUtilTest {
    public static String generateSigHeader(String webhookSigningSecret, String message) {

        long currentTimestamp = System.currentTimeMillis() / 1000L;
        final String payloadToSign = String.format("%d.%s", currentTimestamp, message);
        String signature = null;

        try {
            signature = Webhook.Util.computeHmacSha256(webhookSigningSecret, payloadToSign);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return String.format("t=%d,v1=%s", currentTimestamp, signature);
    }
}
