package uk.gov.pay.connector.util;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.event.LoggingEvent;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationStartupApplicationStartupDependentResourceCheckerTest {

    @InjectMocks
    ApplicationStartupDependentResourceChecker applicationStartupDependentResourceChecker;

    @Mock
    ApplicationStartupDependentResource mockApplicationStartupDependentResource;

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(ApplicationStartupDependentResourceChecker.class);

    @Test
    void start_ShouldWaitAndLogUntilDatabaseIsAccessible() throws Exception {
        Connection mockConnection = mock(Connection.class);
        when(mockApplicationStartupDependentResource.getDatabaseConnection())
                .thenThrow(new SQLException("not there yet"))
                .thenReturn(mockConnection);

        applicationStartupDependentResourceChecker.checkAndWaitForResources();

        verify(mockApplicationStartupDependentResource, times(2)).getDatabaseConnection();
        verify(mockApplicationStartupDependentResource).sleep(5000L);
        assertThat(logs.getEvents())
                .extracting(LoggingEvent::getMessage)
                .containsExactly(
                        "Checking for database availability >>>",
                        "Waiting for 5 seconds till the database is available ...",
                        "Database available.");
    }

    @Test
    void start_ShouldProgressivelyIncrementSleepingTimeBetweenChecksForDBAccessibility() throws Exception {
        Connection mockConnection = mock(Connection.class);
        when(mockApplicationStartupDependentResource.getDatabaseConnection())
                .thenThrow(new SQLException("not there"))
                .thenThrow(new SQLException("not there yet"))
                .thenThrow(new SQLException("still not there"))
                .thenReturn(mockConnection);

        applicationStartupDependentResourceChecker.checkAndWaitForResources();

        verify(mockApplicationStartupDependentResource, times(4)).getDatabaseConnection();
        verify(mockApplicationStartupDependentResource).sleep(5000L);
        verify(mockApplicationStartupDependentResource).sleep(10000L);
        verify(mockApplicationStartupDependentResource).sleep(15000L);
        assertThat(logs.getEvents())
                .extracting(LoggingEvent::getMessage)
                .containsExactly(
                        "Checking for database availability >>>",
                        "Waiting for 5 seconds till the database is available ...",
                        "Waiting for 10 seconds till the database is available ...",
                        "Waiting for 15 seconds till the database is available ...",
                        "Database available.");
    }

    @Test
    void start_ShouldCloseAnyAcquiredConnectionWhenTheCheckIsDone() throws Exception {
        Connection mockConnection = mock(Connection.class);
        when(mockApplicationStartupDependentResource.getDatabaseConnection()).thenReturn(mockConnection);

        applicationStartupDependentResourceChecker.checkAndWaitForResources();

        verify(mockConnection).close();
    }
}
