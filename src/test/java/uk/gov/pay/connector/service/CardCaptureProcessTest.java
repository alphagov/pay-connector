package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import java.time.Duration;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureProcessTest {

    CardCaptureProcess cardCaptureProcess;

    @Mock
    ChargeDao mockChargeDao;

    @Mock
    CardCaptureService mockCardCaptureService;

    @Mock
    Environment mockEnvironment;

    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;

    @Before
    public void setup() {
        MetricRegistry mockMetricRegistry = mock(MetricRegistry.class);
        Histogram mockHistogram = mock(Histogram.class);
        Counter mockCounter = mock(Counter.class);
        CaptureProcessConfig mockCaptureConfiguration = mock(CaptureProcessConfig.class);

        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockCaptureConfiguration.getBatchSize()).thenReturn(10);
        when(mockCaptureConfiguration.getRetryFailuresEveryAsJavaDuration()).thenReturn(Duration.ofMinutes(60));
        when(mockConnectorConfiguration.getCaptureProcessConfig()).thenReturn(mockCaptureConfiguration);
        cardCaptureProcess = new CardCaptureProcess(mockEnvironment, mockChargeDao, mockCardCaptureService, mockConnectorConfiguration);
    }

    @Test
    public void shouldRetrieveASpecifiedNumberOfChargesApprovedForCapture() {
        cardCaptureProcess.runCapture();

        verify(mockChargeDao).findChargesForCapture(10, Duration.ofMinutes(60));
    }

    @Test
    public void shouldRecordTheQueueSizeOnEveryRun() {
        when(mockChargeDao.countChargesForCapture()).thenReturn(15);

        cardCaptureProcess.runCapture();

        assertEquals(cardCaptureProcess.getQueueSize(), 15);
    }

    @Test
    public void shouldRunCaptureForAllCharges() {
        ChargeEntity mockCharge1 = mock(ChargeEntity.class);
        ChargeEntity mockCharge2 = mock(ChargeEntity.class);

        when(mockChargeDao.findChargesForCapture(10, Duration.ofMinutes(60))).thenReturn(asList(mockCharge1, mockCharge2));
        when(mockCharge1.getExternalId()).thenReturn("my-charge-1");
        when(mockCharge2.getExternalId()).thenReturn("my-charge-2");

        cardCaptureProcess.runCapture();

        verify(mockCardCaptureService).doCapture("my-charge-1");
        verify(mockCardCaptureService).doCapture("my-charge-2");

    }
}
