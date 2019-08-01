package uk.gov.pay.connector.tasks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.queue.QueueException;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.OptionalLong;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.AdditionalMatchers.leq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HistoricalEventEmitterWorkerTest {

    @Mock
    ChargeDao chargeDao;
    @Mock
    EmittedEventDao emittedEventDao;
    @Mock
    EventQueue eventQueue;

    @InjectMocks
    HistoricalEventEmitterWorker worker;
    ChargeEntity chargeEntity;

    @Before
    public void setUp() {
        chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
    }

    @Test
    public void execute_emitsEventAndRecordsEmission() throws QueueException {
        ChargeEventEntity firstChargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(chargeEntity.getCreatedDate())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();

        ChargeEventEntity secondChargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.SYSTEM_CANCELLED)
                .build();

        chargeEntity.getEvents().add(firstChargeEventEntity);
        chargeEntity.getEvents().add(secondChargeEventEntity);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        verify(eventQueue, times(2)).emitEvent(argument.capture());

        assertThat(argument.getAllValues().get(0).getEventType(), is("PAYMENT_CREATED"));
        assertThat(argument.getAllValues().get(0).getResourceType(), is(ResourceType.PAYMENT));
        assertThat(argument.getAllValues().get(0).getResourceExternalId(), is(chargeEntity.getExternalId()));
        assertThat(argument.getAllValues().get(0).getTimestamp(), is(firstChargeEventEntity.getUpdated()));

        assertThat(argument.getAllValues().get(1).getEventType(), is("CANCELLED_BY_EXTERNAL_SERVICE"));
        assertThat(argument.getAllValues().get(1).getResourceType(), is(ResourceType.PAYMENT));
        assertThat(argument.getAllValues().get(1).getResourceExternalId(), is(chargeEntity.getExternalId()));
        assertThat(argument.getAllValues().get(1).getTimestamp(), is(secondChargeEventEntity.getUpdated()));

        ArgumentCaptor<Event> daoArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(emittedEventDao, times(2)).recordEmission(daoArgumentCaptor.capture());
        assertThat(daoArgumentCaptor.getAllValues().get(0).getEventType(), is("PAYMENT_CREATED"));
        assertThat(daoArgumentCaptor.getAllValues().get(1).getEventType(), is("CANCELLED_BY_EXTERNAL_SERVICE"));

        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void execute_shouldNotProcessIfNoEventsFound() throws QueueException {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        verify(eventQueue, never()).emitEvent(any());
        verify(emittedEventDao, never()).recordEmission(any());
    }

    @Test
    public void iteratesThroughSpecifiedRange() throws QueueException {
        ChargeEventEntity firstChargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(chargeEntity.getCreatedDate())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();

        chargeEntity.getEvents().add(firstChargeEventEntity);
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.of(100L));

        verify(chargeDao, times(100)).findById(and(geq(1L), leq(100L)));
        verify(eventQueue, times(100)).emitEvent(any());
        verify(emittedEventDao, times(100)).recordEmission(any());
    }

    @Test
    public void execute_shouldNotEmitEvent_ifEmittedPreviously() throws QueueException {
        ChargeEventEntity firstChargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(chargeEntity.getCreatedDate())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();

        chargeEntity.getEvents().add(firstChargeEventEntity);
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any())).thenReturn(true);

        worker.execute(1L, OptionalLong.of(1L));

        verify(chargeDao, times(1)).findById(1L);
        verify(eventQueue, never()).emitEvent(any());
        verify(emittedEventDao, never()).recordEmission(any());
    }

    @Test
    public void execute_shouldIgnoreEventIfStateTransitionIsNotFound() throws QueueException {
        ChargeEventEntity firstChargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(chargeEntity.getCreatedDate())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();

        ChargeEventEntity secondChargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .build();

        chargeEntity.getEvents().add(firstChargeEventEntity);
        chargeEntity.getEvents().add(secondChargeEventEntity);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        verify(eventQueue, times(1)).emitEvent(argument.capture());

        assertThat(argument.getAllValues().get(0).getEventType(), is("PAYMENT_CREATED"));
        assertThat(argument.getAllValues().get(0).getResourceType(), is(ResourceType.PAYMENT));
        assertThat(argument.getAllValues().get(0).getResourceExternalId(), is(chargeEntity.getExternalId()));
        assertThat(argument.getAllValues().get(0).getTimestamp(), is(firstChargeEventEntity.getUpdated()));

        ArgumentCaptor<Event> daoArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(emittedEventDao, times(1)).recordEmission(daoArgumentCaptor.capture());
        assertThat(daoArgumentCaptor.getAllValues().get(0).getEventType(), is("PAYMENT_CREATED"));
    }
}
