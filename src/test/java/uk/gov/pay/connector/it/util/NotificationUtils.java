package uk.gov.pay.connector.it.util;

import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_NOTIFICATION;

public class NotificationUtils {

    public static String worldpayRefundNotificationPayload(String transactionId, String status, String reference) {
        String payload = TestTemplateResourceLoader.load(WORLDPAY_NOTIFICATION)
                .replace("{{transactionId}}", transactionId)
                .replace("{{status}}", status)
                .replace("{{bookingDateDay}}", "10")
                .replace("{{bookingDateMonth}}", "01")
                .replace("{{bookingDateYear}}", "2017");
        return payload.replace("{{refund-ref}}", reference);
    }
}
