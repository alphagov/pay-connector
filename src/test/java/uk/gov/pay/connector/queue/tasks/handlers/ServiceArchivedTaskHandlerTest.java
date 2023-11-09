package uk.gov.pay.connector.queue.tasks.handlers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.model.ServiceArchivedTaskData;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ServiceArchivedTaskHandlerTest {
    
    private ServiceArchivedTaskHandler serviceArchivedTaskHandler;
    
    @Mock
    private GatewayAccountService mockGatewayAccountService;

    private final CollectorRegistry collectorRegistry = CollectorRegistry.defaultRegistry;
    
    @BeforeEach
    void setup() {
        serviceArchivedTaskHandler = new ServiceArchivedTaskHandler(mockGatewayAccountService);
    }

    @Test
    void shouldCallGatewayAccountServiceAndLogMetrics() {
        String serviceId = "service-to-be-archived";
        Double initialDuration = Optional.ofNullable(collectorRegistry.getSampleValue("disable_gateway_accounts_and_credentials_job_duration_seconds_sum")).orElse(0.0);
        
        serviceArchivedTaskHandler.process(new ServiceArchivedTaskData(serviceId));
        
        verify(mockGatewayAccountService).disableAccountsAndRedactOrDeleteCredentials(serviceId);
        Double duration = collectorRegistry.getSampleValue("disable_gateway_accounts_and_credentials_job_duration_seconds_sum");
        assertThat(duration, greaterThan(initialDuration));
    }
}
