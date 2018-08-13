package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import java.time.Duration;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureProcessTest {

    public static final int MAXIMUM_RETRIES = 10;
    CardCaptureProcess cardCaptureProcess;

    @Mock
    ChargeDao mockChargeDao;

    @Mock
    CardCaptureService mockCardCaptureService;

    @Mock
    Environment mockEnvironment;

    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;

    @Mock
    GatewayResponse mockGatewayResponse;

    @Before
    public void setup() {
        MetricRegistry mockMetricRegistry = mock(MetricRegistry.class);
        Histogram mockHistogram = mock(Histogram.class);
        CaptureProcessConfig mockCaptureConfiguration = mock(CaptureProcessConfig.class);

        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        Counter mockCounter = mock(Counter.class);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockCaptureConfiguration.getBatchSize()).thenReturn(10);
        when(mockCaptureConfiguration.getRetryFailuresEveryAsJavaDuration()).thenReturn(Duration.ofMinutes(60));
        when(mockCaptureConfiguration.getMaximumRetries()).thenReturn(MAXIMUM_RETRIES);
        when(mockConnectorConfiguration.getCaptureProcessConfig()).thenReturn(mockCaptureConfiguration);
        cardCaptureProcess = new CardCaptureProcess(mockEnvironment, mockChargeDao, mockCardCaptureService, mockConnectorConfiguration);
        when(mockGatewayResponse.isSuccessful()).thenReturn(true);
        when(mockCardCaptureService.doCapture(anyString())).thenReturn(mockGatewayResponse);
    }

    @Test
    public void shouldRetrieveASpecifiedNumberOfChargesApprovedForCapture() {
        cardCaptureProcess.runCapture();

        verify(mockChargeDao).findChargesForCapture(10, Duration.ofMinutes(60));
    }

    @Test
    public void shouldRecordTheQueueSizeOnEveryRun() {
        when(mockChargeDao.countChargesForCapture(Matchers.any(Duration.class))).thenReturn(15);

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

    @Test
    public void shouldNotCaptureAChargeIfRetriesExceeded() {
        ChargeEntity mockCharge1 = mock(ChargeEntity.class);
        ChargeEntity mockCharge2 = mock(ChargeEntity.class);

        when(mockChargeDao.findChargesForCapture(10, Duration.ofMinutes(60))).thenReturn(asList(mockCharge1, mockCharge2));
        when(mockCharge1.getExternalId()).thenReturn("my-charge-1");
        when(mockCharge2.getExternalId()).thenReturn("my-charge-2");
        when(mockCharge1.getId()).thenReturn(1L);
        when(mockCharge2.getId()).thenReturn(2L);


        when(mockChargeDao.countCaptureRetriesForCharge(1L)).thenReturn(MAXIMUM_RETRIES);
        when(mockChargeDao.countCaptureRetriesForCharge(2L)).thenReturn(2);

        cardCaptureProcess.runCapture();

        verify(mockCardCaptureService, never()).doCapture("my-charge-1");
        verify(mockCardCaptureService).doCapture("my-charge-2");
    }

    @Test
    public void shouldMarkCaptureAsErrorWhenChargeRetriesExceeded() {

        String chargeId = "my-charge-1";
        ChargeEntity mockCharge1 = mock(ChargeEntity.class);

        when(mockChargeDao.findChargesForCapture(10, Duration.ofMinutes(60))).thenReturn(singletonList(mockCharge1));
        when(mockCharge1.getExternalId()).thenReturn(chargeId);
        when(mockCharge1.getId()).thenReturn(1L);


        when(mockChargeDao.countCaptureRetriesForCharge(1L)).thenReturn(MAXIMUM_RETRIES);

        cardCaptureProcess.runCapture();

        verify(mockCardCaptureService).markChargeAsCaptureError(chargeId);
    }
}
