package uk.gov.pay.connector.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ProviderStatusOnlyTest {

    @Test
    public void shouldBeEqualIfSameProviderStatus() {
        ProviderStatusOnly object1 = ProviderStatusOnly.of("Status 1");
        ProviderStatusOnly object2 = ProviderStatusOnly.of("Status 1");

        assertEquals(object1, object2);
        assertEquals(object2, object1);
    }

    @Test
    public void shouldNotBeEqualIfDifferentProviderStatuses() {
        ProviderStatusOnly object1 = ProviderStatusOnly.of("Status 1");
        ProviderStatusOnly object2 = ProviderStatusOnly.of("Status 2");

        assertNotEquals(object1, object2);
        assertNotEquals(object2, object1);
    }

    @Test
    public void shouldBeEqualToAnotherStatusMapFromImplementationWithSameProviderStatus() {
        ProviderStatusOnly object1 = ProviderStatusOnly.of("Status 1");
        AnotherStatusMapFromStatusImplementation object2 = new AnotherStatusMapFromStatusImplementation("Status 1", "Something");

        assertEquals(object1, object2);
    }

    @Test
    public void shouldNotBeEqualToAnotherStatusMapFromImplementationWithDifferentProviderStatus() {
        ProviderStatusOnly object1 = ProviderStatusOnly.of("Status 1");
        AnotherStatusMapFromStatusImplementation object2 = new AnotherStatusMapFromStatusImplementation("Status 2", "Something");

        assertNotEquals(object1, object2);
    }

    @Test
    public void shouldHaveSameHashCodeIfSameProviderStatus() {
        ProviderStatusOnly object1 = ProviderStatusOnly.of("Status 1");
        ProviderStatusOnly object2 = ProviderStatusOnly.of("Status 1");

        assertEquals(object1.hashCode(), object2.hashCode());
    }

    private static class AnotherStatusMapFromStatusImplementation implements StatusMapFromStatus<String> {

        private final String providerStatus;
        private final String anotherField;

        public AnotherStatusMapFromStatusImplementation(String providerStatus, String anotherField) {
            this.providerStatus = providerStatus;
            this.anotherField = anotherField;
        }

        @Override
        public String getProviderStatus() {
            return providerStatus;
        }
    }

}
