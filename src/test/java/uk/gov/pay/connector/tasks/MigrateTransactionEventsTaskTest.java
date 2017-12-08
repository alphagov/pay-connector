package uk.gov.pay.connector.tasks;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class MigrateTransactionEventsTaskTest {

    @Mock
    private PaymentRequestWorker mockedPaymentRequestWorker;

    @Test
    public void shouldCreateTaskWithExpectedName() {
        MigrateTransactionEventsTask task = new MigrateTransactionEventsTask(mockedPaymentRequestWorker);
        assertThat(task, is(notNullValue()));
        String expectedTaskName = "migrate-charge-events-to-charge-transaction-events";
        assertThat(task.getName(), is(expectedTaskName));
    }
}
