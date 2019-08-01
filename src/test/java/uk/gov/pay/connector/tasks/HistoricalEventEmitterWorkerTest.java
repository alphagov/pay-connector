package uk.gov.pay.connector.tasks;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.queue.QueueException;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

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
    ChargeEventDao chargeEventDao;
    @Mock
    ChargeDao chargeDao;
    @Mock
    EmittedEventDao emittedEventDao;
    @Mock
    EventQueue eventQueue;

    @InjectMocks
    HistoricalEventEmitterWorker worker;
    private ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();

    @Test
    public void execute_emitsEventAndRecordsEmission() throws QueueException {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(chargeEventDao.findEventsByChargeId(chargeEntity.getId())).thenReturn(List.of());

        worker.execute(1L, OptionalLong.empty());

        verify(eventQueue).emitEvent(PaymentCreated.from(chargeEntity));
        verify(emittedEventDao).recordEmission(PaymentCreated.from(chargeEntity));
        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void iteratesThroughSpecifiedRange() throws QueueException {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));
        when(chargeEventDao.findEventsByChargeId(chargeEntity.getId())).thenReturn(List.of());

        worker.execute(1L, OptionalLong.of(100L));

        verify(chargeDao, times(100)).findById(and(geq(1L), leq(100L)));
        verify(eventQueue, times(100)).emitEvent(any());
        verify(emittedEventDao, times(100)).recordEmission(any());
    }
}
