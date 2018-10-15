package uk.gov.pay.connector.model;

import org.junit.Test;
import uk.gov.pay.connector.gateway.model.status.GatewayStatusOnly;
import uk.gov.pay.connector.gateway.model.status.StatusMapFromStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class GatewayStatusOnlyTest {

    @Test
    public void shouldBeEqualIfSameGatewayStatus() {
        GatewayStatusOnly object1 = GatewayStatusOnly.of("Status 1");
        GatewayStatusOnly object2 = GatewayStatusOnly.of("Status 1");

        assertEquals(object1, object2);
        assertEquals(object2, object1);
    }

    @Test
    public void shouldNotBeEqualIfDifferentGatewayStatuses() {
        GatewayStatusOnly object1 = GatewayStatusOnly.of("Status 1");
        GatewayStatusOnly object2 = GatewayStatusOnly.of("Status 2");

        assertNotEquals(object1, object2);
        assertNotEquals(object2, object1);
    }

    @Test
    public void shouldBeEqualToAnotherStatusMapFromImplementationWithSameGatewayStatus() {
        GatewayStatusOnly object1 = GatewayStatusOnly.of("Status 1");
        AnotherStatusMapFromStatusImplementation object2 = new AnotherStatusMapFromStatusImplementation("Status 1", "Something");

        assertEquals(object1, object2);
    }

    @Test
    public void shouldNotBeEqualToAnotherStatusMapFromImplementationWithDifferentGatewayStatus() {
        GatewayStatusOnly object1 = GatewayStatusOnly.of("Status 1");
        AnotherStatusMapFromStatusImplementation object2 = new AnotherStatusMapFromStatusImplementation("Status 2", "Something");

        assertNotEquals(object1, object2);
    }

    @Test
    public void shouldHaveSameHashCodeIfSameGatewayStatus() {
        GatewayStatusOnly object1 = GatewayStatusOnly.of("Status 1");
        GatewayStatusOnly object2 = GatewayStatusOnly.of("Status 1");

        assertEquals(object1.hashCode(), object2.hashCode());
    }

    private static class AnotherStatusMapFromStatusImplementation implements StatusMapFromStatus<String> {

        private final String gatewayStatus;
        private final String anotherField;

        public AnotherStatusMapFromStatusImplementation(String gatewayStatus, String anotherField) {
            this.gatewayStatus = gatewayStatus;
            this.anotherField = anotherField;
        }

        @Override
        public String getGatewayStatus() {
            return gatewayStatus;
        }
    }

}
