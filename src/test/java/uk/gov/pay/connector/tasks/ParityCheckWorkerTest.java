package uk.gov.pay.connector.tasks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.paritycheck.LedgerService;
import uk.gov.pay.connector.paritycheck.LedgerTransaction;
import uk.gov.pay.connector.queue.StateTransition;
import uk.gov.pay.connector.queue.StateTransitionQueue;
import uk.gov.pay.connector.refund.dao.RefundDao;

import java.util.Optional;
import java.util.OptionalLong;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ParityCheckWorkerTest {
    @Mock
    ChargeDao chargeDao;
    @Mock
    ChargeEventDao chargeEventDao;
    @Mock
    EmittedEventDao emittedEventDao;
    @Mock
    StateTransitionQueue stateTransitionQueue;
    @Mock
    EventQueue eventQueue;
    @Mock
    RefundDao refundDao;
    @Mock
    LedgerService ledgerService;

    ParityCheckWorker worker;
    private ChargeEntity chargeEntity;

    @Before
    public void setUp() {
        worker = new ParityCheckWorker(chargeDao, ledgerService, emittedEventDao, stateTransitionQueue, eventQueue, refundDao);
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
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(new LedgerTransaction()));

        worker.execute(1L, OptionalLong.empty());

        assertThat(chargeEntity.getParityCheckStatus(), is(ParityCheckStatus.EXISTS_IN_LEDGER));
        verify(stateTransitionQueue, never()).offer(any());
        verify(emittedEventDao, never()).recordEmission(any());
        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void executeEmitsEventAndRecordsEmission() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.empty());

        worker.execute(1L, OptionalLong.empty());

        assertThat(chargeEntity.getParityCheckStatus(), is(ParityCheckStatus.MISSING_IN_LEDGER));
        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionQueue, times(1)).offer(argument.capture());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(PaymentCreated.class));

        ArgumentCaptor<Event> daoArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(emittedEventDao, times(1)).recordEmission(daoArgumentCaptor.capture());
        assertThat(daoArgumentCaptor.getAllValues().get(0).getEventType(), is("PAYMENT_CREATED"));

        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void executeShouldEmitEventIfEmittedPreviously() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any())).thenReturn(true);
        when(ledgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.empty());

        worker.execute(1L, OptionalLong.of(1L));

        assertThat(chargeEntity.getParityCheckStatus(), is(ParityCheckStatus.MISSING_IN_LEDGER));
        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionQueue, times(1)).offer(argument.capture());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(PaymentCreated.class));

        ArgumentCaptor<Event> daoArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(emittedEventDao, times(1)).recordEmission(daoArgumentCaptor.capture());
        assertThat(daoArgumentCaptor.getAllValues().get(0).getEventType(), is("PAYMENT_CREATED"));
    }
}
