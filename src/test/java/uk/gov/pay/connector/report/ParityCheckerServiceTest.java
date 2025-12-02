package uk.gov.pay.connector.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.service.RefundEntityFactory;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.tasks.HistoricalEventEmitter;
import uk.gov.pay.connector.tasks.service.ChargeParityChecker;
import uk.gov.pay.connector.tasks.service.ParityCheckService;
import uk.gov.pay.connector.tasks.service.ParityCheckerService;
import uk.gov.pay.connector.tasks.service.RefundParityChecker;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultCardDetails;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.DATA_MISMATCH;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.MISSING_IN_LEDGER;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.from;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.pact.RefundHistoryEntityFixture.aValidRefundHistoryEntity;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.wallets.WalletType.APPLE_PAY;
import static uk.gov.service.payments.commons.model.Source.CARD_PAYMENT_LINK;

@ExtendWith(MockitoExtension.class)
class ParityCheckerServiceTest {
    @Mock
    private ChargeDao chargeDao;
    @Mock
    private ChargeService chargeService;    
    @Mock
    private PaymentInstrumentService paymentInstrumentService;
    @Mock
    private RefundService refundService;
    @Mock
    private EmittedEventDao emittedEventDao;
    @Mock
    private StateTransitionService stateTransitionService;
    @InjectMocks
    private ParityCheckService parityCheckService;
    @Mock
    private EventService eventService;
    @Mock
    private RefundDao refundDao;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private PaymentProviders mockProviders;
    @Mock
    private HistoricalEventEmitter historicalEventEmitter;
    @Mock
    private RefundEntityFactory refundEntityFactory;
    @InjectMocks
    ChargeParityChecker chargeParityChecker;

    private ParityCheckerService parityCheckerService;
    private ChargeEntity chargeEntity;
    private RefundEntity refundEntity;
    private List<RefundHistory> refundHistoryList;
    private boolean doNotReprocessValidRecords = false;
    private Optional<String> emptyParityCheckStatus = Optional.empty();
    private RefundParityChecker refundParityChecker;

    @BeforeEach
    void setUp() {
        lenient().when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(refundEntityFactory));
        refundParityChecker = new RefundParityChecker(refundDao);
        parityCheckService = new ParityCheckService(ledgerService, chargeService, historicalEventEmitter,
                chargeParityChecker, refundParityChecker, refundService);

        parityCheckerService = new ParityCheckerService(chargeDao, chargeService, emittedEventDao,
                stateTransitionService, eventService, refundService, refundDao, parityCheckService);
        chargeEntity = aValidChargeEntity()
                .withCardDetails(defaultCardDetails())
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .withMoto(true)
                .withSource(CARD_PAYMENT_LINK)
                .withFee(Fee.of(null, 10L))
                .withCorporateSurcharge(25L)
                .withWalletType(APPLE_PAY)
                .withDelayedCapture(true)
                .build();
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.ofInstant(chargeEntity.getCreatedDate(), ZoneOffset.UTC))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();
        chargeEntity.getEvents().add(chargeEventEntity);

        refundEntity = aValidRefundEntity()
                .withChargeExternalId(chargeEntity.getExternalId())
                .build();
        RefundHistory refundHistory = aValidRefundHistoryEntity()
                .withChargeExternalId(refundEntity.getChargeExternalId())
                .withStatus(CREATED.toString())
                .withHistoryStartDate(refundEntity.getCreatedDate())
                .build();
        refundHistoryList = new ArrayList<>();
        refundHistoryList.add(refundHistory);
    }

    @Test
    void executeSkipsParityCheckForAlreadyCheckedChargesExistingInLedger() {
        chargeEntity.updateParityCheck(ParityCheckStatus.EXISTS_IN_LEDGER);
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        parityCheckerService.checkParity(1L, Optional.empty(), true,
                emptyParityCheckStatus, null);

        verify(chargeService, never()).updateChargeParityStatus(any(), any());
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), any());
        verify(emittedEventDao, never()).recordEmission(any(), any());
        verify(chargeDao, never()).findById(2L);
    }

    @Test
    void executeRecordsParityStatusForChargesExistingInLedger() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(from(chargeEntity, null).build()));

        parityCheckerService.checkParity(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 1L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.EXISTS_IN_LEDGER);
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), any());
        verify(emittedEventDao, never()).recordEmission(any(), any());
        verify(chargeDao, never()).findById(2L);
    }

    @Test
    void executeRecordsParityStatusForChargeAndRefundsExistingInLedger() {
        RefundEntity refundEntity = aValidRefundEntity().build();
        chargeEntity.setStatus(ChargeStatus.EXPIRED);
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(refundService.findNotExpungedRefunds(chargeEntity.getExternalId())).thenReturn(List.of(refundEntity));
        when(refundService.findRefunds(Charge.from(chargeEntity))).thenReturn(List.of(Refund.from(refundEntity)));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(from(chargeEntity, null).build()));
        LedgerTransaction refundTransaction = from(chargeEntity.getGatewayAccount().getId(), refundEntity).build();
        when(ledgerService.getTransaction(refundEntity.getExternalId()))
                .thenReturn(Optional.of(refundTransaction));
        RefundHistory refundHistory = aValidRefundHistoryEntity()
                .withStatus(CREATED.toString())
                .withHistoryStartDate(refundEntity.getCreatedDate())
                .build();
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(refundEntity.getExternalId(), CREATED)).thenReturn(Optional.of(refundHistory));

        parityCheckerService.checkParity(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 1L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.EXISTS_IN_LEDGER);
        verify(ledgerService, times(2)).getTransaction(any());
        verify(ledgerService, times(1)).getTransaction(chargeEntity.getExternalId());
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), any());
        verify(emittedEventDao, never()).recordEmission(any(), any());
        verify(chargeDao, never()).findById(2L);
    }

    @Test
    void executeRecordsParityStatusForChargeWithDifferentStatusInLedger() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(aValidLedgerTransaction().withStatus("started").build()));

        parityCheckerService.checkParity(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 1L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), DATA_MISMATCH);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(ledgerService, times(1)).getTransaction(chargeEntity.getExternalId());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    void executeEmitsEventAndRecordsEmissionWhenRefundDoesNotExist() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(refundService.findNotExpungedRefunds(chargeEntity.getExternalId()))
                .thenReturn(List.of(aValidRefundEntity().build(), aValidRefundEntity().build()));
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(any())).thenReturn(Optional.empty());
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(from(chargeEntity, null).build()));

        parityCheckerService.checkParity(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 1L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), MISSING_IN_LEDGER);
        verify(ledgerService, times(2)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    void executeEmitsEventAndRecordsEmissionWhenRefundWithDifferentStatusInLedger() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(refundService.findNotExpungedRefunds(chargeEntity.getExternalId()))
                .thenReturn(List.of(aValidRefundEntity().build(), aValidRefundEntity().build()));
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(any())).thenReturn(Optional.of(
                aValidLedgerTransaction().withStatus("failed").build()));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(from(chargeEntity, null).build()));

        parityCheckerService.checkParity(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 1L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), DATA_MISMATCH);
        verify(ledgerService, times(2)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    void executeEmitsEventAndRecordsEmission() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.empty());

        parityCheckerService.checkParity(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus,
                120L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(),
                MISSING_IN_LEDGER);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    void executeShouldEmitEventIfEmittedPreviously() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.empty());

        parityCheckerService.checkParity(1L, Optional.of(1L), doNotReprocessValidRecords, emptyParityCheckStatus, 120L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), MISSING_IN_LEDGER);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    void executeForParityCheckStatusShouldEmitEventsOnlyForStatus() {
        when(chargeDao.findByParityCheckStatus(DATA_MISMATCH, 100, chargeEntity.getId())).thenReturn(List.of());
        when(chargeDao.findByParityCheckStatus(DATA_MISMATCH, 100, 0L)).thenReturn(List.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.empty());

        parityCheckerService.checkParity(0L, Optional.empty(), doNotReprocessValidRecords, Optional.of("DATA_MISMATCH"), 1L);

        verify(chargeDao, times(2)).findByParityCheckStatus(eq(DATA_MISMATCH), anyInt(), any());
        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), MISSING_IN_LEDGER);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    void parityCheckForRefundsByParityCheckStatus__shouldEmitEventsAndSetParityCheckStatus() {
        when(refundDao.findByParityCheckStatus(DATA_MISMATCH, 100, refundEntity.getId())).thenReturn(List.of());
        when(refundDao.findByParityCheckStatus(DATA_MISMATCH, 100, 0L)).thenReturn(List.of(refundEntity));
        when(chargeService.findCharge(refundEntity.getChargeExternalId())).thenReturn(Optional.of(Charge.from(chargeEntity)));
        when(refundDao.getRefundHistoryByRefundExternalId(refundEntity.getExternalId())).thenReturn(refundHistoryList);
        when(ledgerService.getTransaction(refundEntity.getExternalId())).thenReturn(Optional.empty());

        parityCheckerService.checkParityForRefundsOnly(0L, null, doNotReprocessValidRecords, "DATA_MISMATCH", 1L);

        verify(refundDao, times(2)).findByParityCheckStatus(eq(DATA_MISMATCH), anyInt(), any());
        verify(refundService, times(1)).updateRefundParityStatus(refundEntity.getExternalId(), MISSING_IN_LEDGER);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    void parityCheckForRefundsByParityCheckStatus_shouldSkipPreviouslyMatchedRecordsWhenDoNoReprocessValidRecordsIsSet() {
        refundEntity.setParityCheckStatus(EXISTS_IN_LEDGER);
        when(refundDao.findByParityCheckStatus(EXISTS_IN_LEDGER, 100, 0L)).thenReturn(List.of(refundEntity));
        when(refundDao.findByParityCheckStatus(EXISTS_IN_LEDGER, 100, refundEntity.getId())).thenReturn(List.of());

        parityCheckerService.checkParityForRefundsOnly(0L, null, true, "EXISTS_IN_LEDGER", 1L);

        verify(refundService, never()).updateRefundParityStatus(any(), any());
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), notNull());
    }

    @Test
    void parityCheckRefundsByIdRange_shouldEmitEventsAndSetParityCheckStatus() {
        when(refundDao.findMaxId()).thenReturn(1L);
        when(refundDao.findById(1L)).thenReturn(Optional.of(refundEntity));

        when(chargeService.findCharge(refundEntity.getChargeExternalId())).thenReturn(Optional.of(Charge.from(chargeEntity)));
        when(refundDao.getRefundHistoryByRefundExternalId(refundEntity.getExternalId())).thenReturn(refundHistoryList);
        when(ledgerService.getTransaction(refundEntity.getExternalId())).thenReturn(Optional.empty());

        parityCheckerService.checkParityForRefundsOnly(1L, null, true, null, null);

        verify(refundService, times(1)).updateRefundParityStatus(refundEntity.getExternalId(), MISSING_IN_LEDGER);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), isNull());
    }

    @Test
    void parityCheckRefundsByIdRange_shouldSkipPreviouslyMatchedRecordsWhenDoNoReprocessValidRecordsIsSet() {
        refundEntity.setParityCheckStatus(EXISTS_IN_LEDGER);
        when(refundDao.findMaxId()).thenReturn(1L);
        when(refundDao.findById(1L)).thenReturn(Optional.of(refundEntity));

        parityCheckerService.checkParityForRefundsOnly(1L, null, true, null, null);

        verify(refundService, never()).updateRefundParityStatus(any(), any());
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), notNull());
    }
}
