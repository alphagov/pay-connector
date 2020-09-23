package uk.gov.pay.connector.tasks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.tasks.service.ChargeParityChecker;
import uk.gov.pay.connector.tasks.service.ParityCheckService;

import java.util.List;
import java.util.Optional;

import static java.time.ZonedDateTime.parse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.commons.model.Source.CARD_PAYMENT_LINK;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultCardDetails;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.DATA_MISMATCH;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.from;
import static uk.gov.pay.connector.pact.ChargeEventEntityFixture.aValidChargeEventEntity;
import static uk.gov.pay.connector.wallets.WalletType.APPLE_PAY;

@RunWith(MockitoJUnitRunner.class)
public class ParityCheckServiceTest {

    private ParityCheckService parityCheckService;
    @Mock
    private LedgerService mockLedgerService;
    @Mock
    private ChargeService mockChargeService;
    @Mock
    private RefundService mockRefundService;
    @Mock
    private RefundDao mockRefundDao;
    @Mock
    private HistoricalEventEmitter mockHistoricalEventEmitter;
    @Mock
    private PaymentProviders mockProviders;
    @InjectMocks
    ChargeParityChecker chargeParityChecker;
    private ChargeEntity chargeEntity;
    private List<RefundEntity> refundEntities = List.of();

    @Before
    public void setUp() {
        ChargeEventEntity chargeEventCreated = createChargeEventEntity(CREATED, "2016-01-25T13:23:55Z");
        ChargeEventEntity chargeEventCaptured = createChargeEventEntity(CAPTURED, "2016-01-26T14:23:55Z");
        ChargeEventEntity chargeEventCaptureSubmitted = createChargeEventEntity(CAPTURE_SUBMITTED,
                "2016-01-26T13:23:55Z");

        chargeEntity = aValidChargeEntity()
                .withStatus(CAPTURED)
                .withCardDetails(defaultCardDetails())
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .withMoto(true)
                .withSource(CARD_PAYMENT_LINK)
                .withFee(10L)
                .withCorporateSurcharge(25L)
                .withWalletType(APPLE_PAY)
                .withDelayedCapture(true)
                .withEvents(List.of(chargeEventCreated, chargeEventCaptured, chargeEventCaptureSubmitted))
                .build();

        when(mockRefundDao.findRefundsByChargeExternalId(any())).thenReturn(refundEntities);
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());
        parityCheckService = new ParityCheckService(mockLedgerService, mockChargeService, mockRefundDao,
                mockHistoricalEventEmitter, chargeParityChecker);
    }

    @Test
    public void chargeAndRefundsParityCheckStatus_shouldFetchTransactionFromLedgerAndParityCheck() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .build();
        when(mockLedgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(transaction));

        ParityCheckStatus chargeAndRefundsParityCheckStatus = parityCheckService.getChargeAndRefundsParityCheckStatus(chargeEntity);

        assertThat(chargeAndRefundsParityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    public void parityCheckChargeForExpunger_shouldBackfillChargeIfParityCheckFails() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withGatewayAccountId(345345L)
                .isLive(true)
                .withPaymentProvider("test-paymemt-provider")
                .build();
        when(mockLedgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(transaction));

        boolean matchesWithLedger = parityCheckService.parityCheckChargeForExpunger(chargeEntity);

        assertThat(matchesWithLedger, is(false));
        verify(mockHistoricalEventEmitter).processPaymentEvents(chargeEntity, true);
        verify(mockChargeService).updateChargeParityStatus(chargeEntity.getExternalId(), DATA_MISMATCH);
    }

    @Test
    public void parityCheckChargeForExpunger_shouldNotBackfillIfChargeMatchesWithLedger() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .build();
        when(mockLedgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(transaction));

        boolean matchesWithLedger = parityCheckService.parityCheckChargeForExpunger(chargeEntity);

        assertThat(matchesWithLedger, is(true));
        verify(mockHistoricalEventEmitter, never()).processPaymentEvents(chargeEntity, true);
        verify(mockChargeService, never()).updateChargeParityStatus(chargeEntity.getExternalId(), DATA_MISMATCH);
    }

    private ChargeEventEntity createChargeEventEntity(ChargeStatus status, String timeStamp) {
        return aValidChargeEventEntity()
                .withCharge(chargeEntity)
                .withChargeStatus(status)
                .withTimestamp(parse(timeStamp))
                .build();
    }
}
