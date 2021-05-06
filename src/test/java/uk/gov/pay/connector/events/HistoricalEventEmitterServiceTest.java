package uk.gov.pay.connector.events;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.AuthorisationSucceeded;
import uk.gov.pay.connector.events.model.charge.BackfillerRecreatedUserEmailCollected;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmed;
import uk.gov.pay.connector.events.model.charge.CaptureSubmitted;
import uk.gov.pay.connector.events.model.charge.GatewayRequires3dsAuthorisation;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.PaymentStarted;
import uk.gov.pay.connector.events.model.refund.RefundCreatedByService;
import uk.gov.pay.connector.events.model.refund.RefundSucceeded;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.pact.RefundHistoryEntityFixture;
import uk.gov.pay.connector.queue.statetransition.StateTransition;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZoneOffset;
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
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HistoricalEventEmitterServiceTest {

    @Mock
    ChargeDao chargeDao;
    @Mock
    ChargeService chargeService;
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

    HistoricalEventEmitterService historicalEventEmitterService;
    private ChargeEntity chargeEntity;
    private ConnectorConfiguration connectorConfiguration;

    @Before
    public void setUp() {
        connectorConfiguration = new ConnectorConfiguration();
        historicalEventEmitterService = new HistoricalEventEmitterService(chargeDao, refundDao, chargeEventDao, emittedEventDao,
                stateTransitionService, eventService, chargeService, connectorConfiguration);
        CardDetailsEntity cardDetails = mock(CardDetailsEntity.class);
        when(cardDetails.getLastDigitsCardNumber()).thenReturn(LastDigitsCardNumber.of("1234"));
        chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withCardDetails(cardDetails)
                .build();
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.ofInstant(chargeEntity.getCreatedDate(), ZoneOffset.UTC))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();
        chargeEntity.getEvents().add(chargeEventEntity);
    }

    @Test
    public void executeEmitsEventAndRecordsEmission() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(1)).offerStateTransition(argument.capture(),
                any(), isNotNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(PaymentCreated.class));

        verify(chargeDao, never()).findById(2L);
    }

    @Test
    public void executeShouldNotProcessIfNoEventsFound() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        chargeEntity.getEvents().clear();

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(stateTransitionService, never()).offerStateTransition(any(), any(), any());
    }

    @Test
    public void iteratesThroughSpecifiedRange() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.of(100L), 1L);

        verify(chargeDao, times(100)).findById(and(geq(1L), leq(100L)));
        verify(stateTransitionService, times(100)).offerStateTransition(any(), any(), isNotNull());
    }

    @Test
    public void executeShouldNotEmitEventIfEmittedPreviously() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any())).thenReturn(true);

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.of(1L), 1L);

        verify(chargeDao, times(1)).findById(1L);
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), any());
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

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(1)).offerStateTransition(argument.capture(),
                any(), isNotNull());

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

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, times(1)).emitAndRecordEvent(any(PaymentDetailsEntered.class), isNotNull());
    }

    @Test
    public void executeShouldEmitUserEmailCollectedEventWhenEnteringCardDetailsStateExists() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .build();

        chargeEntity.getEvents().add(firstEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, times(1)).emitAndRecordEvent(any(BackfillerRecreatedUserEmailCollected.class), isNotNull());
    }

    @Test
    public void executeShouldNotEmitPaymentDetailsEnteredEventWithTerminalAuthenticationStateForNotificationPayment() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.PAYMENT_NOTIFICATION_CREATED)
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

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, never()).emitAndRecordEvent(any(PaymentDetailsEntered.class), any());
    }

    @Test
    public void executeShouldNotEmitManualEventsWithNoTerminalAuthenticationState() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CREATED)
                .build();

        chargeEntity.getEvents().add(firstEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verifyNoInteractions(eventService);
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

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(2)).offerStateTransition(argument.capture(),
                any(), isNotNull());

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
        chargeEntity.setEmail(null);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(1)).offerStateTransition(argument.capture(),
                any(AuthorisationSucceeded.class), isNotNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(AuthorisationSucceeded.class));

        ArgumentCaptor<Event> daoArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventService, times(1)).emitAndRecordEvent(daoArgumentCaptor.capture(), isNotNull()); // additional events - paymentDetailsEnteredEvent
        assertThat(daoArgumentCaptor.getAllValues().get(0).getEventType(), is("PAYMENT_DETAILS_ENTERED"));
    }

    @Test
    public void executeShouldOfferRefundEventsWithRefundHistory() {
        RefundHistory refundHistory = RefundHistoryEntityFixture
                .aValidRefundHistoryEntity()
                .withChargeExternalId(chargeEntity.getExternalId())
                .withStatus(RefundStatus.CREATED.toString())
                .build();

        chargeEntity.getEvents().clear();
        when(refundDao.searchAllHistoryByChargeExternalId(chargeEntity.getExternalId())).thenReturn(List.of(refundHistory));
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(chargeService.findCharge(chargeEntity.getExternalId())).thenReturn(Optional.of(Charge.from(chargeEntity)));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService).offerStateTransition(argument.capture(), any(RefundCreatedByService.class), isNotNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(RefundCreatedByService.class));

        verify(emittedEventDao, atMostOnce()).recordEmission(any(), isNotNull());
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

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(2)).offerStateTransition(argument.capture(), any(), isNotNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(GatewayRequires3dsAuthorisation.class));
        assertThat(argument.getAllValues().get(1).getStateTransitionEventClass(), is(AuthorisationSucceeded.class));

        ArgumentCaptor<Event> daoArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventService, times(2)).emitAndRecordEvent(daoArgumentCaptor.capture(), isNotNull());
        assertThat(daoArgumentCaptor.getAllValues().get(0).getEventType(), is("PAYMENT_DETAILS_ENTERED"));
        assertThat(daoArgumentCaptor.getAllValues().get(1).getEventType(), is("BACKFILLER_RECREATED_USER_EMAIL_COLLECTED"));
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

        historicalEventEmitterService.emitHistoricEventsByDate(eventDate, eventDate, 1L);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(2)).offerStateTransition(argument.capture(), any(), isNotNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(PaymentCreated.class));
        assertThat(argument.getAllValues().get(1).getStateTransitionEventClass(), is(PaymentStarted.class));
    }

    @Test
    public void executeForDateRange_ShouldEmitAllRefundsEventsOfAChargeWithRefundEventWithinDateRange() {
        ZonedDateTime eventDate = ZonedDateTime.parse("2016-01-01T00:00:00Z");

        RefundHistory refundHistory = getRefundHistoryEntity(chargeEntity, RefundStatus.CREATED);
        RefundHistory refundHistory2 = getRefundHistoryEntity(chargeEntity, RefundStatus.REFUNDED);

        chargeEntity.getEvents().clear();
        when(chargeService.findCharge(chargeEntity.getExternalId())).thenReturn(Optional.of(Charge.from(chargeEntity)));
        when(refundDao.getRefundHistoryByDateRange(eventDate, eventDate, 1, 100)).thenReturn(List.of(refundHistory));
        when(refundDao.searchAllHistoryByChargeExternalId(chargeEntity.getExternalId())).thenReturn(List.of(refundHistory, refundHistory2));

        historicalEventEmitterService.emitHistoricEventsByDate(eventDate, eventDate, 1L);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(2)).offerStateTransition(argument.capture(), any(), isNotNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(RefundCreatedByService.class));
        assertThat(argument.getAllValues().get(1).getStateTransitionEventClass(), is(RefundSucceeded.class));
    }

    @Test
    public void emitHistoricEventsById_shouldNotProcessIfRecordDoesNotExist() {
        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verifyNoInteractions(emittedEventDao);
        verify(refundDao, never()).searchAllHistoryByChargeExternalId(any());
    }

    @Test
    public void executeForRefundsOnly_shouldEmitRefundEvents() {
        RefundHistory refundHistory = getRefundHistoryEntity(chargeEntity, RefundStatus.CREATED);
        RefundHistory refundHistory2 = getRefundHistoryEntity(chargeEntity, RefundStatus.REFUNDED);
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().withId(1L).
                withChargeExternalId(chargeEntity.getExternalId()).build();

        when(chargeService.findCharge(chargeEntity.getExternalId())).thenReturn(Optional.of(Charge.from(chargeEntity)));
        when(refundDao.findById(1L)).thenReturn(Optional.of(refundEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any())).thenReturn(false);

        when(refundDao.searchAllHistoryByChargeExternalId(chargeEntity.getExternalId())).thenReturn(List.of(refundHistory, refundHistory2));

        historicalEventEmitterService.emitRefundEventsOnlyById(1L, OptionalLong.of(1L), 1L);

        ArgumentCaptor<StateTransition> argument = ArgumentCaptor.forClass(StateTransition.class);
        verify(stateTransitionService, times(2)).offerStateTransition(argument.capture(), any(), isNotNull());

        assertThat(argument.getAllValues().get(0).getStateTransitionEventClass(), is(RefundCreatedByService.class));
        assertThat(argument.getAllValues().get(1).getStateTransitionEventClass(), is(RefundSucceeded.class));

        verify(emittedEventDao, atMostOnce()).recordEmission(any(Event.class), isNotNull());
    }

    private RefundHistory getRefundHistoryEntity(ChargeEntity chargeEntity, RefundStatus refundStatus) {
        return RefundHistoryEntityFixture
                .aValidRefundHistoryEntity()
                .withChargeExternalId(chargeEntity.getExternalId())
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
