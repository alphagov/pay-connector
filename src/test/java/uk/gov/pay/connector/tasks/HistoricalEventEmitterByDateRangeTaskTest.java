package uk.gov.pay.connector.tasks;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.lifecycle.setup.ExecutorServiceBuilder;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnitParamsRunner.class)
public class HistoricalEventEmitterByDateRangeTaskTest {

    private HistoricalEventEmitterByDateRangeTask historicalEventEmitterByDateRangeTask;
    private PrintWriter mockPrintWriter;
    private ExecutorService mockExecutorService;

    @Before
    public void setup() {
        mockPrintWriter = mock(PrintWriter.class);
        mockExecutorService = mock(ExecutorService.class);
        HistoricalEventEmitterWorker mockHistoricalEventEmitterWorker = mock(HistoricalEventEmitterWorker.class);
        Environment mockEnvironment = mock(Environment.class);
        LifecycleEnvironment mockLifecycleEnvironment = mock(LifecycleEnvironment.class);
        ExecutorServiceBuilder mockExecutorServiceBuilder = mock(ExecutorServiceBuilder.class);

        when(mockEnvironment.lifecycle()).thenReturn(mockLifecycleEnvironment);
        when(mockLifecycleEnvironment.executorService(any())).thenReturn(mockExecutorServiceBuilder);
        when(mockExecutorServiceBuilder.maxThreads(anyInt())).thenReturn(mockExecutorServiceBuilder);
        when(mockExecutorServiceBuilder.workQueue(any())).thenReturn(mockExecutorServiceBuilder);
        when(mockExecutorServiceBuilder.build()).thenReturn(mockExecutorService);

        historicalEventEmitterByDateRangeTask = new HistoricalEventEmitterByDateRangeTask(mockHistoricalEventEmitterWorker,
                mockEnvironment);
    }

    @Test
    public void shouldInvokeExecutorForValidDates() {
        String startDate = "2016-01-25T13:23:55Z";
        String endDate = "2016-01-25T13:23:55Z";
        ImmutableMultimap map = ImmutableMultimap.of("start_date", startDate,
                "end_date", endDate);

        historicalEventEmitterByDateRangeTask.execute(map, mockPrintWriter);

        verify(mockExecutorService).execute(any());
    }

    @Test
    @Parameters({
            "\"2016-01-25T13:23:55Z\", \"\"",
            "\"\", \"2016-01-25T13:23:55Z\"",
            "\"invalid-date\", \"invalid-date\"",
            "\"\", \"\""
    })
    public void rejectTaskForInvalidDates(String startDate, String endDate) {
        ImmutableMultimap map = ImmutableMultimap.of("start_date", startDate,
                "end_date", endDate);

        historicalEventEmitterByDateRangeTask.execute(map, mockPrintWriter);

        verify(mockExecutorService, never()).execute(any());
    }

}
