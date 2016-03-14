package uk.gov.pay.connector.model.api;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.EnumSet;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ExternalChargeStatusTest {

    @Test
    public void shouldMapAllInternalStatuesCorrectlyToExternalStatuses() throws Exception {
        for (ChargeStatus chargeStatus : EnumSet.allOf(ChargeStatus.class)) {
            assertThat(ExternalChargeStatus.mapFromStatus(chargeStatus), is(instanceOf(ExternalChargeStatus.class)));
        }
    }

    @Test
    public void shouldMapAnInternalStatusToAnExternalStatusCorrectly() throws Exception {
            assertThat(ExternalChargeStatus.mapFromStatus(READY_FOR_CAPTURE), is(EXT_IN_PROGRESS));
            assertThat(ExternalChargeStatus.mapFromStatus(CREATED), is(EXT_CREATED));
        assertThat(ExternalChargeStatus.mapFromStatus(AUTHORISATION_ERROR), is(EXT_FAILED));
            assertThat(ExternalChargeStatus.mapFromStatus(CAPTURE_SUBMITTED), is(EXT_SUCCEEDED));
            assertThat(ExternalChargeStatus.mapFromStatus(SYSTEM_CANCELLED), is(EXT_SYSTEM_CANCELLED));
    }
}