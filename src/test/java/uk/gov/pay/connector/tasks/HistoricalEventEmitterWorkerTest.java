package uk.gov.pay.connector.tasks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.StateTransition;
import uk.gov.pay.connector.queue.StateTransitionQueue;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.OptionalLong;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.AdditionalMatchers.leq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HistoricalEventEmitterWorkerTest {

    @Mock
    ChargeDao chargeDao;
    @Mock
    EmittedEventDao emittedEventDao;
    @Mock
    StateTransitionQueue stateTransitionQueue;
    @Mock
    EventQueue eventQueue;

    @InjectMocks
    HistoricalEventEmitterWorker worker;
    private ChargeEntity chargeEntity;

    @Before
    public void setUp() {
        CardDetailsEntity cardDetails = mock(CardDetailsEntity.class);
        when(cardDetails.getLastDigitsCardNumber()).thenReturn(LastDigitsCardNumber.of("1234"));
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
    public void executeEmitsEventAndRecordsEmission() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionQueue, times(1)).offer(argument.capture());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(PaymentCreated.class));

        ArgumentCaptor<Event> daoArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(emittedEventDao, times(1)).recordEmission(daoArgumentCaptor.capture());
        assertThat(daoArgumentCaptor.getAllValues().get(0).getEventType(), is("PAYMENT_CREATED"));

        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void executeShouldNotProcessIfNoEventsFound() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        chargeEntity.getEvents().clear();

        worker.execute(1L, OptionalLong.empty());

        verify(stateTransitionQueue, never()).offer(any());
        verify(emittedEventDao, never()).recordEmission(any());
    }

    @Test
    public void iteratesThroughSpecifiedRange() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.of(100L));

        verify(chargeDao, times(100)).findById(and(geq(1L), leq(100L)));
        verify(stateTransitionQueue, times(100)).offer(any());
        verify(emittedEventDao, times(100)).recordEmission(any());
    }

    @Test
    public void executeShouldNotEmitEventIfEmittedPreviously() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any())).thenReturn(true);

        worker.execute(1L, OptionalLong.of(1L));

        verify(chargeDao, times(1)).findById(1L);
        verify(stateTransitionQueue, never()).offer(any());
        verify(emittedEventDao, never()).recordEmission(any());
    }

    @Test
    public void executeShouldIgnoreEventIfStateTransitionIsNotFound() {
        ChargeEventEntity secondChargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CAPTURED)
                .build();

        chargeEntity.getEvents().add(secondChargeEventEntity);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionQueue, times(1)).offer(argument.capture());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(PaymentCreated.class));

        ArgumentCaptor<Event> daoArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(emittedEventDao, times(1)).recordEmission(daoArgumentCaptor.capture());
        assertThat(daoArgumentCaptor.getAllValues().get(0).getEventType(), is("PAYMENT_CREATED"));
    }

    @Test
    public void executeShouldEmitManualEventsWithTerminalAuthenticationState() throws QueueException {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .build();

        ChargeEventEntity secondEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .build();

        chargeEntity.getEvents().add(firstEvent);
        chargeEntity.getEvents().add(secondEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        verify(eventQueue).emitEvent(any(PaymentDetailsEntered.class));
    }

    @Test public void executeShouldNotEmitManualEventsWithNoTerminalAuthenticationState() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .build();

        chargeEntity.getEvents().add(firstEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        verifyZeroInteractions(eventQueue);
    }
}
