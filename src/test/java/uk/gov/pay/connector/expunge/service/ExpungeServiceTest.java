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

    int defaultNumberOfChargesOrRefundsToExpunge = 999;
    ExpungeService expungeService;

    @Before
    public void setUp() {
        ExpungeConfig expungeConfig = mock(ExpungeConfig.class);
        when(mockConnectorConfiguration.getExpungeConfig()).thenReturn(expungeConfig);
        when(expungeConfig.getNumberOfChargesOrRefundsToExpunge()).thenReturn(defaultNumberOfChargesOrRefundsToExpunge);

        expungeService = new ExpungeService(mockChargeExpungeService, mockRefundExpungeService, mockConnectorConfiguration);
    }

    @Test
    public void shouldInvokeChargeAndRefundExpungerWithNoOfRecordsToExpungeBasedOnConfigurationWhenQueryParamIsNotPassed() {
        expungeService.expunge(null);

        verify(mockChargeExpungeService).expunge(defaultNumberOfChargesOrRefundsToExpunge);
        verify(mockRefundExpungeService).expunge(defaultNumberOfChargesOrRefundsToExpunge);
    }

    @Test
    public void shouldInvokeChargeAndRefundExpungerWithQueryParameterPassedForNoOfRecordsToExpunge() {
        expungeService.expunge(5);

        verify(mockChargeExpungeService).expunge(5);
        verify(mockRefundExpungeService).expunge(5);
    }
}
