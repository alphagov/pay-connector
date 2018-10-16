package uk.gov.pay.connector.model;

import org.junit.Test;
import uk.gov.pay.connector.gateway.model.status.GatewayStatusOnly;
import uk.gov.pay.connector.gateway.model.status.GatewayStatusWithCurrentStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;

public class GatewayStatusWithCurrentStatusTest {

    @Test
    public void shouldBeEqualIfSameGatewayStatusAndCurrentStatus() {
        GatewayStatusWithCurrentStatus object1 = GatewayStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        GatewayStatusWithCurrentStatus object2 = GatewayStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);

        assertEquals(object1, object2);
        assertEquals(object2, object1);
    }

    @Test
    public void shouldNotBeEqualIfSameGatewayStatusButDifferentCurrentStatus() {
        GatewayStatusWithCurrentStatus object1 = GatewayStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        GatewayStatusWithCurrentStatus object2 = GatewayStatusWithCurrentStatus.of("Status 1", SYSTEM_CANCEL_SUBMITTED);

        assertNotEquals(object1, object2);
        assertNotEquals(object2, object1);
    }

    @Test
    public void shouldNotBeEqualIfDifferentGatewayStatusButSameCurrentStatus() {
        GatewayStatusWithCurrentStatus object1 = GatewayStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        GatewayStatusWithCurrentStatus object2 = GatewayStatusWithCurrentStatus.of("Status 2", USER_CANCEL_SUBMITTED);

        assertNotEquals(object1, object2);
        assertNotEquals(object2, object1);
    }

    @Test
    public void shouldNotBeEqualIfDifferentGatewayStatusAndDifferentCurrentStatus() {
        GatewayStatusWithCurrentStatus object1 = GatewayStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        GatewayStatusWithCurrentStatus object2 = GatewayStatusWithCurrentStatus.of("Status 2", SYSTEM_CANCEL_SUBMITTED);

        assertNotEquals(object1, object2);
        assertNotEquals(object2, object1);
    }

    @Test
    public void shouldBeEqualToGatewayStatusOnlyWithSameGatewayStatus() {
        GatewayStatusWithCurrentStatus object1 = GatewayStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        GatewayStatusOnly object2 = GatewayStatusOnly.of("Status 1");

        assertEquals(object1, object2);
    }

    @Test
    public void shouldNotBeEqualToGatewayStatusOnlyWithDifferentGatewayStatus() {
        GatewayStatusWithCurrentStatus object1 = GatewayStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED );
        GatewayStatusOnly object2 = GatewayStatusOnly.of("Status 2");

        assertNotEquals(object1, object2);
    }

    @Test
    public void shouldHaveSameHashCodeIfSameGatewayStatusAndCurrentStatus() {
        GatewayStatusWithCurrentStatus object1 = GatewayStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        GatewayStatusWithCurrentStatus object2 = GatewayStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);

        assertEquals(object1.hashCode(), object2.hashCode());
    }

    @Test
    public void shouldHaveSameHashCodeAsGatewayStatusOnlyWithSameGatewayStatus() {
        GatewayStatusWithCurrentStatus object1 = GatewayStatusWithCurrentStatus.of("Status 1", USER_CANCEL_SUBMITTED);
        GatewayStatusOnly object2 = GatewayStatusOnly.of("Status 1");

        assertEquals(object1.hashCode(), object2.hashCode());
    }

}
