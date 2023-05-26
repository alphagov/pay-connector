package uk.gov.pay.connector.model.domain;


import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailNotificationTypeTest {

    @Test
    void shouldConvertPaymentConfirmed() {
        assertThat(EmailNotificationType.fromString("payment_confirmed"), is(EmailNotificationType.PAYMENT_CONFIRMED));
        assertThat(EmailNotificationType.fromString("PAYMENT_CONFIRMED"), is(EmailNotificationType.PAYMENT_CONFIRMED));
    }

    @Test
    void shouldConvertRefundIssued() {
        assertThat(EmailNotificationType.fromString("refund_issued"), is(EmailNotificationType.REFUND_ISSUED));
        assertThat(EmailNotificationType.fromString("REFUND_ISSUED"), is(EmailNotificationType.REFUND_ISSUED));
    }
    
    @Test
    void shouldThrowIfConvertingUnknownEnum() {
        assertThrows(IllegalArgumentException.class, () ->
                EmailNotificationType.fromString("nope")
        );
    }
}
