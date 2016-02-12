package uk.gov.pay.connector.unit.service.worldpay;

import org.junit.Test;
import uk.gov.pay.connector.service.worldpay.WorldpayStatusesBlacklist;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class WorldpayStatusesBlacklistTest {

    @Test
    public void shouldBlacklistedAuthorisedStatuses() throws Exception {
        assertTrue(WorldpayStatusesBlacklist.has(AUTHORISATION_SUBMITTED));
        assertTrue(WorldpayStatusesBlacklist.has(AUTHORISATION_SUCCESS));
        assertTrue(WorldpayStatusesBlacklist.has(AUTHORISATION_REJECTED));
    }

    @Test
    public void shouldNotBlacklistNonAuthorisedStatuses() throws Exception {
        assertFalse(WorldpayStatusesBlacklist.has(CAPTURED));
        assertFalse(WorldpayStatusesBlacklist.has(SYSTEM_CANCELLED));
    }
}