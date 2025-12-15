package uk.gov.pay.connector.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
 class ApplicationStartupApplicationStartupDependentResourceCheckerTest {

    @InjectMocks
    ApplicationStartupDependentResourceChecker applicationStartupDependentResourceChecker;

    @Mock
    ConnectorConfiguration mockConnectorConfiguration;

    @Mock
    ApplicationStartupDependentResource mockApplicationStartupDependentResource;

    private Appender<ILoggingEvent> mockAppender;

    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @BeforeEach
     void setup() {
        Logger root = (Logger) LoggerFactory.getLogger(ApplicationStartupDependentResourceChecker.class);
        mockAppender = mockAppender();
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
     void start_ShouldWaitAndLogUntilDatabaseIsAccessible() throws Exception {

        Connection mockConnection = mock(Connection.class);
        when(mockApplicationStartupDependentResource.getDatabaseConnection())
                .thenThrow(new SQLException("not there yet"))
                .thenReturn(mockConnection);

        applicationStartupDependentResourceChecker.checkAndWaitForResources();

        verify(mockApplicationStartupDependentResource, times(2)).getDatabaseConnection();
        verify(mockApplicationStartupDependentResource).sleep(5000L);

        verify(mockAppender, times(3)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> allValues = loggingEventArgumentCaptor.getAllValues();

        assertThat(allValues.getFirst().getFormattedMessage(), is("Checking for database availability >>>"));
        assertThat(allValues.get(1).getFormattedMessage(), is("Waiting for 5 seconds till the database is available ..."));
        assertThat(allValues.get(2).getFormattedMessage(), is("Database available."));
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
        verify(mockAppender, times(5)).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        assertThat(logStatement.getFirst().getFormattedMessage(), is("Checking for database availability >>>"));
        assertThat(logStatement.get(1).getFormattedMessage(), is("Waiting for 5 seconds till the database is available ..."));
        assertThat(logStatement.get(2).getFormattedMessage(), is("Waiting for 10 seconds till the database is available ..."));
        assertThat(logStatement.get(3).getFormattedMessage(), is("Waiting for 15 seconds till the database is available ..."));
        assertThat(logStatement.get(4).getFormattedMessage(), is("Database available."));
    }

    @Test
     void start_ShouldCloseAnyAcquiredConnectionWhenTheCheckIsDone() throws Exception {
        Connection mockConnection = mock(Connection.class);
        when(mockApplicationStartupDependentResource.getDatabaseConnection()).thenReturn(mockConnection);

        applicationStartupDependentResourceChecker.checkAndWaitForResources();

        verify(mockConnection).close();
    }

    @SuppressWarnings("unchecked")
    private <T> Appender<T> mockAppender() {
        return mock(Appender.class);
    }
}
