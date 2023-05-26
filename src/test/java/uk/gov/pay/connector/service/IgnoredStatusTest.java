package uk.gov.pay.connector.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.status.IgnoredStatus;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

class IgnoredStatusTest {

    private final IgnoredStatus ignoredStatus = new IgnoredStatus();

    @Test
    void shouldReturnCorrectType() {
        assertThat(ignoredStatus.getType(), is(InterpretedStatus.Type.IGNORED));
    }

    @Test
    void shouldThrowExceptionForGetChargeStatus() {

        Assertions.assertThrows(IllegalStateException.class, () -> {
            ignoredStatus.getChargeStatus();
        });
    }

    @Test
    void shouldThrowExceptionForGetRefundStatus() {

        Assertions.assertThrows(IllegalStateException.class, () -> {
            ignoredStatus.getRefundStatus();
        });
    }

}
