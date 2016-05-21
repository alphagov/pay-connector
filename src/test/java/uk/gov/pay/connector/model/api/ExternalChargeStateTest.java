package uk.gov.pay.connector.model.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ExternalChargeStateTest {
    private static ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldMapAnInternalStatusToExternalStateCorrectly() throws Exception {
        assertThat(CREATED.toExternal().getStatus(), is("created"));
        assertThat(ENTERING_CARD_DETAILS.toExternal().getStatus(), is("started"));
        assertThat(AUTHORISATION_READY.toExternal().getStatus(), is("started"));

        assertThat(AUTHORISATION_SUCCESS.toExternal().getStatus(), is("submitted"));
        assertThat(AUTHORISATION_REJECTED.toExternal().getStatus(), is("failed"));
        assertThat(AUTHORISATION_ERROR.toExternal().getStatus(), is("error"));

        assertThat(CAPTURE_READY.toExternal().getStatus(), is("submitted"));
        assertThat(CAPTURED.toExternal().getStatus(), is("success"));
        assertThat(CAPTURE_SUBMITTED.toExternal().getStatus(), is("success"));
        assertThat(CAPTURE_ERROR.toExternal().getStatus(), is("error"));

        assertThat(EXPIRE_CANCEL_READY.toExternal().getStatus(), is("failed"));
        assertThat(EXPIRE_CANCEL_FAILED.toExternal().getStatus(), is("failed"));
        assertThat(EXPIRED.toExternal().getStatus(), is("failed"));

        assertThat(SYSTEM_CANCEL_READY.toExternal().getStatus(), is("cancelled"));
        assertThat(SYSTEM_CANCEL_ERROR.toExternal().getStatus(), is("cancelled"));
        assertThat(SYSTEM_CANCELLED.toExternal().getStatus(), is("cancelled"));

        assertThat(USER_CANCELLED.toExternal().getStatus(), is("failed"));
        assertThat(USER_CANCEL_ERROR.toExternal().getStatus(), is("failed"));
    }

    @Test
    public void unsuccessfulStatesShouldHaveCodeAndMessage() throws Exception {
        List<String> errorStates = asList("cancelled", "failed", "error");

        for (ExternalChargeState state : ExternalChargeState.values()) {
            if (errorStates.contains(state.getStatus())) {
                assertThat(state.getCode(), notNullValue());
                assertThat(state.getMessage(), notNullValue());
            }
            else {
                assertThat(state.getCode(), nullValue());
                assertThat(state.getMessage(), nullValue());
            }
        }
    }

    @Test
    public void createdStateSerializesCorrectly() throws Exception {
        String json = mapper.writeValueAsString(ExternalChargeState.EXTERNAL_CREATED);
        JsonNode object = mapper.readTree(json);

        assertThat(object.get("status").asText(), is("created"));
        assertThat(object.get("finished").asBoolean(), is(false));
        assertThat(object.has("code"), is(false));
        assertThat(object.has("message"), is(false));
    }

    @Test
    public void successStateSerializesCorrectly() throws Exception {
        String json = mapper.writeValueAsString(ExternalChargeState.EXTERNAL_SUCCESS);
        JsonNode object = mapper.readTree(json);

        assertThat(object.get("status").asText(), is("success"));
        assertThat(object.get("finished").asBoolean(), is(true));
        assertThat(object.has("code"), is(false));
        assertThat(object.has("message"), is(false));
    }

    @Test
    public void cancelStateSerializesCorrectly() throws Exception {
        String json = mapper.writeValueAsString(ExternalChargeState.EXTERNAL_CANCELLED);
        JsonNode object = mapper.readTree(json);

        assertThat(object.get("status").asText(), is("cancelled"));
        assertThat(object.get("finished").asBoolean(), is(true));
        assertThat(object.get("code").asText(), notNullValue());
        assertThat(object.get("message").asText(), notNullValue());
    }
}
