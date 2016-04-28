package uk.gov.pay.connector.managed;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.DependentResource;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.managed.DependentResourceChecker.INITIALI_SECONDS_TO_WAIT;

@RunWith(MockitoJUnitRunner.class)
public class DependentResourceCheckerTest {

    @InjectMocks
    DependentResourceChecker dependentResourceChecker;

    @Mock
    ConnectorConfiguration mockConnectorConfiguration;

    @Mock
    DependentResource mockDependentResource;

    @Mock
    Logger mockLogger;

    @Test
    public void start_ShouldWaitAndLogUntilDatabaseIsAccessible() throws Exception {
        Connection mockConnection = mock(Connection.class);
        when(mockDependentResource.getDatabaseConnection())
                .thenThrow(new SQLException("not there yet"))
                .thenReturn(mockConnection);

        dependentResourceChecker.setLogger(mockLogger);
        dependentResourceChecker.start();

        verify(mockLogger).info("Checking for database availability");
        verify(mockDependentResource, times(2)).getDatabaseConnection();
        verify(mockDependentResource).sleep(5000L);
        verify(mockLogger).info("Waiting for 5 seconds till the database is available ...");
        verify(mockLogger).info("Database available");
    }

}
