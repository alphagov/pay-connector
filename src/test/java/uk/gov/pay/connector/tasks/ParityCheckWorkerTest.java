package uk.gov.pay.connector.tasks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.paritycheck.LedgerService;
import uk.gov.pay.connector.queue.StateTransitionQueue;
import uk.gov.pay.connector.refund.dao.RefundDao;

import java.util.Optional;
import java.util.OptionalLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@RunWith(MockitoJUnitRunner.class)
public class ParityCheckWorkerTest {
    @Mock
    private ChargeDao chargeDao;
    @Mock
    private ChargeService chargeService;
    @Mock
    private EmittedEventDao emittedEventDao;
    @Mock
    private StateTransitionQueue stateTransitionQueue;
    @Mock
    private EventQueue eventQueue;
    @Mock
    private RefundDao refundDao;
    @Mock
    private LedgerService ledgerService;

    private ParityCheckWorker worker;
    private ChargeEntity chargeEntity;

    @Before
    public void setUp() {
        worker = new ParityCheckWorker(chargeDao, chargeService, ledgerService, emittedEventDao, stateTransitionQueue, eventQueue, refundDao);
        CardDetailsEntity cardDetails = mock(CardDetailsEntity.class);
        chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withCardDetails(cardDetails)
                .build();
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(chargeEntity.getCreatedDate())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();
        chargeEntity.getEvents().add(chargeEventEntity);
    }

    @Test
    public void executeRecordsParityStatusForChargesExistingInLedger() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(aValidLedgerTransaction().build()));

        worker.execute(1L, OptionalLong.empty());

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.EXISTS_IN_LEDGER);
        verify(stateTransitionQueue, never()).offer(any());
        verify(emittedEventDao, never()).recordEmission(any());
        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void executeRecordsParityStatusForChargeAndRefundsExistingInLedger() {
        chargeEntity.getRefunds().add(aValidRefundEntity().build());
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(aValidLedgerTransaction().build()));
        when(ledgerService.getTransaction(chargeEntity.getRefunds().get(0).getExternalId()))
                .thenReturn(Optional.of(aValidLedgerTransaction().withStatus("submitted").build()));

        worker.execute(1L, OptionalLong.empty());

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.EXISTS_IN_LEDGER);
        verify(ledgerService, times(2)).getTransaction(any());
        verify(ledgerService, times(1)).getTransaction(chargeEntity.getExternalId());
        verify(ledgerService, times(1)).getTransaction(chargeEntity.getRefunds().get(0).getExternalId());
        verify(stateTransitionQueue, never()).offer(any());
        verify(emittedEventDao, never()).recordEmission(any());
        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void executeRecordsParityStatusForChargeWithDifferentStatusInLedger() {
        chargeEntity.getRefunds().add(aValidRefundEntity().build());
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(aValidLedgerTransaction().withStatus("started").build()));

        worker.execute(1L, OptionalLong.empty());

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.DATA_MISMATCH);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(ledgerService, times(1)).getTransaction(chargeEntity.getExternalId());
        verify(ledgerService, never()).getTransaction(chargeEntity.getRefunds().get(0).getExternalId());
        verify(stateTransitionQueue, times(1)).offer(any());
        verify(emittedEventDao, times(1)).recordEmission(any());
    }

    @Test
    public void executeEmitsEventAndRecordsEmissionWhenRefundDoesNotExist() {
        chargeEntity.getRefunds().add(aValidRefundEntity().build());
        chargeEntity.getRefunds().add(aValidRefundEntity().build());
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(any())).thenReturn(Optional.empty());
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(aValidLedgerTransaction().build()));

        worker.execute(1L, OptionalLong.empty());

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.MISSING_IN_LEDGER);
        verify(ledgerService, times(2)).getTransaction(any());
        verify(stateTransitionQueue, times(1)).offer(any());
        verify(emittedEventDao, times(1)).recordEmission(any());
    }

    @Test
    public void executeEmitsEventAndRecordsEmissionWhenRefundWithDifferentStatusInLedger() {
        chargeEntity.getRefunds().add(aValidRefundEntity().build());
        chargeEntity.getRefunds().add(aValidRefundEntity().build());
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(any())).thenReturn(Optional.empty());
        when(ledgerService.getTransaction(any())).thenReturn(Optional.of(
                aValidLedgerTransaction().withStatus("success").build()));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(aValidLedgerTransaction().build()));

        worker.execute(1L, OptionalLong.empty());

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.DATA_MISMATCH);
        verify(ledgerService, times(2)).getTransaction(any());
        verify(stateTransitionQueue, times(1)).offer(any());
        verify(emittedEventDao, times(1)).recordEmission(any());
    }

    @Test
    public void executeEmitsEventAndRecordsEmission() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.empty());

        worker.execute(1L, OptionalLong.empty());

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.MISSING_IN_LEDGER);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(stateTransitionQueue, times(1)).offer(any());
        verify(emittedEventDao, times(1)).recordEmission(any());
    }

    @Test
    public void executeShouldEmitEventIfEmittedPreviously() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.empty());

        worker.execute(1L, OptionalLong.of(1L));

        verify(chargeService, times(1)).updateChargeParityStatus(chargeEntity.getExternalId(), ParityCheckStatus.MISSING_IN_LEDGER);
        verify(ledgerService, times(1)).getTransaction(any());
        verify(stateTransitionQueue, times(1)).offer(any());
        verify(emittedEventDao, times(1)).recordEmission(any());
    }
}
