package uk.gov.pay.connector.model;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class EvaluatedRefundStatusNotificationTest {

    @Test
    public void shouldCreateEvaluatedRefundStatusNotification() {
        String transactionId = "transaction-id";
        String reference = "reference";
        String status = "status";
        RefundStatus refundStatus = RefundStatus.REFUNDED;
        ZonedDateTime now = ZonedDateTime.now();
        List<NameValuePair> payload = singletonList(new BasicNameValuePair("my", "payload"));

        BaseNotification<String> baseNotification = new BaseNotification<>(transactionId, reference, status, now, payload);

        EvaluatedRefundStatusNotification<String> evaluatedNotification = new EvaluatedRefundStatusNotification(baseNotification, refundStatus);

        assertThat(evaluatedNotification.getTransactionId(), is(transactionId));
        assertThat(evaluatedNotification.getStatus(), is(status));
        assertThat(evaluatedNotification.getRefundStatus(), is(refundStatus));
        assertThat(evaluatedNotification.getGatewayEventDate(), is(now));
        assertThat(evaluatedNotification.getPayload(), is(Optional.of(payload)));
        assertThat(evaluatedNotification.isOfChargeType(), is(false));
        assertThat(evaluatedNotification.isOfRefundType(), is(true));
        assertThat(evaluatedNotification.getRefundStatus(), is(refundStatus));
    }

}
