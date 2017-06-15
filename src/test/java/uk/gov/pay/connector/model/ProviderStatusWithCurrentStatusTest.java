package uk.gov.pay.connector.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;

public class ProviderStatusWithCurrentStatusTest {

    @Test
    public void shouldBeEqualIfSameProviderStatusAndCurrentStatus() {
        ProviderStatusWithCurrentStatus object1 = ProviderStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        ProviderStatusWithCurrentStatus object2 = ProviderStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);

        assertEquals(object1, object2);
        assertEquals(object2, object1);
    }

    @Test
    public void shouldNotBeEqualIfSameProviderStatusButDifferentCurrentStatus() {
        ProviderStatusWithCurrentStatus object1 = ProviderStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        ProviderStatusWithCurrentStatus object2 = ProviderStatusWithCurrentStatus.of("Status 1", SYSTEM_CANCEL_SUBMITTED);

        assertNotEquals(object1, object2);
        assertNotEquals(object2, object1);
    }

    @Test
    public void shouldNotBeEqualIfDifferentProviderStatusButSameCurrentStatus() {
        ProviderStatusWithCurrentStatus object1 = ProviderStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        ProviderStatusWithCurrentStatus object2 = ProviderStatusWithCurrentStatus.of("Status 2", USER_CANCEL_SUBMITTED);

        assertNotEquals(object1, object2);
        assertNotEquals(object2, object1);
    }

    @Test
    public void shouldNotBeEqualIfDifferentProviderStatusAndDifferentCurrentStatus() {
        ProviderStatusWithCurrentStatus object1 = ProviderStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        ProviderStatusWithCurrentStatus object2 = ProviderStatusWithCurrentStatus.of("Status 2", SYSTEM_CANCEL_SUBMITTED);

        assertNotEquals(object1, object2);
        assertNotEquals(object2, object1);
    }

    @Test
    public void shouldBeEqualToProviderStatusOnlyWithSameProviderStatus() {
        ProviderStatusWithCurrentStatus object1 = ProviderStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        ProviderStatusOnly object2 = ProviderStatusOnly.of("Status 1");

        assertEquals(object1, object2);
    }

    @Test
    public void shouldNotBeEqualToProviderStatusOnlyWithDifferentProviderStatus() {
        ProviderStatusWithCurrentStatus object1 = ProviderStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED );
        ProviderStatusOnly object2 = ProviderStatusOnly.of("Status 2");

        assertNotEquals(object1, object2);
    }

    @Test
    public void shouldHaveSameHashCodeIfSameProviderStatusAndCurrentStatus() {
        ProviderStatusWithCurrentStatus object1 = ProviderStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        ProviderStatusWithCurrentStatus object2 = ProviderStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);

        assertEquals(object1.hashCode(), object2.hashCode());
    }

    @Test
    public void shouldHaveSameHashCodeAsProviderStatusOnlyWithSameProviderStatus() {
        ProviderStatusWithCurrentStatus object1 = ProviderStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        ProviderStatusOnly object2 = ProviderStatusOnly.of("Status 1");

        assertEquals(object1.hashCode(), object2.hashCode());
    }

}
