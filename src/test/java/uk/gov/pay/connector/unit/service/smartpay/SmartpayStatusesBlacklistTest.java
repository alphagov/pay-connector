package uk.gov.pay.connector.unit.service.smartpay;

import org.junit.Test;
import uk.gov.pay.connector.service.ChargeStatusBlacklist;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class SmartpayStatusesBlacklistTest {

    @Test
    public void shouldBlacklistedAuthorisedStatuses() throws Exception {
        assertTrue(ChargeStatusBlacklist.has(AUTHORISATION_SUBMITTED));
        assertTrue(ChargeStatusBlacklist.has(AUTHORISATION_SUCCESS));
        assertTrue(ChargeStatusBlacklist.has(AUTHORISATION_REJECTED));
    }

    @Test
    public void shouldNotBlacklistNonAuthorisedStatuses() throws Exception {
        assertFalse(ChargeStatusBlacklist.has(CAPTURED));
        assertFalse(ChargeStatusBlacklist.has(SYSTEM_CANCELLED));
    }
}