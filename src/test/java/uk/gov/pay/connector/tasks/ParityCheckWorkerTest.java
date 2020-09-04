package uk.gov.pay.connector.tasks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.commons.model.Source.CARD_PAYMENT_LINK;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultCardDetails;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.from;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.wallets.WalletType.APPLE_PAY;

@RunWith(MockitoJUnitRunner.class)
public class ParityCheckWorkerTest {
    @Mock
    private ChargeDao chargeDao;
    @Mock
    private ChargeService chargeService;
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

    private ParityCheckWorker worker;
    private ChargeEntity chargeEntity;
    private boolean doNotReprocessValidRecords = false;
    private Optional<String> emptyParityCheckStatus = Optional.empty();

    @Before
    public void setUp() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());
        
        worker = new ParityCheckWorker(chargeDao, chargeService, ledgerService, emittedEventDao,
                stateTransitionService, eventService, refundDao, parityCheckService);
        CardDetailsEntity cardDetails = mock(CardDetailsEntity.class);
        chargeEntity = aValidChargeEntity()
                .withCardDetails(defaultCardDetails())
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .withMoto(true)
                .withSource(CARD_PAYMENT_LINK)
                .withFee(10L)
                .withCorporateSurcharge(25L)
                .withWalletType(APPLE_PAY)
                .withDelayedCapture(true)
                .build();
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(chargeEntity.getCreatedDate())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();
        chargeEntity.getEvents().add(chargeEventEntity);
    }

    @Test
    public void executeSkipsParityCheckForAlreadyCheckedChargesExistingInLedger() {
        chargeEntity.updateParityCheck(ParityCheckStatus.EXISTS_IN_LEDGER);
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, Optional.empty(), true, 
                emptyParityCheckStatus, null);

        verify(chargeService, never()).updateChargeParityStatus(any(), any());
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), any());
        verify(emittedEventDao, never()).recordEmission(any(), any());
        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void executeRecordsParityStatusForChargesExistingInLedger() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(from(chargeEntity, null).build()));

        worker.execute(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 1L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.EXISTS_IN_LEDGER);
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), any());
        verify(emittedEventDao, never()).recordEmission(any(), any());
        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void executeRecordsParityStatusForChargeAndRefundsExistingInLedger() {
        RefundEntity refundEntity = aValidRefundEntity().build();
        chargeEntity.setStatus(ChargeStatus.EXPIRED);
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(refundDao.findRefundsByChargeExternalId(chargeEntity.getExternalId())).thenReturn(List.of(refundEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(from(chargeEntity, null).build()));
        when(ledgerService.getTransaction(refundEntity.getExternalId()))
                .thenReturn(Optional.of(aValidLedgerTransaction().withStatus("submitted").build()));

        worker.execute(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 1L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.EXISTS_IN_LEDGER);
        verify(ledgerService, times(2)).getTransaction(any());
        verify(ledgerService, times(1)).getTransaction(chargeEntity.getExternalId());
        verify(refundDao, times(2)).findRefundsByChargeExternalId(chargeEntity.getExternalId());
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), any());
        verify(emittedEventDao, never()).recordEmission(any(), any());
        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void executeRecordsParityStatusForChargeWithDifferentStatusInLedger() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(aValidLedgerTransaction().withStatus("started").build()));

        worker.execute(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 1L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.DATA_MISMATCH);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(ledgerService, times(1)).getTransaction(chargeEntity.getExternalId());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    public void executeEmitsEventAndRecordsEmissionWhenRefundDoesNotExist() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(refundDao.findRefundsByChargeExternalId(chargeEntity.getExternalId()))
                .thenReturn(List.of(aValidRefundEntity().build(), aValidRefundEntity().build()));
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(any())).thenReturn(Optional.empty());
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(from(chargeEntity, null).build()));

        worker.execute(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 1L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.MISSING_IN_LEDGER);
        verify(ledgerService, times(2)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    public void executeEmitsEventAndRecordsEmissionWhenRefundWithDifferentStatusInLedger() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(refundDao.findRefundsByChargeExternalId(chargeEntity.getExternalId()))
                .thenReturn(List.of(aValidRefundEntity().build(), aValidRefundEntity().build()));
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(any())).thenReturn(Optional.of(
                aValidLedgerTransaction().withStatus("success").build()));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(from(chargeEntity, null).build()));

        worker.execute(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 1L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.DATA_MISMATCH);
        verify(ledgerService, times(2)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    public void executeEmitsEventAndRecordsEmission() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.empty());

        worker.execute(1L, Optional.empty(), doNotReprocessValidRecords, emptyParityCheckStatus, 
                120L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(),
                ParityCheckStatus.MISSING_IN_LEDGER);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    public void executeShouldEmitEventIfEmittedPreviously() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.empty());

        worker.execute(1L, Optional.of(1L), doNotReprocessValidRecords, emptyParityCheckStatus, 120L);

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.MISSING_IN_LEDGER);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }

    @Test
    public void executeForParityCheckStatusShouldEmitEventsOnlyForStatus() {
        when(chargeDao.findByParityCheckStatus(ParityCheckStatus.DATA_MISMATCH, 100, chargeEntity.getId())).thenReturn(List.of());
        when(chargeDao.findByParityCheckStatus(ParityCheckStatus.DATA_MISMATCH, 100, 0L)).thenReturn(List.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.empty());

        worker.execute(0L, Optional.empty(), doNotReprocessValidRecords, Optional.of("DATA_MISMATCH"), 1L);

        verify(chargeDao, times(2)).findByParityCheckStatus(eq(ParityCheckStatus.DATA_MISMATCH), anyInt(), any());
        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.MISSING_IN_LEDGER);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), notNull());
    }
}
