package uk.gov.pay.connector.expunge.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpungeServiceTest {

    @Mock
    ChargeExpungeService mockChargeExpungeService;
    @Mock
    RefundExpungeService mockRefundExpungeService;
    @Mock
    ConnectorConfiguration mockConnectorConfiguration;

    int defaultNumberOfChargesToExpunge = 999;
    int defaultNumberOfRefundsToExpunge = 100;
    ExpungeService expungeService;


    @Test
    void shouldInvokeChargeAndRefundExpungerWithNoOfRecordsToExpungeBasedOnConfigurationWhenQueryParamIsNotPassed() {
        ExpungeConfig expungeConfig = mock(ExpungeConfig.class);
        when(mockConnectorConfiguration.getExpungeConfig()).thenReturn(expungeConfig);
        when(expungeConfig.getNumberOfChargesToExpunge()).thenReturn(defaultNumberOfChargesToExpunge);
        when(expungeConfig.getNumberOfRefundsToExpunge()).thenReturn(defaultNumberOfRefundsToExpunge);

        expungeService = new ExpungeService(mockChargeExpungeService, mockRefundExpungeService, mockConnectorConfiguration);

        expungeService.expunge(null, null);

        verify(mockChargeExpungeService).expunge(defaultNumberOfChargesToExpunge);
        verify(mockRefundExpungeService).expunge(defaultNumberOfRefundsToExpunge);
    }

    @Test
    void shouldInvokeChargeAndRefundExpungerWithQueryParameterPassedForNoOfRecordsToExpunge() {
        ExpungeConfig expungeConfig = mock(ExpungeConfig.class);
        when(mockConnectorConfiguration.getExpungeConfig()).thenReturn(expungeConfig);

        expungeService = new ExpungeService(mockChargeExpungeService, mockRefundExpungeService, mockConnectorConfiguration);

        expungeService.expunge(5, 10);

        verify(mockChargeExpungeService).expunge(5);
        verify(mockRefundExpungeService).expunge(10);
    }
}
