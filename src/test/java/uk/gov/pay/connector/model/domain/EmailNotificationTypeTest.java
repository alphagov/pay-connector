package uk.gov.pay.connector.model.domain;

import org.junit.Test;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class EmailNotificationTypeTest {

    @Test
    public void shouldConvertPaymentConfirmed() {
        assertThat(EmailNotificationType.fromString("payment_confirmed"), is(EmailNotificationType.PAYMENT_CONFIRMED));
        assertThat(EmailNotificationType.fromString("PAYMENT_CONFIRMED"), is(EmailNotificationType.PAYMENT_CONFIRMED));
    }

    @Test
    public void shouldConvertRefundIssued() {
        assertThat(EmailNotificationType.fromString("refund_issued"), is(EmailNotificationType.REFUND_ISSUED));
        assertThat(EmailNotificationType.fromString("REFUND_ISSUED"), is(EmailNotificationType.REFUND_ISSUED));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfConvertingUnknownEnum() {
        EmailNotificationType.fromString("nope");
    }

}
