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
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.pact.RefundHistoryEntityFixture;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.tasks.service.ChargeParityChecker;
import uk.gov.pay.connector.tasks.service.ParityCheckService;
import uk.gov.pay.connector.tasks.service.RefundParityChecker;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.time.ZonedDateTime.parse;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.service.payments.commons.model.Source.CARD_PAYMENT_LINK;
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
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
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
    private HistoricalEventEmitter mockHistoricalEventEmitter;
    @Mock
    private PaymentProviders mockProviders;
    @InjectMocks
    ChargeParityChecker chargeParityChecker;
    @Mock
    RefundDao mockRefundDao;
    private ChargeEntity chargeEntity;
    private RefundEntity refundEntity;
    private List<RefundEntity> refundEntities = List.of();
    @InjectMocks
    private RefundParityChecker refundParityChecker;

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
        refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withStatus(REFUNDED)
                .build();

        when(mockRefundService.findRefunds(any())).thenReturn(refundEntities.stream().map(Refund::from).collect(Collectors.toList()));
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());
        parityCheckService = new ParityCheckService(mockLedgerService, mockChargeService, mockHistoricalEventEmitter,
                chargeParityChecker, refundParityChecker, mockRefundService);
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

    @Test
    public void parityCheckRefundForExpunger_shouldBackfillRefundIfParityCheckFails() {
        LedgerTransaction transaction = from(nextLong(), refundEntity)
                .withStatus(REFUND_ERROR.toExternal().getStatus()).build();
        when(mockLedgerService.getTransaction(refundEntity.getExternalId())).thenReturn(Optional.of(transaction));

        boolean matchesWithLedger = parityCheckService.parityCheckRefundForExpunger(refundEntity);

        assertThat(matchesWithLedger, is(false));
        verify(mockHistoricalEventEmitter).emitEventsForRefund(refundEntity.getExternalId(), true);
        verify(mockRefundService).updateRefundParityStatus(refundEntity.getExternalId(), DATA_MISMATCH);
    }

    @Test
    public void parityCheckRefundForExpunger_shouldNotBackfillRefundIfMatchesWithLedger() {
        RefundHistory refundHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity().
                withHistoryStartDate(refundEntity.getCreatedDate()).build();
        when(mockRefundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(refundEntity.getExternalId(), RefundStatus.CREATED))
                .thenReturn(Optional.of(refundHistory));

        LedgerTransaction transaction = from(nextLong(), refundEntity).build();
        when(mockLedgerService.getTransaction(refundEntity.getExternalId())).thenReturn(Optional.of(transaction));

        boolean matchesWithLedger = parityCheckService.parityCheckRefundForExpunger(refundEntity);

        assertThat(matchesWithLedger, is(true));
        verify(mockHistoricalEventEmitter, never()).emitEventsForRefund(refundEntity.getExternalId(), true);
        verify(mockRefundService, never()).updateRefundParityStatus(any(), any());
    }

    private ChargeEventEntity createChargeEventEntity(ChargeStatus status, String timeStamp) {
        return aValidChargeEventEntity()
                .withCharge(chargeEntity)
                .withChargeStatus(status)
                .withTimestamp(parse(timeStamp))
                .build();
    }
}
