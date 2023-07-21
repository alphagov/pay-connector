package uk.gov.pay.connector.expunge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
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
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.FeeType.RADAR;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.SKIPPED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.service.payments.commons.model.AuthorisationMode.AGREEMENT;
import static uk.gov.service.payments.commons.model.AuthorisationMode.EXTERNAL;
import static uk.gov.service.payments.commons.model.AuthorisationMode.WEB;

@ExtendWith(MockitoExtension.class)
class ChargeExpungeServiceTest {

    @Mock
    private ExpungeConfig mockExpungeConfig;
    @Mock
    private ChargeDao mockChargeDao;
    @Mock
    private ChargeService mockChargeService;
    @Mock
    private  IdempotencyDao mockIdempotencyDao;
    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;
    @Mock
    private ParityCheckService parityCheckService;

    private final int minimumAgeOfChargeInDays = 3;
    private final int defaultNumberOfChargesToExpunge = 10;
    private final int defaultExcludeChargesParityCheckedWithInDays = 1;
    private ChargeExpungeService chargeExpungeService;

    private GatewayAccountEntity testGatewayAccount = aGatewayAccountEntity()
            .withType(GatewayAccountType.TEST)
            .build();
    private GatewayAccountEntity liveGatewayAccount = aGatewayAccountEntity()
            .withType(GatewayAccountType.LIVE)
            .build();

    @BeforeEach
    void setUp() {
        when(mockConnectorConfiguration.getExpungeConfig()).thenReturn(mockExpungeConfig);
        when(mockExpungeConfig.isExpungeChargesEnabled()).thenReturn(true);

        chargeExpungeService = new ChargeExpungeService(mockChargeDao, mockConnectorConfiguration, parityCheckService,
                mockChargeService, mockIdempotencyDao);
    }

    @Test
   void expunge_shouldExpungeNoOfChargesAsPerConfiguration() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity));

        chargeExpungeService.expunge(defaultNumberOfChargesToExpunge);
        verify(mockChargeDao, times(defaultNumberOfChargesToExpunge)).findChargeToExpunge(minimumAgeOfChargeInDays,
                defaultExcludeChargesParityCheckedWithInDays);
    }

    @Test
    void expunge_shouldNotExpungeChargesIfFeatureIsNotEnabled() {
        when(mockExpungeConfig.isExpungeChargesEnabled()).thenReturn(false);
        chargeExpungeService.expunge(null);
        verifyNoInteractions(mockChargeDao);
    }

    @Test
    void expunge_shouldNotExpungeChargeIfInNonTerminalStateAndUpdateParityCheckStatusToSkipped() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(CREATED)
                .withPaymentProvider("worldpay")
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
    void expunge_shouldNotExpungeCharge_ifIsStripePaymentMissingFees_andUpdateParityCheckStatusToSkipped() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(liveGatewayAccount)
                .withStatus(AUTHORISATION_REJECTED)
                .withPaymentProvider("stripe")
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withCreatedDate(Instant.parse("2022-01-01T11:08:00.000Z"))
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
    void expunge_shouldExpungeCharge_whenStripePaymentHasFees() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(liveGatewayAccount)
                .withStatus(AUTHORISATION_REJECTED)
                .withPaymentProvider("stripe")
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withCreatedDate(Instant.parse("2022-01-01T11:08:00.000Z"))
                .withFee(Fee.of(RADAR, 5L))
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
    void expunge_shouldNotExpungeCharge__whenInAuthorisationErrorState() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(liveGatewayAccount)
                .withStatus(AUTHORISATION_ERROR)
                .withPaymentProvider("stripe")
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withCreatedDate(Instant.parse("2022-01-01T11:08:00.000Z"))
                .withFee(Fee.of(RADAR, 5L))
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
    void expunge_shouldExpungeCharge_whenInAuthorisationErrorStateButIsExternalReportedPayment() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAuthorisationMode(EXTERNAL)
                .withGatewayAccountEntity(liveGatewayAccount)
                .withStatus(AUTHORISATION_ERROR)
                .withPaymentProvider("worldpay")
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withCreatedDate(Instant.parse("2022-01-01T11:08:00.000Z"))
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
    void expunge_shouldExpungeCharge_ifStripePaymentWithoutGatewayTransactionIdAndNoFees() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(liveGatewayAccount)
                .withStatus(AUTHORISATION_REJECTED)
                .withPaymentProvider("stripe")
                .withCreatedDate(Instant.parse("2022-01-01T11:08:00.000Z"))
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
    void expunge_shouldNotExpungeCharge_ifIsTestStripePaymentMissingFees() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(testGatewayAccount)
                .withStatus(AUTHORISATION_REJECTED)
                .withPaymentProvider("stripe")
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withCreatedDate(Instant.parse("2021-01-01T01:00:00.000Z"))
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
    void expunge_shouldNotExpungeChargeIfAuthorisationModeIsAgreementModeAndIdempotencyRecordExists() {
        String resourceId = "test-resource-id";

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(AUTHORISATION_SUBMITTED)
                .withAuthorisationMode(AGREEMENT)
                .withExternalId(resourceId)
                .build();

        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity));
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);
        when(mockIdempotencyDao.idempotencyExistsByResourceExternalId(resourceId)).thenReturn(true);

        chargeExpungeService.expunge(1);
        verify(mockChargeDao, never()).expungeCharge(any(), any());
        verify(mockIdempotencyDao).idempotencyExistsByResourceExternalId(resourceId);
    }

    @Test
    void expunge_shouldExpungeChargeIfAuthorisationModeIsAgreementAndIdempotencyRecordDoesNotExist() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(AUTHORISATION_SUBMITTED)
                .withAuthorisationMode(AGREEMENT)
                .build();

        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity));
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);
        when(parityCheckService.parityCheckChargeForExpunger(chargeEntity)).thenReturn(true);

        chargeExpungeService.expunge(1);

        verify(mockChargeDao).expungeCharge(chargeEntity.getId(), chargeEntity.getExternalId());
        verify(mockChargeService, never()).updateChargeParityStatus(any(),any());
    }


    @Test
    void expunge_shouldExpungeChargeIfAuthorisationModeIsNotAgreement() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(AUTHORISATION_SUBMITTED)
                .withAuthorisationMode(WEB)
                .build();

        when(mockExpungeConfig.getMinimumAgeOfChargeInDays()).thenReturn(minimumAgeOfChargeInDays);
        when(mockChargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, defaultExcludeChargesParityCheckedWithInDays))
                .thenReturn(Optional.of(chargeEntity));
        when(mockExpungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays()).thenReturn(defaultExcludeChargesParityCheckedWithInDays);
        when(parityCheckService.parityCheckChargeForExpunger(chargeEntity)).thenReturn(true);

        chargeExpungeService.expunge(1);

        verify(mockChargeDao).expungeCharge(chargeEntity.getId(), chargeEntity.getExternalId());
        verify(mockChargeService, never()).updateChargeParityStatus(any(), any());

    }

    @ParameterizedTest
    @ValueSource( strings = {"CAPTURE_SUBMITTED", "EXPIRE_CANCEL_SUBMITTED", "SYSTEM_CANCEL_SUBMITTED", "USER_CANCEL_SUBMITTED"})
    void expunge_shouldExpungeChargeIfInSubmittedStateAndChargeIsOlderThanHistoric(String state) {
        when(mockExpungeConfig.getMinimumAgeForHistoricChargeExceptions()).thenReturn(2);

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(5)))
                .withStatus(ChargeStatus.valueOf(state))
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
    void expunge_shouldNotExpungeChargeIfInCaptureSubmittedAndChargeIsNewerThanHistoric() {
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
    void expunge_whenChargeMeetsTheConditions() {
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


    @ParameterizedTest
    @CsvSource( {"AUTHORISATION_ERROR", "AUTHORISATION_TIMEOUT", "AUTHORISATION_UNEXPECTED_ERROR"})
    void shouldNotExpungeEpdqChargeWithState(String state) {
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


    @ParameterizedTest
    @ValueSource( strings = {"USER_CANCELLED", "EXPIRED", "CAPTURE_ERROR", "AUTHORISATION_REJECTED"})
    void shouldExpungeEpdqChargesWithState(String state) {
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
