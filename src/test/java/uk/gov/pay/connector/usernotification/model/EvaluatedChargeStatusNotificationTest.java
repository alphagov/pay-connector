package uk.gov.pay.connector.usernotification.model;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class EvaluatedChargeStatusNotificationTest {

    @Test
    public void shouldCreateEvaluatedChargeStatusNotification() {
        String transactionId = "transaction-id";
        String reference = "reference";
        String status = "status";
        ChargeStatus chargeStatus = ChargeStatus.CAPTURED;
        ZonedDateTime now = ZonedDateTime.now();
        List<NameValuePair> payload = singletonList(new BasicNameValuePair("my", "payload"));

        Notification<String> baseNotification = new BaseNotification<>(transactionId, reference, status, now, payload);

        EvaluatedChargeStatusNotification<String> evaluatedNotification = new EvaluatedChargeStatusNotification(baseNotification, chargeStatus);

        assertThat(evaluatedNotification.getTransactionId(), is(transactionId));
        assertThat(evaluatedNotification.getReference(), is(reference));
        assertThat(evaluatedNotification.getStatus(), is(status));
        assertThat(evaluatedNotification.getGatewayEventDate(), is(now));
        assertThat(evaluatedNotification.getPayload(), is(Optional.of(payload)));
        assertThat(evaluatedNotification.isOfChargeType(), is(true));
        assertThat(evaluatedNotification.isOfRefundType(), is(false));
        assertThat(evaluatedNotification.getChargeStatus(), is(chargeStatus));
    }

}
