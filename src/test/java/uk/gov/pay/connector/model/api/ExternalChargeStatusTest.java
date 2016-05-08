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
    public void shouldMapAnInternalStatusToAnExternalStatusCorrectly() throws Exception {
        assertThat(CAPTURE_READY.toExternal(), is(EXT_IN_PROGRESS));
        assertThat(CREATED.toExternal(), is(EXT_CREATED));
        assertThat(AUTHORISATION_ERROR.toExternal(), is(EXT_FAILED));
        assertThat(CAPTURE_SUBMITTED.toExternal(), is(EXT_SUCCEEDED));
        assertThat(SYSTEM_CANCELLED.toExternal(), is(EXT_SYSTEM_CANCELLED));
    }
}