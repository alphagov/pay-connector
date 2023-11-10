package uk.gov.pay.connector.queue.tasks.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.model.ServiceArchivedTaskData;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ServiceArchivedTaskHandlerTest {
    
    private ServiceArchivedTaskHandler serviceArchivedTaskHandler;
    
    @Mock
    private GatewayAccountService mockGatewayAccountService;

    @BeforeEach
    void setup() {
        serviceArchivedTaskHandler = new ServiceArchivedTaskHandler(mockGatewayAccountService);
    }

    @Test
    void shouldCallGatewayAccountServiceAndLogMetrics() {
        String serviceId = "service-to-be-archived";
        serviceArchivedTaskHandler.process(new ServiceArchivedTaskData(serviceId));
        verify(mockGatewayAccountService).disableAccountsAndRedactOrDeleteCredentials(serviceId);
    }
}
