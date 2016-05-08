package uk.gov.pay.connector.model.api;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.api.LegacyChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class LegacyChargeStatusTest {
    @Test
    public void shouldMapAnInternalStatusToALegacyStatusCorrectly() throws Exception {
        assertThat(CAPTURE_READY.toLegacy(), is(LEGACY_EXT_IN_PROGRESS));
        assertThat(CREATED.toLegacy(), is(LEGACY_EXT_CREATED));
        assertThat(AUTHORISATION_ERROR.toLegacy(), is(LEGACY_EXT_FAILED));
        assertThat(CAPTURE_SUBMITTED.toLegacy(), is(LEGACY_EXT_SUCCEEDED));
        assertThat(SYSTEM_CANCELLED.toLegacy(), is(LEGACY_EXT_SYSTEM_CANCELLED));
    }
}