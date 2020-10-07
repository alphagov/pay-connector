package uk.gov.pay.connector.expunge.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExpungeServiceTest {

    @Mock
    ChargeExpungeService mockChargeExpungeService;
    @Mock
    RefundExpungeService mockRefundExpungeService;
    @Mock
    ConnectorConfiguration mockConnectorConfiguration;

    int defaultNumberOfChargesToExpunge = 999;
    int defaultNumberOfRefundsToExpunge = 100;
    ExpungeService expungeService;

    @Before
    public void setUp() {
        ExpungeConfig expungeConfig = mock(ExpungeConfig.class);
        when(mockConnectorConfiguration.getExpungeConfig()).thenReturn(expungeConfig);
        when(expungeConfig.getNumberOfChargesToExpunge()).thenReturn(defaultNumberOfChargesToExpunge);
        when(expungeConfig.getNumberOfRefundsToExpunge()).thenReturn(defaultNumberOfRefundsToExpunge);

        expungeService = new ExpungeService(mockChargeExpungeService, mockRefundExpungeService, mockConnectorConfiguration);
    }

    @Test
    public void shouldInvokeChargeAndRefundExpungerWithNoOfRecordsToExpungeBasedOnConfigurationWhenQueryParamIsNotPassed() {
        expungeService.expunge(null, null);

        verify(mockChargeExpungeService).expunge(defaultNumberOfChargesToExpunge);
        verify(mockRefundExpungeService).expunge(defaultNumberOfRefundsToExpunge);
    }

    @Test
    public void shouldInvokeChargeAndRefundExpungerWithQueryParameterPassedForNoOfRecordsToExpunge() {
        expungeService.expunge(5, 10);

        verify(mockChargeExpungeService).expunge(5);
        verify(mockRefundExpungeService).expunge(10);
    }
}
