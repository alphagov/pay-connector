package uk.gov.pay.connector.events;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.EmittedEventSweepConfig;
import uk.gov.pay.connector.app.config.EventEmitterConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.pact.RefundHistoryEntityFixture;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.tasks.HistoricalEventEmitter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.events.EmittedEventFixture.anEmittedEventEntity;

@ExtendWith(MockitoExtension.class)
class EmittedEventsBackfillServiceTest {
    @Mock
    private EmittedEventDao emittedEventDao;
    @Mock
    private ChargeService chargeService;
    @Mock
    private RefundDao refundDao;
    @Mock
    private ChargeDao chargeDao;
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

    @BeforeEach
    void setUp() {
        var sweepConfig = mock(EmittedEventSweepConfig.class);
        when(sweepConfig.getNotEmittedEventMaxAgeInSeconds()).thenReturn(1800);

        EventEmitterConfig mockEventEmitterConfig = mock(EventEmitterConfig.class);
        when(mockEventEmitterConfig.getDefaultDoNotRetryEmittingEventUntilDurationInSeconds()).thenReturn(60L);

        when(connectorConfiguration.getEmittedEventSweepConfig()).thenReturn(sweepConfig);
        when(connectorConfiguration.getEventEmitterConfig()).thenReturn(mockEventEmitterConfig);
        Logger root = (Logger) LoggerFactory.getLogger(EmittedEventsBackfillService.class);
        mockAppender = mock(Appender.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
        HistoricalEventEmitter historicalEventEmitter = new HistoricalEventEmitter(emittedEventDao, refundDao,
                eventService, stateTransitionService, chargeService);
        emittedEventsBackfillService = new EmittedEventsBackfillService(emittedEventDao, chargeService, refundDao,
                historicalEventEmitter, connectorConfiguration);
        lenient().when(chargeService.findChargeByExternalId(any())).thenThrow(new ChargeNotFoundRuntimeException(""));
        chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .build();
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.ofInstant(chargeEntity.getCreatedDate(), ZoneOffset.UTC))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();
        chargeEntity.getEvents().add(chargeEventEntity);
        refundEntity = mock(RefundEntity.class);
    }

    @Test
    void logsMessageWhenNoEmittedEventsSatisfyingCriteria() {
        when(emittedEventDao.findNotEmittedEventMaxIdOlderThan(any(Instant.class), any())).thenReturn(Optional.empty());

        emittedEventsBackfillService.backfillNotEmittedEvents();

        verify(emittedEventDao, never()).findNotEmittedEventsOlderThan(any(Instant.class), anyInt(), anyLong(), eq(maxId), any());
        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.getFirst().getFormattedMessage(), is("Finished processing not emitted events [lastProcessedId=0, maxId=none]"));
    }

    @Test
    void backfillsEventsWhenEmittedPaymentEventSatisfyingCriteria() {
        var emittedEvent = anEmittedEventEntity().withResourceExternalId(chargeEntity.getExternalId()).build();
        when(emittedEventDao.findNotEmittedEventsOlderThan(any(Instant.class), anyInt(), eq(0L), eq(maxId), any())).thenReturn(List.of(emittedEvent));
        when(emittedEventDao.findNotEmittedEventMaxIdOlderThan(any(Instant.class), any())).thenReturn(Optional.of(maxId));
        doReturn(chargeEntity).when(chargeService).findChargeByExternalId(chargeEntity.getExternalId());

        emittedEventsBackfillService.backfillNotEmittedEvents();

        verify(emittedEventDao, times(1)).findNotEmittedEventsOlderThan(any(Instant.class), anyInt(), eq(0L), eq(maxId), any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), isNull());
        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.getFirst().getFormattedMessage(),
                is("Processing not emitted events [lastProcessedId=0, no.of.events=1, oldestDate=2019-09-20T10:00:00Z]"));
        assertThat(loggingEvents.get(1).getFormattedMessage(),
                is("Finished processing not emitted events [lastProcessedId=1, maxId=2]"));
    }

    @Test
    void backfillsEventsWhenEmittedRefundEventSatisfyingCriteria() {
        var emittedEvent = anEmittedEventEntity().withResourceType("refund")
                .withResourceExternalId(refundEntity.getExternalId()).build();
        var refundHistory = RefundHistoryEntityFixture
                .aValidRefundHistoryEntity()
                .withExternalId(refundEntity.getExternalId())
                .withChargeExternalId(chargeEntity.getExternalId())
                .build();
        when(emittedEventDao.findNotEmittedEventsOlderThan(any(Instant.class), anyInt(), eq(0L), eq(maxId), any())).thenReturn(List.of(emittedEvent));
        when(emittedEventDao.findNotEmittedEventMaxIdOlderThan(any(Instant.class), any())).thenReturn(Optional.of(maxId));
        when(refundDao.findByExternalId(refundEntity.getExternalId())).thenReturn(Optional.of(refundEntity));
        when(refundDao.getRefundHistoryByRefundExternalId(refundEntity.getExternalId())).thenReturn(List.of(refundHistory));
        doReturn(Optional.of(Charge.from(chargeEntity))).when(chargeService).findCharge(chargeEntity.getExternalId());
        chargeEntity.getEvents().clear();

        emittedEventsBackfillService.backfillNotEmittedEvents();

        verify(emittedEventDao, times(1)).findNotEmittedEventsOlderThan(any(Instant.class), anyInt(), eq(0L), eq(maxId), any());
        verify(stateTransitionService, times(1)).offerStateTransition(any(), any(), isNull());
        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.getFirst().getFormattedMessage(),
                is("Processing not emitted events [lastProcessedId=0, no.of.events=1, oldestDate=2019-09-20T10:00:00Z]"));
        assertThat(loggingEvents.get(1).getFormattedMessage(),
                is("Finished processing not emitted events [lastProcessedId=1, maxId=2]"));
    }

    @Test
    void backfillsEventsWhenEmittedEventsSatisfyingCriteria() {
        when(emittedEventDao.findNotEmittedEventMaxIdOlderThan(any(Instant.class), any())).thenReturn(Optional.of(maxId));
        var emittedPaymentEvent = anEmittedEventEntity().withResourceExternalId(chargeEntity.getExternalId()).build();
        var emittedRefundEvent = anEmittedEventEntity().withResourceType("refund").withId(2L)
                .withEventDate(Instant.parse("2019-09-20T09:00:00Z"))
                .withResourceExternalId(refundEntity.getExternalId())
                .build();
        var refundHistory = RefundHistoryEntityFixture
                .aValidRefundHistoryEntity()
                .withExternalId(refundEntity.getExternalId())
                .withChargeExternalId(chargeEntity.getExternalId())
                .build();
        when(emittedEventDao.findNotEmittedEventsOlderThan(any(Instant.class), anyInt(), eq(0L), eq(maxId), any()))
                .thenReturn(List.of(emittedPaymentEvent, emittedRefundEvent));
        doReturn(chargeEntity).when(chargeService).findChargeByExternalId(chargeEntity.getExternalId());
        when(refundDao.findByExternalId(refundEntity.getExternalId())).thenReturn(Optional.of(refundEntity));
        when(refundDao.getRefundHistoryByRefundExternalId(refundEntity.getExternalId())).thenReturn(List.of(refundHistory));
        doReturn(Optional.of(Charge.from(chargeEntity))).when(chargeService).findCharge(chargeEntity.getExternalId());

        emittedEventsBackfillService.backfillNotEmittedEvents();

        verify(emittedEventDao, times(1)).findNotEmittedEventsOlderThan(any(Instant.class), anyInt(), eq(0L), eq(maxId), any());
        // 2 events emitted for payment event and refund event
        verify(stateTransitionService, times(2)).offerStateTransition(any(), any(), isNull());
        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.getFirst().getFormattedMessage(), is("Processing not emitted events [lastProcessedId=0, no.of.events=2, oldestDate=2019-09-20T09:00:00Z]"));
        assertThat(loggingEvents.get(1).getFormattedMessage(), is("Finished processing not emitted events [lastProcessedId=2, maxId=2]"));
    }
}
