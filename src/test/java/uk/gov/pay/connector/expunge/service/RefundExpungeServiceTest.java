package uk.gov.pay.connector.expunge.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.tasks.service.ParityCheckService;

import java.time.ZonedDateTime;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.SKIPPED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;

@RunWith(MockitoJUnitRunner.class)
public class RefundExpungeServiceTest {

    private RefundExpungeService refundExpungeService;
    private int minimumAgeOfRefundInDays = 3;
    private int defaultNumberOfRefundsToExpunge = 10;
    private int defaultExcludeRefundsParityCheckedWithInDays = 10;

    @Mock
    private ExpungeConfig mockExpungeConfig;
    @Mock
    private RefundDao mockRefundDao;
    @Mock
    private RefundService mockRefundService;
    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;
    @Mock
    private ParityCheckService mockParityCheckService;

    @Before
    public void setUp() {
        when(mockExpungeConfig.isExpungeRefundsEnabled()).thenReturn(true);
        when(mockExpungeConfig.getMinimumAgeOfRefundInDays()).thenReturn(minimumAgeOfRefundInDays);
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeRefundsParityCheckedWithInDays);
        when(mockExpungeConfig.getMinimumAgeForHistoricRefundExceptions()).thenReturn(10);

        when(mockConnectorConfiguration.getExpungeConfig()).thenReturn(mockExpungeConfig);

        refundExpungeService = new RefundExpungeService(mockConnectorConfiguration, mockParityCheckService,
                mockRefundService, mockRefundDao);
    }

    @Test
    public void expunge_shouldExpungeNoOfRefundsAsPerConfiguration() {
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withStatus(REFUNDED).build();
        when(mockParityCheckService.parityCheckRefundForExpunger(any())).thenReturn(true);
        when(mockRefundDao.findRefundToExpunge(minimumAgeOfRefundInDays, defaultExcludeRefundsParityCheckedWithInDays))
                .thenReturn(Optional.of(refundEntity));
        refundExpungeService.expunge(defaultNumberOfRefundsToExpunge);

        verify(mockRefundDao, times(defaultNumberOfRefundsToExpunge)).expungeRefund(any());
        verify(mockRefundDao, times(defaultNumberOfRefundsToExpunge)).findRefundToExpunge(minimumAgeOfRefundInDays,
                defaultExcludeRefundsParityCheckedWithInDays);
    }

    @Test
    public void expunge_shouldExpungeHistoricRefundInNonTerminalState() {
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withCreatedDate(ZonedDateTime.now(UTC).minusDays(20))
                .withStatus(REFUND_SUBMITTED).build();
        when(mockRefundDao.findRefundToExpunge(minimumAgeOfRefundInDays, defaultExcludeRefundsParityCheckedWithInDays))
                .thenReturn(Optional.of(refundEntity));
        when(mockParityCheckService.parityCheckRefundForExpunger(refundEntity)).thenReturn(true);

        refundExpungeService.expunge(1);

        verify(mockRefundDao).expungeRefund(refundEntity.getExternalId());
    }

    @Test
    public void expunge_shouldNotExpungeRefundsIfFeatureIsNotEnabled() {
        when(mockExpungeConfig.isExpungeRefundsEnabled()).thenReturn(false);

        refundExpungeService.expunge(null);
        verifyNoInteractions(mockRefundDao);
    }

    @Test
    public void expunge_shouldNotExpungeRefundInNonTerminalState() {
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withStatus(REFUND_SUBMITTED).build();
        when(mockRefundDao.findRefundToExpunge(minimumAgeOfRefundInDays, defaultExcludeRefundsParityCheckedWithInDays))
                .thenReturn(Optional.of(refundEntity));

        refundExpungeService.expunge(1);

        verify(mockRefundService).updateRefundParityStatus(refundEntity.getExternalId(), SKIPPED);
        verify(mockRefundDao, never()).expungeRefund(any());
    }

    @Test
    public void expunge_shouldNotExpungeRefundIfParityCheckFailed() {
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withStatus(REFUNDED).build();
        when(mockRefundDao.findRefundToExpunge(minimumAgeOfRefundInDays, defaultExcludeRefundsParityCheckedWithInDays))
                .thenReturn(Optional.of(refundEntity));
        when(mockParityCheckService.parityCheckRefundForExpunger(refundEntity)).thenReturn(false);

        refundExpungeService.expunge(1);

        verify(mockRefundDao, never()).expungeRefund(any());
    }
}
