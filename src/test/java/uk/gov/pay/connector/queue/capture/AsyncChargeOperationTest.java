package uk.gov.pay.connector.queue.capture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AsyncChargeOperationTest {

    @Test
    public void shouldAppropriatelySymmetricallySerialiseOperationKey() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        AsyncChargeOperation operation = new AsyncChargeOperation("a-charge-id", AsyncChargeOperationKey.AUTHORISE_USER_NOT_PRESENT);
        String futureStrategyJson = objectMapper.writeValueAsString(operation);
        var serialisedOperation = objectMapper.readValue(futureStrategyJson, AsyncChargeOperation.class);
        assertThat(serialisedOperation.getOperationKey(), is(AsyncChargeOperationKey.AUTHORISE_USER_NOT_PRESENT));
    }
}
