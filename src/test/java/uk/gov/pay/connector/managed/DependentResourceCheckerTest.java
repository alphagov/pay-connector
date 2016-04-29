package uk.gov.pay.connector.managed;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.DependentResource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DependentResourceCheckerTest {

    @InjectMocks
    DependentResourceChecker dependentResourceChecker;

    @Mock
    ConnectorConfiguration mockConnectorConfiguration;

    @Mock
    DependentResource mockDependentResource;

    private Appender<ILoggingEvent> mockAppender;

    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Before
    public void setup() {
        Logger root = (Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        mockAppender = mockAppender();
        root.addAppender(mockAppender);
    }

    @Test
    public void start_ShouldWaitAndLogUntilDatabaseIsAccessible() throws Exception {

        Connection mockConnection = mock(Connection.class);
        when(mockDependentResource.getDatabaseConnection())
                .thenThrow(new SQLException("not there yet"))
                .thenReturn(mockConnection);

        dependentResourceChecker.start();

        verify(mockDependentResource, times(2)).getDatabaseConnection();
        verify(mockDependentResource).sleep(5000L);

        verify(mockAppender, times(3)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> allValues = loggingEventArgumentCaptor.getAllValues();

        assertThat(allValues.get(0).getFormattedMessage(), is("Checking for database availability"));
        assertThat(allValues.get(1).getFormattedMessage(), is("Waiting for 5 seconds till the database is available ..."));
        assertThat(allValues.get(2).getFormattedMessage(), is("Database available"));
    }

    @SuppressWarnings("unchecked")
    private <T> Appender<T> mockAppender() {
        return mock(Appender.class);
    }

}
