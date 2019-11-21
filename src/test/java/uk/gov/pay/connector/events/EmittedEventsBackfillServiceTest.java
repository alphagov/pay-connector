package uk.gov.pay.connector.events;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.EmittedEventSweepConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.queue.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.events.EmittedEventFixture.anEmittedEventEntity;

@RunWith(MockitoJUnitRunner.class)
public class EmittedEventsBackfillServiceTest {
    @Mock
    private EmittedEventDao emittedEventDao;
    @Mock
    private ChargeService chargeService;
    @Mock
    private RefundDao refundDao;
    @Mock
    private EventService eventService;
    @Mock
    private StateTransitionService stateTransitionService;
    @Mock
    private ConnectorConfiguration connectorConfiguration;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private Appender<ILoggingEvent> mockAppender;
    private EmittedEventsBackfillService emittedEventsBackfillService;
    private ChargeEntity chargeEntity;
    private RefundEntity refundEntity;
    private Long maxId = 2L;

    @Before
    public void setUp() {
        var sweepConfig = mock(EmittedEventSweepConfig.class);
        when(sweepConfig.getNotEmittedEventMaxAgeInSeconds()).thenReturn(1800);
        when(connectorConfiguration.getEmittedEventSweepConfig()).thenReturn(sweepConfig);
        Logger root = (Logger) LoggerFactory.getLogger(EmittedEventsBackfillService.class);
        mockAppender = mock(Appender.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
        emittedEventsBackfillService = new EmittedEventsBackfillService(emittedEventDao, chargeService, refundDao,
                eventService, stateTransitionService, connectorConfiguration);
        chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .build();
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(chargeEntity.getCreatedDate())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();
        chargeEntity.getEvents().add(chargeEventEntity);
        refundEntity = mock(RefundEntity.class);
        when(refundEntity.getChargeEntity()).thenReturn(chargeEntity);
        when(emittedEventDao.findNotEmittedEventMaxIdOlderThan(any(ZonedDateTime.class), any())).thenReturn(Optional.of(maxId));
    }
    
    @Test
    public void logsMessageWhenNoEmittedEventsSatisfyingCriteria() {
        when(emittedEventDao.findNotEmittedEventMaxIdOlderThan(any(ZonedDateTime.class), any())).thenReturn(Optional.empty());
        
        emittedEventsBackfillService.backfillNotEmittedEvents();
        
        verify(emittedEventDao, never()).findNotEmittedEventsOlderThan(any(ZonedDateTime.class), anyInt(), anyLong(), eq(maxId), any());
        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.get(0).getFormattedMessage(), is("Finished processing not emitted events [lastProcessedId=0, maxId=none]"));
    }

    @Test
    public void backfillsEventsWhenEmittedPaymentEventSatisfyingCriteria() {
        var emittedEvent = anEmittedEventEntity().withResourceExternalId(chargeEntity.getExternalId()).build();
        when(emittedEventDao.findNotEmittedEventsOlderThan(any(ZonedDateTime.class), anyInt(), eq(0L), eq(maxId), any())).thenReturn(List.of(emittedEvent));
        when(chargeService.findChargeById(chargeEntity.getExternalId())).thenReturn(chargeEntity);

        emittedEventsBackfillService.backfillNotEmittedEvents();

        verify(emittedEventDao, times(1)).findNotEmittedEventsOlderThan(any(ZonedDateTime.class), anyInt(), eq(0L), eq(maxId), any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any());
        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.get(0).getFormattedMessage(), is("Processing not emitted events [lastProcessedId=0, no.of.events=1, oldestDate=2019-09-20T10:00Z]"));
        assertThat(loggingEvents.get(1).getFormattedMessage(), is("Finished processing not emitted events [lastProcessedId=1, maxId=2]"));
    }

    @Test
    public void backfillsEventsWhenEmittedRefundEventSatisfyingCriteria() {
        var emittedEvent = anEmittedEventEntity().withResourceType("refund")
                .withResourceExternalId(chargeEntity.getExternalId()).build();
        when(emittedEventDao.findNotEmittedEventsOlderThan(any(ZonedDateTime.class), anyInt(), eq(0L), eq(maxId), any())).thenReturn(List.of(emittedEvent));
        when(refundDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(refundEntity));

        emittedEventsBackfillService.backfillNotEmittedEvents();

        verify(emittedEventDao, times(1)).findNotEmittedEventsOlderThan(any(ZonedDateTime.class), anyInt(), eq(0L), eq(maxId), any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any());
        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.get(0).getFormattedMessage(), is("Processing not emitted events [lastProcessedId=0, no.of.events=1, oldestDate=2019-09-20T10:00Z]"));
        assertThat(loggingEvents.get(1).getFormattedMessage(), is("Finished processing not emitted events [lastProcessedId=1, maxId=2]"));
    }

    @Test
    public void backfillsEventsWhenEmittedEventsSatisfyingCriteria() {
        var emittedPaymentEvent = anEmittedEventEntity().withResourceExternalId(chargeEntity.getExternalId()).build();
        var emittedRefundEvent = anEmittedEventEntity().withResourceType("refund").withId(2L)
                .withEventDate(ZonedDateTime.parse("2019-09-20T09:00Z"))
                .withResourceExternalId(chargeEntity.getExternalId())
                .build();
        when(emittedEventDao.findNotEmittedEventsOlderThan(any(ZonedDateTime.class), anyInt(), eq(0L), eq(maxId), any())).thenReturn(List.of(emittedPaymentEvent, emittedRefundEvent));
        when(refundDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(refundEntity));
        when(chargeService.findChargeById(chargeEntity.getExternalId())).thenReturn(chargeEntity);

        emittedEventsBackfillService.backfillNotEmittedEvents();

        verify(emittedEventDao, times(1)).findNotEmittedEventsOlderThan(any(ZonedDateTime.class), anyInt(), eq(0L), eq(maxId), any());
        verify(stateTransitionService, times(2)).offerStateTransition(any(), any());
        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.get(0).getFormattedMessage(), is("Processing not emitted events [lastProcessedId=0, no.of.events=2, oldestDate=2019-09-20T09:00Z]"));
        assertThat(loggingEvents.get(1).getFormattedMessage(), is("Finished processing not emitted events [lastProcessedId=2, maxId=2]"));
    }
}
