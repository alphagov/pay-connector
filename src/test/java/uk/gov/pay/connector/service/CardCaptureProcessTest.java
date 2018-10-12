package uk.gov.pay.connector.service;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureProcessTest {

    private static final int MAXIMUM_RETRIES = 10;
    private CardCaptureProcess cardCaptureProcess;

    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private CardCaptureService mockCardCaptureService;

    @Mock
    private Environment mockEnvironment;

    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;

    @Mock
    private GatewayResponse mockGatewayResponse;
    
    @Mock
    private MetricRegistry mockMetricRegistry;
    
    @Mock
    private
    CaptureProcessConfig mockCaptureConfiguration = mock(CaptureProcessConfig.class);

    @Before
    public void setup() {
        Histogram mockHistogram = mock(Histogram.class);

        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
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
        when(mockCaptureConfiguration.getBatchSize()).thenReturn(0);
        when(mockChargeDao.countChargesForImmediateCapture(Matchers.any(Duration.class))).thenReturn(15);
        
        cardCaptureProcess.runCapture();

        assertThat(cardCaptureProcess.getReadyCaptureQueueSize(), is(15));
    }


    @Test
    public void shouldRegisterGaugesForChargesQueue_AndReturnCorrectSizes() {
        when(mockCaptureConfiguration.getBatchSize()).thenReturn(0);
        when(mockChargeDao.countChargesForImmediateCapture(Matchers.any(Duration.class))).thenReturn(15);
        when(mockChargeDao.countChargesAwaitingCaptureRetry(Matchers.any(Duration.class))).thenReturn(10);
        cardCaptureProcess.runCapture();
        ArgumentCaptor<MetricRegistry.MetricSupplier> argumentCaptor = ArgumentCaptor.forClass(MetricRegistry.MetricSupplier.class);

        verify(mockMetricRegistry, times(2))
                .gauge(anyString(), argumentCaptor.capture());

        List<Gauge<Integer>> gauges = argumentCaptor.getAllValues()
                .stream()
                .map(a -> (Gauge<Integer>)a.newMetric())
                .collect(Collectors.toList());

        Gauge<Integer> queueSizeGauge = gauges.get(0);
        assertThat(queueSizeGauge.getValue(), is(15));

        Gauge<Integer> waitingQueueSizeGauge = gauges.get(1);
        assertThat(waitingQueueSizeGauge.getValue(), is(10));
    }

    @Test
    public void shouldRecordTheWaitingQueueSizeOnEveryRun() {
        when(mockChargeDao.countChargesAwaitingCaptureRetry(Matchers.any(Duration.class))).thenReturn(15);

        cardCaptureProcess.runCapture();

        assertThat(cardCaptureProcess.getWaitingCaptureQueueSize(), is(15));
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
