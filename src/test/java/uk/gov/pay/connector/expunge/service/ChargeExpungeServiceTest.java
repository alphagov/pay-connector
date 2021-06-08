package uk.gov.pay.connector.expunge.service;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.tasks.service.ParityCheckService;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.SKIPPED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

@RunWith(JUnitParamsRunner.class)
public class ChargeExpungeServiceTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private ChargeExpungeService chargeExpungeService;
    private int minimumAgeOfChargeInDays = 3;
    private int defaultNumberOfChargesToExpunge = 10;
    private int defaultExcludeChargesParityCheckedWithInDays = 1;

    @Mock
    private ExpungeConfig mockExpungeConfig;
    @Mock
    private ChargeDao mockChargeDao;
    @Mock
    private ChargeService mockChargeService;
    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;
    @Mock
    private ParityCheckService parityCheckService;

    @Before
    public void setUp() {
        when(mockConnectorConfiguration.getExpungeConfig()).thenReturn(mockExpungeConfig);
        when(mockExpungeConfig.isExpungeChargesEnabled()).thenReturn(true);

        chargeExpungeService = new ChargeExpungeService(mockChargeDao, mockConnectorConfiguration, parityCheckService,
                mockChargeService);
    }

    @Test
    public void expunge_shouldExpungeNoOfChargesAsPerConfiguration() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays,defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity));

        chargeExpungeService.expunge(defaultNumberOfChargesToExpunge);
        verify(mockChargeDao, times(defaultNumberOfChargesToExpunge)).findChargeToExpunge(minimumAgeOfChargeInDays,
                defaultExcludeChargesParityCheckedWithInDays);
    }

    @Test
    public void expunge_shouldNotExpungeChargesIfFeatureIsNotEnabled() {
        when(mockExpungeConfig.isExpungeChargesEnabled()).thenReturn(false);
        chargeExpungeService.expunge(null);
        verifyNoInteractions(mockChargeDao);
    }

    @Test
    public void expunge_shouldNotExpungeChargeIfInNonTerminalStateAndUpdateParityCheckStatusToSkipped() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(CREATED)
                .withPaymentProvider("epdq")
                .build();
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity));
        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);

        chargeExpungeService.expunge(1);

        verify(mockChargeService).updateChargeParityStatus(chargeEntity.getExternalId(), SKIPPED);
        verify(mockChargeDao, never()).expungeCharge(any(), any());
    }

    @Test
    public void expunge_shouldExpungeChargeIfInCaptureSubmittedAndChargeIsOlderThanHistoric() {
        when(mockExpungeConfig.getMinimumAgeForHistoricChargeExceptions()).thenReturn(2);

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(5)))
                .withStatus(CAPTURE_SUBMITTED)
                .build();
        when(mockExpungeConfig.isExpungeChargesEnabled()).thenReturn(true);
        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(parityCheckService.parityCheckChargeForExpunger(chargeEntity)).thenReturn(true);
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity));
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);

        chargeExpungeService.expunge(1);

        verify(mockChargeDao).expungeCharge(chargeEntity.getId(), chargeEntity.getExternalId());
    }

    @Test
    public void expunge_shouldNotExpungeChargeIfInCaptureSubmittedAndChargeIsNewerThanHistoric() {
        when(mockExpungeConfig.getMinimumAgeForHistoricChargeExceptions()).thenReturn(8);

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(5)))
                .withStatus(CAPTURE_SUBMITTED)
                .build();
        when(mockExpungeConfig.isExpungeChargesEnabled()).thenReturn(true);
        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity));
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);

        chargeExpungeService.expunge(1);

        verify(mockChargeService).updateChargeParityStatus(chargeEntity.getExternalId(), SKIPPED);
        verify(mockChargeDao, never()).expungeCharge(any(), any());
    }

    @Test
    public void expunge_whenChargeMeetsTheConditions() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(aGatewayAccountEntity().withId(1L).build())
                .withAmount(120L)
                .withStatus(CAPTURED)
                .build();
        when(mockExpungeConfig.isExpungeChargesEnabled()).thenReturn(true);
        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity)).thenReturn(Optional.empty());
        when(parityCheckService.parityCheckChargeForExpunger(chargeEntity)).thenReturn(true);
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);

        chargeExpungeService.expunge(2);
        verify(mockChargeDao).expungeCharge(chargeEntity.getId(), chargeEntity.getExternalId());
    }

    @Test
    @Parameters({"AUTHORISATION_ERROR", "AUTHORISATION_TIMEOUT", "AUTHORISATION_UNEXPECTED_ERROR"})
    public void shouldNotExpungeEpdqChargeWithState(String state) {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(5)))
                .withStatus(ChargeStatus.valueOf(state))
                .withPaymentProvider("epdq")
                .withGatewayAccountEntity(aGatewayAccountEntity().withGatewayName("epdq").build())
                .build();

        when(mockExpungeConfig.isExpungeChargesEnabled()).thenReturn(true);
        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity));
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);

        chargeExpungeService.expunge(1);

        verify(mockChargeService).updateChargeParityStatus(chargeEntity.getExternalId(), SKIPPED);
        verify(mockChargeDao, never()).expungeCharge(any(), any());
    }
    
    @Test
    @Parameters({"USER_CANCELLED", "EXPIRED", "CAPTURE_ERROR", "AUTHORISATION_REJECTED"})
    public void shouldExpungeEpdqChargesWithState(String state) {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(5)))
                .withStatus(ChargeStatus.valueOf(state))
                .withGatewayAccountEntity(aGatewayAccountEntity().withGatewayName("epdq").build())
                .build();

        when(mockExpungeConfig.isExpungeChargesEnabled()).thenReturn(true);
        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity));
        when(parityCheckService.parityCheckChargeForExpunger(chargeEntity)).thenReturn(true);
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);

        chargeExpungeService.expunge(1);
        verify(mockChargeDao).expungeCharge(chargeEntity.getId(), chargeEntity.getExternalId());
    }
}
