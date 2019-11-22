package uk.gov.pay.connector.tasks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.AuthorisationSucceeded;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmed;
import uk.gov.pay.connector.events.model.charge.CaptureSubmitted;
import uk.gov.pay.connector.events.model.charge.GatewayRequires3dsAuthorisation;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.PaymentStarted;
import uk.gov.pay.connector.events.model.refund.RefundCreatedByService;
import uk.gov.pay.connector.events.model.refund.RefundSucceeded;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.pact.RefundHistoryEntityFixture;
import uk.gov.pay.connector.queue.StateTransition;
import uk.gov.pay.connector.queue.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.AdditionalMatchers.leq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atMostOnce;
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
    ChargeEventDao chargeEventDao;
    @Mock
    EmittedEventDao emittedEventDao;
    @Mock
    StateTransitionService stateTransitionService;
    @Mock
    EventService eventService;
    @Mock
    RefundDao refundDao;

    HistoricalEventEmitter historicalEventEmitter;
    HistoricalEventEmitterWorker worker;
    private ChargeEntity chargeEntity;

    @Before
    public void setUp() {
        historicalEventEmitter = new HistoricalEventEmitter(emittedEventDao, refundDao, eventService, stateTransitionService);
        worker = new HistoricalEventEmitterWorker(chargeDao, refundDao, chargeEventDao, historicalEventEmitter);
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
        verify(stateTransitionService, times(1)).offerStateTransition(argument.capture(), any(), isNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(PaymentCreated.class));

        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void executeShouldNotProcessIfNoEventsFound() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        chargeEntity.getEvents().clear();

        worker.execute(1L, OptionalLong.empty());

        verify(stateTransitionService, never()).offerStateTransition(any(), any(), isNull());
    }

    @Test
    public void iteratesThroughSpecifiedRange() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.of(100L));

        verify(chargeDao, times(100)).findById(and(geq(1L), leq(100L)));
        verify(stateTransitionService, times(100)).offerStateTransition(any(), any(), isNull());
    }

    @Test
    public void executeShouldNotEmitEventIfEmittedPreviously() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any())).thenReturn(true);

        worker.execute(1L, OptionalLong.of(1L));

        verify(chargeDao, times(1)).findById(1L);
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), isNull());
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
        verify(stateTransitionService, times(1)).offerStateTransition(argument.capture(), any(), isNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(PaymentCreated.class));
    }

    @Test
    public void executeShouldEmitManualEventsWithTerminalAuthenticationState() {
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

        verify(eventService, times(1)).emitAndRecordEvent(any(PaymentDetailsEntered.class), isNull());
    }

    @Test
    public void executeShouldNotEmitManualEventsWithNoTerminalAuthenticationState() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .build();

        chargeEntity.getEvents().add(firstEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        verifyZeroInteractions(eventService);
    }

    @Test
    public void executeShouldOfferOutOfOrderCaptureStatesInOrder() {
        ChargeEntity chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .build();
        ChargeEventEntity authSuccessEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().minusDays(10))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .build();
        ChargeEventEntity capturedEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().minusSeconds(2))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CAPTURED)
                .build();

        ChargeEventEntity captureSubmittedEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CAPTURE_SUBMITTED)
                .build();

        chargeEntity.getEvents().clear();
        chargeEntity.getEvents().add(capturedEvent);
        chargeEntity.getEvents().add(authSuccessEvent);
        chargeEntity.getEvents().add(captureSubmittedEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(2)).offerStateTransition(argument.capture(), any(), isNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(CaptureSubmitted.class));
        assertThat(argument.getAllValues().get(1).getStateTransitionEventClass(), is(CaptureConfirmed.class));
    }

    @Test
    public void executeShouldOfferEventsWithIntermediateState() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .build();

        ChargeEventEntity secondEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .build();

        chargeEntity.getEvents().clear();
        chargeEntity.getEvents().add(firstEvent);
        chargeEntity.getEvents().add(secondEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(1)).offerStateTransition(argument.capture(),
                any(AuthorisationSucceeded.class), isNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(AuthorisationSucceeded.class));

        ArgumentCaptor<Event> daoArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventService, times(1)).emitAndRecordEvent(daoArgumentCaptor.capture(), isNull()); // additional event - paymentDetailsEnteredEvent
        assertThat(daoArgumentCaptor.getAllValues().get(0).getEventType(), is("PAYMENT_DETAILS_ENTERED"));
    }

    @Test
    public void executeShouldOfferRefundEventsWithRefundHistory() {
        RefundHistory refundHistory = RefundHistoryEntityFixture
                .aValidRefundHistoryEntity()
                .withChargeExternalId(chargeEntity.getExternalId())
                .withChargeId(chargeEntity.getId())
                .withStatus(RefundStatus.CREATED.toString())
                .build();

        chargeEntity.getEvents().clear();
        when(refundDao.searchAllHistoryByChargeId(chargeEntity.getId())).thenReturn(List.of(refundHistory));
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService).offerStateTransition(argument.capture(), any(RefundCreatedByService.class), isNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(RefundCreatedByService.class));

        verify(emittedEventDao, atMostOnce()).recordEmission(any(), isNull());
    }

    @Test
    public void shouldEmitPaymentDetailsEnteredOnlyOnce_IfChargeEventsContainsBothAuth3DSRequiredAndAuthSuccessEvents() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .build();

        ChargeEventEntity secondEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.AUTHORISATION_3DS_REQUIRED)
                .build();

        ChargeEventEntity thirdEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .build();

        chargeEntity.getEvents().clear();
        chargeEntity.getEvents().add(firstEvent);
        chargeEntity.getEvents().add(secondEvent);
        chargeEntity.getEvents().add(thirdEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        worker.execute(1L, OptionalLong.empty());

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(2)).offerStateTransition(argument.capture(), any(), isNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(GatewayRequires3dsAuthorisation.class));
        assertThat(argument.getAllValues().get(1).getStateTransitionEventClass(), is(AuthorisationSucceeded.class));

        ArgumentCaptor<Event> daoArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventService, times(1)).emitAndRecordEvent(daoArgumentCaptor.capture(), isNull());
        assertThat(daoArgumentCaptor.getAllValues().get(0).getEventType(), is("PAYMENT_DETAILS_ENTERED"));
    }

    @Test
    public void executeForDateRange_ShouldEmitAllEventsOfAChargeWithEventWithinDateRange() {
        ZonedDateTime eventDate = ZonedDateTime.parse("2016-01-01T00:00:00Z");

        ChargeEventEntity firstEvent = getChargeEventEntity(chargeEntity, ChargeStatus.CREATED, eventDate);
        ChargeEventEntity secondEvent = getChargeEventEntity(chargeEntity, ChargeStatus.ENTERING_CARD_DETAILS, eventDate);
        List<ChargeEventEntity> chargeEventEntities = List.of(firstEvent);

        chargeEntity.getEvents().clear();
        chargeEntity.getEvents().add(firstEvent);
        chargeEntity.getEvents().add(secondEvent);

        when(chargeDao.findById(any())).thenReturn(Optional.of(chargeEntity));
        when(chargeEventDao.findChargeEvents(eventDate, eventDate, 1, 100)).thenReturn(chargeEventEntities);

        worker.executeForDateRange(eventDate, eventDate);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(2)).offerStateTransition(argument.capture(), any(), isNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(PaymentCreated.class));
        assertThat(argument.getAllValues().get(1).getStateTransitionEventClass(), is(PaymentStarted.class));
    }

    @Test
    public void executeForDateRange_ShouldEmitAllRefundsEventsOfAChargeWithRefundEventWithinDateRange() {
        ZonedDateTime eventDate = ZonedDateTime.parse("2016-01-01T00:00:00Z");

        RefundHistory refundHistory = getRefundHistoryEntity(chargeEntity, RefundStatus.CREATED);
        RefundHistory refundHistory2 = getRefundHistoryEntity(chargeEntity, RefundStatus.REFUNDED);

        chargeEntity.getEvents().clear();
        when(chargeDao.findById(any())).thenReturn(Optional.of(chargeEntity));
        when(refundDao.getRefundHistoryByDateRange(eventDate, eventDate, 1, 100)).thenReturn(List.of(refundHistory));
        when(refundDao.searchAllHistoryByChargeId(chargeEntity.getId())).thenReturn(List.of(refundHistory, refundHistory2));

        worker.executeForDateRange(eventDate, eventDate);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(2)).offerStateTransition(argument.capture(), any(), isNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(RefundCreatedByService.class));
        assertThat(argument.getAllValues().get(1).getStateTransitionEventClass(), is(RefundSucceeded.class));
    }

    private RefundHistory getRefundHistoryEntity(ChargeEntity chargeEntity, RefundStatus refundStatus) {
        return RefundHistoryEntityFixture
                .aValidRefundHistoryEntity()
                .withChargeExternalId(chargeEntity.getExternalId())
                .withChargeId(chargeEntity.getId())
                .withStatus(refundStatus.toString())
                .build();
    }

    private ChargeEventEntity getChargeEventEntity(ChargeEntity chargeEntity, ChargeStatus status,
                                                   ZonedDateTime eventDate) {
        return ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(eventDate)
                .withCharge(chargeEntity)
                .withChargeStatus(status)
                .build();
    }
}
