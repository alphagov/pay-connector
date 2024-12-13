package uk.gov.pay.connector.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.eventdetails.charge.Gateway3dsExemptionResultObtainedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.Requested3dsExemptionEventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.AuthorisationSucceeded;
import uk.gov.pay.connector.events.model.charge.BackfillerGatewayTransactionIdSet;
import uk.gov.pay.connector.events.model.charge.BackfillerRecreatedUserEmailCollected;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmed;
import uk.gov.pay.connector.events.model.charge.CaptureSubmitted;
import uk.gov.pay.connector.events.model.charge.FeeIncurredEvent;
import uk.gov.pay.connector.events.model.charge.Gateway3dsExemptionResultObtained;
import uk.gov.pay.connector.events.model.charge.Gateway3dsInfoObtained;
import uk.gov.pay.connector.events.model.charge.GatewayDoesNotRequire3dsAuthorisation;
import uk.gov.pay.connector.events.model.charge.GatewayRequires3dsAuthorisation;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsSubmittedByAPI;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsTakenFromPaymentInstrument;
import uk.gov.pay.connector.events.model.charge.PaymentStarted;
import uk.gov.pay.connector.events.model.charge.Requested3dsExemption;
import uk.gov.pay.connector.events.model.refund.RefundCreatedByService;
import uk.gov.pay.connector.events.model.refund.RefundSucceeded;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.pact.RefundHistoryEntityFixture;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.queue.statetransition.StateTransition;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
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
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_REJECTED;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_HONOURED;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_NOT_REQUESTED;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_OUT_OF_SCOPE;
import static uk.gov.service.payments.commons.model.AuthorisationMode.AGREEMENT;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;
import static uk.gov.pay.connector.charge.model.domain.Exemption3dsType.OPTIMISED;
import static uk.gov.pay.connector.charge.model.domain.Exemption3dsType.CORPORATE;

@ExtendWith(MockitoExtension.class)
class HistoricalEventEmitterServiceTest {

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

    @BeforeEach
    void setUp() {
        connectorConfiguration = new ConnectorConfiguration();
        historicalEventEmitterService = new HistoricalEventEmitterService(chargeDao, refundDao, chargeEventDao, emittedEventDao,
                stateTransitionService, eventService, chargeService, connectorConfiguration);
        CardDetailsEntity cardDetails = mock(CardDetailsEntity.class);
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
    void executeEmitsEventAndRecordsEmission() {
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
    void executeShouldNotProcessIfNoEventsFound() {
        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        chargeEntity.getEvents().clear();

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(stateTransitionService, never()).offerStateTransition(any(), any(), any());
    }

    @Test
    void iteratesThroughSpecifiedRange() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.of(100L), 1L);

        verify(chargeDao, times(100)).findById(and(geq(1L), leq(100L)));
        verify(stateTransitionService, times(100)).offerStateTransition(any(), any(), isNotNull());
    }

    @Test
    void executeShouldNotEmitEventIfEmittedPreviously() {
        when(chargeDao.findById((any()))).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any())).thenReturn(true);

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.of(1L), 1L);

        verify(chargeDao, times(1)).findById(1L);
        verify(stateTransitionService, never()).offerStateTransition(any(), any(), any());
    }

    @Test
    void executeShouldIgnoreEventIfStateTransitionIsNotFound() {
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
    void executeShouldEmitManualEventsWithTerminalAuthenticationState() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .build();

        ChargeEventEntity secondEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_SUCCESS)
                .build();

        chargeEntity.getEvents().add(firstEvent);
        chargeEntity.getEvents().add(secondEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, times(1)).emitAndRecordEvent(any(PaymentDetailsEntered.class), isNotNull());
    }

    @Test
    void executeShouldEmitPaymentSubmittedByApiEventForMotoApiCharges() {
        chargeEntity.setAuthorisationMode(MOTO_API);
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("123456"), LastDigitsCardNumber.of("1258"),
                "Mr. Pay Mc Payment", CardExpiryDate.valueOf("03/09"), "VISA", null, null);
        chargeEntity.setCardDetails(cardDetailsEntity);

        ChargeEventEntity authSuccessEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_SUCCESS)
                .build();

        chargeEntity.getEvents().add(authSuccessEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService).emitAndRecordEvent(any(PaymentDetailsSubmittedByAPI.class), isNotNull());
    }

    @Test
    void executeShouldEmitPaymentDetailsTakenFromPaymentInstrumentEventForAgreementCharges() {
        chargeEntity.setAuthorisationMode(AGREEMENT);
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("123456"), LastDigitsCardNumber.of("1258"),
                "Mr. Pay Mc Payment", CardExpiryDate.valueOf("03/09"), "VISA", null, null);
        chargeEntity.setCardDetails(cardDetailsEntity);

        ChargeEventEntity authSuccessEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_SUCCESS)
                .build();

        chargeEntity.getEvents().add(authSuccessEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService).emitAndRecordEvent(any(PaymentDetailsTakenFromPaymentInstrument.class), isNotNull());
    }

    @Test
    void executeShouldEmitUserEmailCollectedEventWhenEnteringCardDetailsStateExists() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .build();

        chargeEntity.getEvents().add(firstEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, times(1)).emitAndRecordEvent(any(BackfillerRecreatedUserEmailCollected.class), isNotNull());
    }

    @Test
    void executeShouldEmitGatewayTransactionIdSetEvent() {
        chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayTransactionId("gateway-tx-id")
                .build();
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .build();

        chargeEntity.getEvents().add(firstEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService).emitAndRecordEvent(any(BackfillerGatewayTransactionIdSet.class), isNotNull());
    }

    @Test
    void executeShouldNotEmitGatewayTransactionIdSetEventWhenThereIsNoGatewayTransactionIdOnCharge() {
        chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayTransactionId(null)
                .build();
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .build();
        chargeEntity.getEvents().add(firstEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, never()).emitAndRecordEvent(any(BackfillerGatewayTransactionIdSet.class), isNotNull());
    }

    @Test
    void executeShouldNotEmitGatewayTransactionIdSetEventIfChargeHasAuthorisationEvent() {
        chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayTransactionId("gateway-tx-id")
                .build();
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .build();
        ChargeEventEntity authorisedEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_SUCCESS)
                .build();
        chargeEntity.getEvents().add(firstEvent);
        chargeEntity.getEvents().add(authorisedEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, never()).emitAndRecordEvent(any(BackfillerGatewayTransactionIdSet.class), isNotNull());
    }

    @Test
    void executeShouldNotEmitGatewayTransactionIdSetEventIfChargeIsATelephonePaymentNotification() {
        chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayTransactionId("gateway-tx-id")
                .build();
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(CREATED)
                .build();
        ChargeEventEntity paymentNotificationEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(PAYMENT_NOTIFICATION_CREATED)
                .build();
        chargeEntity.getEvents().add(firstEvent);
        chargeEntity.getEvents().add(paymentNotificationEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, never()).emitAndRecordEvent(any(BackfillerGatewayTransactionIdSet.class), isNotNull());
    }

    @Test
    void executeShouldNotEmitPaymentDetailsEnteredEventWithTerminalAuthenticationStateForNotificationPayment() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(1))
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.PAYMENT_NOTIFICATION_CREATED)
                .build();

        ChargeEventEntity secondEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_SUCCESS)
                .build();

        chargeEntity.getEvents().add(firstEvent);
        chargeEntity.getEvents().add(secondEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, never()).emitAndRecordEvent(any(PaymentDetailsEntered.class), any());
    }

    @Test
    void executeShouldNotEmitManualEventsWithNoTerminalAuthenticationState() {
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
    void executeShouldOfferOutOfOrderCaptureStatesInOrder() {
        ChargeEntity chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .build();
        ChargeEventEntity authSuccessEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().minusDays(10))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_SUCCESS)
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
    void executeShouldEmitFeeIncurredEvent() {
        ChargeEntity chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withFee(Fee.of(FeeType.TRANSACTION, 5L))
                .withFee(Fee.of(FeeType.RADAR, 2L))
                .build();

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any(FeeIncurredEvent.class))).thenReturn(false);

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, times(1)).emitAndRecordEvent(any(FeeIncurredEvent.class), isNotNull());
    }

    @Test
    void executeShouldNotEmitFeeIncurredEventWhenFeeTypeIsNull() {
        ChargeEntity chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withFee(Fee.of(null, 5L))
                .build();

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, never()).emitAndRecordEvent(any(FeeIncurredEvent.class), isNotNull());
    }

    @Test
    void executeShouldNotEmitFeeIncurredEventWhenEventHasBeenEmittedBefore() {
        ChargeEntity chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withFee(Fee.of(FeeType.TRANSACTION, 5L))
                .withFee(Fee.of(FeeType.RADAR, 2L))
                .build();

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any(FeeIncurredEvent.class))).thenReturn(true);

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, never()).emitAndRecordEvent(any(FeeIncurredEvent.class), isNotNull());
    }

    @Test
    void executeShouldEmitFeeIncurredEventWhenOnlyRadarFeeIsPresent() {
        ChargeEntity chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withFee(Fee.of(FeeType.RADAR, 2L))
                .build();

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any(FeeIncurredEvent.class))).thenReturn(false);

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, times(1)).emitAndRecordEvent(any(FeeIncurredEvent.class), isNotNull());
    }

    @Test
    void executeShouldOfferEventsWithIntermediateState() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now())
                .withCharge(chargeEntity)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .build();

        ChargeEventEntity secondEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_SUCCESS)
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
    void executeShouldOfferRefundEventsWithRefundHistory() {
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
    void shouldEmitPaymentDetailsEnteredOnlyOnce_IfChargeEventsContainsBothAuth3DSRequiredAndAuthSuccessEvents() {
        ChargeEventEntity firstEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now())
                .withCharge(chargeEntity)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .build();

        ChargeEventEntity secondEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now())
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.AUTHORISATION_3DS_REQUIRED)
                .build();

        ChargeEventEntity thirdEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_SUCCESS)
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
    void executeForDateRange_ShouldEmitAllEventsOfAChargeWithEventWithinDateRange() {
        ZonedDateTime eventDate = ZonedDateTime.parse("2016-01-01T00:00:00Z");

        ChargeEventEntity firstEvent = getChargeEventEntity(chargeEntity, ChargeStatus.CREATED, eventDate);
        ChargeEventEntity secondEvent = getChargeEventEntity(chargeEntity, ENTERING_CARD_DETAILS, eventDate);
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
    void executeForDateRange_ShouldEmitAllRefundsEventsOfAChargeWithRefundEventWithinDateRange() {
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
    void emitHistoricEventsById_shouldNotProcessIfRecordDoesNotExist() {
        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verifyNoInteractions(emittedEventDao);
        verify(refundDao, never()).searchAllHistoryByChargeExternalId(any());
    }

    @Test
    void executeForRefundsOnly_shouldEmitRefundEvents() {
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

    @Test
    void shouldEmitGateway3dsInfoObtainedEvent_whenThreeDsVersionIsMissingFromLedger() throws QueueException {
        chargeEntity.set3dsRequiredDetails(setupAuth3dsRequiredEntity());

        ChargeEventEntity successEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_SUCCESS)
                .build();

        chargeEntity.getEvents().clear();
        chargeEntity.getEvents().add(successEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any())).thenReturn(false);

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        verify(eventService, times(1)).emitAndRecordEvent(any(Gateway3dsInfoObtained.class), isNotNull());
    }

    @Test
    void shouldEmitGatewayDoesNotRequire3DSAuthorisation_whenRequires3DSSetToFalseAndChargeStatusAuthorised() throws QueueException {
        chargeEntity.setRequires3ds(false);

        ChargeEventEntity successEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_SUCCESS)
                .build();

        chargeEntity.getEvents().clear();
        chargeEntity.getEvents().add(successEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any())).thenReturn(false);

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);
        verify(eventService).emitAndRecordEvent(any(GatewayDoesNotRequire3dsAuthorisation.class), isNotNull());
    }

    @Test
    void shouldEmitGatewayDoesNotRequire3DSAuthorisation_whenRequires3DSSetToFalseAndChargeStatusRejected() throws QueueException {
        chargeEntity.setRequires3ds(false);

        ChargeEventEntity successEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(AUTHORISATION_REJECTED)
                .build();

        chargeEntity.getEvents().clear();
        chargeEntity.getEvents().add(successEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        when(emittedEventDao.hasBeenEmittedBefore(any())).thenReturn(false);

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);
        verify(eventService).emitAndRecordEvent(any(GatewayDoesNotRequire3dsAuthorisation.class), isNotNull());
    }


    @ParameterizedTest
    @MethodSource
    void shouldDoStuffWithRequested3dsExemptionObtained(Exemption3ds exemption3ds, ChargeStatus chargeStatus) throws QueueException {
        ChargeEventEntity successEvent = ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(chargeStatus)
                .build();

        chargeEntity.setExemption3ds(exemption3ds);
        chargeEntity.set3dsRequiredDetails(setupAuth3dsRequiredEntity());
        chargeEntity.getEvents().add(successEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));

        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);

        ArgumentCaptor<Gateway3dsExemptionResultObtained> argument = ArgumentCaptor.forClass(Gateway3dsExemptionResultObtained.class);
        verify(eventService, times(1)).emitAndRecordEvent(argument.capture(), isNotNull());

        List<Gateway3dsExemptionResultObtained> capturedEvents = argument.getAllValues();
        Gateway3dsExemptionResultObtainedEventDetails eventDetailsType = (Gateway3dsExemptionResultObtainedEventDetails) argument.getAllValues().get(0).getEventDetails();
        
        assertThat(capturedEvents.get(0).getEventType(), is("GATEWAY_3DS_EXEMPTION_RESULT_OBTAINED"));
        assertThat(eventDetailsType.getExemption3ds(), is(exemption3ds.toString()));
    }
    
    private static Stream<Arguments> shouldDoStuffWithRequested3dsExemptionObtained() {
        return Stream.of(
                Arguments.of(EXEMPTION_NOT_REQUESTED, AUTHORISATION_SUCCESS),
                Arguments.of(EXEMPTION_HONOURED, AUTHORISATION_SUCCESS),
                Arguments.of(EXEMPTION_REJECTED, AUTHORISATION_SUCCESS),
                Arguments.of(EXEMPTION_OUT_OF_SCOPE, AUTHORISATION_SUCCESS)
        );
    }

    @ParameterizedTest
    @MethodSource
    void shouldEmitRequested3dsExemptionWithEventDetails(Exemption3dsType exemption3dsType, String eventType, String event3dsType, Exemption3ds exemption3ds, ChargeStatus chargeStatus) throws QueueException {
        ChargeEventEntity successEvent = setupChargeEvent(chargeStatus);
        chargeEntity.setExemption3dsRequested(exemption3dsType);
        chargeEntity.setExemption3ds(exemption3ds);
        chargeEntity.getEvents().add(successEvent);

        when(chargeDao.findMaxId()).thenReturn(1L);
        when(chargeDao.findById(1L)).thenReturn(Optional.of(chargeEntity));
        
        historicalEventEmitterService.emitHistoricEventsById(1L, OptionalLong.empty(), 1L);
        
        ArgumentCaptor<Requested3dsExemption> requested3dsExemptionArgument = ArgumentCaptor.forClass(Requested3dsExemption.class);
        ArgumentCaptor<Gateway3dsExemptionResultObtained> gateway3dsExemptionArgument = ArgumentCaptor.forClass(Gateway3dsExemptionResultObtained.class);

        verify(eventService).emitAndRecordEvent(requested3dsExemptionArgument.capture(), isNotNull());
        verify(eventService).emitAndRecordEvent(gateway3dsExemptionArgument.capture(), isNotNull());

        Requested3dsExemption capturedRequestedExemption = requested3dsExemptionArgument.getValue();
        Requested3dsExemptionEventDetails requestedExemptionDetails = (Requested3dsExemptionEventDetails) capturedRequestedExemption.getEventDetails();

        Gateway3dsExemptionResultObtained capturedGatewayExemption = gateway3dsExemptionArgument.getValue();
        Gateway3dsExemptionResultObtainedEventDetails gatewayExemptionDetails = (Gateway3dsExemptionResultObtainedEventDetails) capturedGatewayExemption.getEventDetails();

        assertAll(
                () -> assertThat(capturedRequestedExemption.getEventType(), is(eventType)),
                () -> assertThat(requestedExemptionDetails.getType(), is(event3dsType)),
                () -> assertThat(capturedGatewayExemption.getEventType(), is("GATEWAY_3DS_EXEMPTION_RESULT_OBTAINED")),
                () -> assertThat(gatewayExemptionDetails.getExemption3ds(), is(exemption3ds == null ? null : exemption3ds.toString()))
        );
    }
    
    private ChargeEventEntity setupChargeEvent(ChargeStatus chargeStatus) {
        return ChargeEventEntityFixture.aValidChargeEventEntity()
                .withTimestamp(ZonedDateTime.now().plusMinutes(2))
                .withCharge(chargeEntity)
                .withChargeStatus(chargeStatus)
                .build();
    }
    
    private Auth3dsRequiredEntity setupAuth3dsRequiredEntity() {
        var auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setThreeDsVersion("2.1.1");
        return auth3dsRequiredEntity;
    }
    
    private static Stream<Arguments> shouldEmitRequested3dsExemptionWithEventDetails() {
        return Stream.of(
                Arguments.of(OPTIMISED, "REQUESTED_3DS_EXEMPTION", "OPTIMISED", EXEMPTION_HONOURED, AUTHORISATION_SUCCESS),
                Arguments.of(OPTIMISED, "REQUESTED_3DS_EXEMPTION", "OPTIMISED", EXEMPTION_NOT_REQUESTED, AUTHORISATION_SUCCESS),
                Arguments.of(OPTIMISED, "REQUESTED_3DS_EXEMPTION", "OPTIMISED", EXEMPTION_OUT_OF_SCOPE, AUTHORISATION_SUCCESS),
                Arguments.of(CORPORATE, "REQUESTED_3DS_EXEMPTION", "CORPORATE", EXEMPTION_HONOURED, AUTHORISATION_SUCCESS),
                Arguments.of(CORPORATE, "REQUESTED_3DS_EXEMPTION", "CORPORATE", EXEMPTION_REJECTED, AUTHORISATION_SUCCESS),
                Arguments.of(CORPORATE, "REQUESTED_3DS_EXEMPTION", "CORPORATE", EXEMPTION_OUT_OF_SCOPE, AUTHORISATION_SUCCESS),
                Arguments.of(OPTIMISED, "REQUESTED_3DS_EXEMPTION", "OPTIMISED", EXEMPTION_HONOURED, AUTHORISATION_REJECTED),
                Arguments.of(OPTIMISED, "REQUESTED_3DS_EXEMPTION", "OPTIMISED", EXEMPTION_NOT_REQUESTED, AUTHORISATION_REJECTED),
                Arguments.of(OPTIMISED, "REQUESTED_3DS_EXEMPTION", "OPTIMISED", EXEMPTION_OUT_OF_SCOPE, AUTHORISATION_REJECTED),
                Arguments.of(CORPORATE, "REQUESTED_3DS_EXEMPTION", "CORPORATE", EXEMPTION_HONOURED, AUTHORISATION_REJECTED),
                Arguments.of(CORPORATE, "REQUESTED_3DS_EXEMPTION", "CORPORATE", EXEMPTION_REJECTED, AUTHORISATION_REJECTED),
                Arguments.of(CORPORATE, "REQUESTED_3DS_EXEMPTION", "CORPORATE", EXEMPTION_OUT_OF_SCOPE, AUTHORISATION_REJECTED)
//                Arguments.of(null, "PAYMENT_DETAILS_ENTERED", "OPTIMISED", null, AUTHORISATION_SUCCESS)
        );
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
