package uk.gov.pay.connector.events.model.refund;

import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.events.eventdetails.EmptyEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.AuthorisationRejectedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.CancelledByUserEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.CancelledWithGatewayAfterAuthorisationErrorEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureConfirmedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureSubmittedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.GatewayRequires3dsAuthorisationEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentNotificationCreatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.UserEmailCollectedEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByUserEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithGatewayTransactionIdDetails;
import uk.gov.pay.connector.events.exception.EventCreationException;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.EventFactory;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.events.model.charge.AuthorisationCancelled;
import uk.gov.pay.connector.events.model.charge.AuthorisationErrorCheckedWithGatewayChargeWasMissing;
import uk.gov.pay.connector.events.model.charge.AuthorisationErrorCheckedWithGatewayChargeWasRejected;
import uk.gov.pay.connector.events.model.charge.AuthorisationRejected;
import uk.gov.pay.connector.events.model.charge.BackfillerRecreatedUserEmailCollected;
import uk.gov.pay.connector.events.model.charge.CancelByExpirationFailed;
import uk.gov.pay.connector.events.model.charge.CancelByExpirationSubmitted;
import uk.gov.pay.connector.events.model.charge.CancelByExternalServiceFailed;
import uk.gov.pay.connector.events.model.charge.CancelByExternalServiceSubmitted;
import uk.gov.pay.connector.events.model.charge.CancelByUserFailed;
import uk.gov.pay.connector.events.model.charge.CancelByUserSubmitted;
import uk.gov.pay.connector.events.model.charge.CancelledByExpiration;
import uk.gov.pay.connector.events.model.charge.CancelledByExternalService;
import uk.gov.pay.connector.events.model.charge.CancelledByUser;
import uk.gov.pay.connector.events.model.charge.CancelledWithGatewayAfterAuthorisationError;
import uk.gov.pay.connector.events.model.charge.CaptureAbandonedAfterTooManyRetries;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmed;
import uk.gov.pay.connector.events.model.charge.CaptureErrored;
import uk.gov.pay.connector.events.model.charge.CaptureSubmitted;
import uk.gov.pay.connector.events.model.charge.GatewayErrorDuringAuthorisation;
import uk.gov.pay.connector.events.model.charge.GatewayRequires3dsAuthorisation;
import uk.gov.pay.connector.events.model.charge.GatewayTimeoutDuringAuthorisation;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentExpired;
import uk.gov.pay.connector.events.model.charge.PaymentNotificationCreated;
import uk.gov.pay.connector.events.model.charge.RefundAvailabilityUpdated;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToCapturedToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.UnexpectedGatewayErrorDuringAuthorisation;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.pact.RefundHistoryEntityFixture;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.statetransition.PaymentStateTransition;
import uk.gov.pay.connector.queue.statetransition.RefundStateTransition;
import uk.gov.pay.connector.queue.statetransition.StateTransition;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.randomAlphanumeric;

@ExtendWith(MockitoExtension.class)
class EventFactoryTest {

    private final ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();

    private ChargeService chargeService;
    private RefundDao refundDao;
    private RefundService refundService;
    private ChargeEventDao chargeEventDao;
    private PaymentProviders paymentProviders;
    private PaymentInstrumentService paymentInstrumentService;

    private EventFactory eventFactory;

    @BeforeEach
    void setUp() {
        chargeService = mock(ChargeService.class);
        refundDao = mock(RefundDao.class);
        refundService = mock(RefundService.class);
        chargeEventDao = mock(ChargeEventDao.class);
        paymentProviders = mock(PaymentProviders.class);
        paymentInstrumentService = mock(PaymentInstrumentService.class);

        eventFactory = new EventFactory(chargeService, refundDao, chargeEventDao);
    }

    @Test
    void shouldCreateCorrectEventsFromRefundCreatedStateTransition() throws Exception {
        when(chargeService.findCharge(chargeEntity.getExternalId())).thenReturn(Optional.of(Charge.from(chargeEntity)));
        RefundHistory refundCreatedHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.CREATED.getValue())
                .withUserExternalId("user_external_id")
                .withUserEmail("test@example.com")
                .withChargeExternalId(chargeEntity.getExternalId())
                .withAmount(chargeEntity.getAmount())
                .build();
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundCreatedHistory.getExternalId(),
                refundCreatedHistory.getStatus())
        ).thenReturn(Optional.of(refundCreatedHistory));

        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);
        when(chargeService.createRefundAvailabilityUpdatedEvent(Charge.from(chargeEntity), refundCreatedHistory.getHistoryStartDate().toInstant()))
                .thenReturn(refundAvailabilityUpdated);

        StateTransition refundStateTransition = new RefundStateTransition(
                refundCreatedHistory.getExternalId(), refundCreatedHistory.getStatus(), RefundCreatedByUser.class
        );
        List<Event> refundEvents = eventFactory.createEvents(refundStateTransition);

        assertThat(refundEvents.size(), is(2));

        RefundCreatedByUser refundCreatedByUser = (RefundCreatedByUser) refundEvents.stream()
                .filter(e -> ResourceType.REFUND.equals(e.getResourceType()))
                .findFirst().get();

        RefundCreatedByUserEventDetails eventDetails = (RefundCreatedByUserEventDetails) refundCreatedByUser.getEventDetails();
        assertThat(refundCreatedByUser.getParentResourceExternalId(), is(chargeEntity.getExternalId()));
        assertThat(eventDetails.getRefundedBy(), is("user_external_id"));
        assertThat(eventDetails.getUserEmail(), is("test@example.com"));
        assertThat(refundCreatedByUser.getResourceType(), is(ResourceType.REFUND));
        assertThat(refundCreatedByUser.getEventDetails(), is(instanceOf(RefundCreatedByUserEventDetails.class)));

        assertThat(refundEvents, hasItem(refundAvailabilityUpdated));
    }

    @Test
    void shouldCreateCorrectEventsFromRefundSubmittedStateTransition() throws Exception {
        when(chargeService.findCharge(chargeEntity.getExternalId())).thenReturn(Optional.of(Charge.from(chargeEntity)));
        RefundHistory refundSubmittedHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.REFUND_SUBMITTED.getValue())
                .withUserExternalId("user_external_id")
                .withChargeExternalId(chargeEntity.getExternalId())
                .withAmount(chargeEntity.getAmount())
                .build();
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundSubmittedHistory.getExternalId(),
                refundSubmittedHistory.getStatus())
        ).thenReturn(Optional.of(refundSubmittedHistory));


        StateTransition refundStateTransition = new RefundStateTransition(
                refundSubmittedHistory.getExternalId(), refundSubmittedHistory.getStatus(), RefundSubmitted.class
        );
        List<Event> refundEvents = eventFactory.createEvents(refundStateTransition);


        assertThat(refundEvents.size(), is(1));

        RefundSubmitted refundSubmitted = (RefundSubmitted) refundEvents.getFirst();

        assertThat(refundSubmitted.getParentResourceExternalId(), is(chargeEntity.getExternalId()));
        assertThat(refundSubmitted.getResourceExternalId(), is(refundSubmittedHistory.getExternalId()));
        assertThat(refundSubmitted.getResourceType(), is(ResourceType.REFUND));
        assertThat(refundSubmitted.getEventDetails(), is(instanceOf(RefundEventWithGatewayTransactionIdDetails.class)));
    }

    @Test
    void shouldCreateCorrectEventsFromRefundSucceededStateTransition() throws Exception {
        when(chargeService.findCharge(chargeEntity.getExternalId())).thenReturn(Optional.of(Charge.from(chargeEntity)));
        RefundHistory refundSucceededHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.REFUNDED.getValue())
                .withUserExternalId("user_external_id")
                .withChargeExternalId(chargeEntity.getExternalId())
                .withAmount(chargeEntity.getAmount())
                .build();
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundSucceededHistory.getExternalId(),
                refundSucceededHistory.getStatus())
        ).thenReturn(Optional.of(refundSucceededHistory));


        StateTransition refundStateTransition = new RefundStateTransition(
                refundSucceededHistory.getExternalId(), refundSucceededHistory.getStatus(), RefundSucceeded.class
        );
        List<Event> refundEvents = eventFactory.createEvents(refundStateTransition);


        assertThat(refundEvents.size(), is(1));

        RefundSucceeded refundSucceeded = (RefundSucceeded) refundEvents.getFirst();

        assertThat(refundSucceeded.getParentResourceExternalId(), is(chargeEntity.getExternalId()));
        assertThat(refundSucceeded.getResourceExternalId(), is(refundSucceededHistory.getExternalId()));
        assertThat(refundSucceeded.getResourceType(), is(ResourceType.REFUND));
        assertThat(refundSucceeded.getEventDetails(), is(instanceOf(RefundEventWithGatewayTransactionIdDetails.class)));
    }

    @Test
    void shouldCreateCorrectEventsFromRefundErrorStateTransition() throws Exception {
        Charge charge = Charge.from(chargeEntity);
        when(chargeService.findCharge(chargeEntity.getExternalId())).thenReturn(Optional.of(charge));
        RefundHistory refundErrorHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.REFUND_ERROR.getValue())
                .withUserExternalId("user_external_id")
                .withChargeExternalId(chargeEntity.getExternalId())
                .withGatewayTransactionId(randomAlphanumeric(30))
                .withAmount(chargeEntity.getAmount())
                .build();
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundErrorHistory.getExternalId(),
                refundErrorHistory.getStatus())
        ).thenReturn(Optional.of(refundErrorHistory));

        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);
        when(chargeService.createRefundAvailabilityUpdatedEvent(charge, refundErrorHistory.getHistoryStartDate().toInstant()))
                .thenReturn(refundAvailabilityUpdated);

        StateTransition refundStateTransition = new RefundStateTransition(
                refundErrorHistory.getExternalId(), refundErrorHistory.getStatus(), RefundError.class
        );
        List<Event> refundEvents = eventFactory.createEvents(refundStateTransition);


        assertThat(refundEvents.size(), is(2));

        RefundError refundError = (RefundError) refundEvents.stream()
                .filter(e -> ResourceType.REFUND.equals(e.getResourceType()))
                .findFirst().get();
        assertThat(refundError.getParentResourceExternalId(), is(chargeEntity.getExternalId()));
        assertThat(((RefundEventWithGatewayTransactionIdDetails) refundError.getEventDetails()).getGatewayTransactionId(), is(refundErrorHistory.getGatewayTransactionId()));
        assertThat(refundError.getResourceType(), is(ResourceType.REFUND));
        assertThat(refundError.getEventDetails(), is(instanceOf(RefundEventWithGatewayTransactionIdDetails.class)));

        assertThat(refundEvents, hasItem(refundAvailabilityUpdated));
    }

    @Test
    void shouldCreatePaymentCreatedEventWithCorrectPayloadForPaymentCreatedStateTransition() throws Exception {
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(chargeEntity)
                .withId(chargeEventEntityId)
                .build();
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, PaymentCreated.class);

        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);
        when(chargeService.createRefundAvailabilityUpdatedEvent(Charge.from(chargeEntity), chargeEventEntity.getUpdated().toInstant()))
                .thenReturn(refundAvailabilityUpdated);

        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2));
        PaymentCreated event = (PaymentCreated) events.getFirst();
        assertThat(event, instanceOf(PaymentCreated.class));
        assertThat(event.getEventDetails(), instanceOf(PaymentCreatedEventDetails.class));
        assertThat(event.getResourceExternalId(), Is.is(chargeEventEntity.getChargeEntity().getExternalId()));

        assertThat(events, hasItem(refundAvailabilityUpdated));
    }

    @Test
    void shouldCreateEventWithNoPayloadForNonPayloadEventStateTransition() throws Exception {
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withId(chargeEventEntityId)
                .withCharge(chargeEntity)
                .build();
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, CancelByExternalServiceSubmitted.class);

        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);
        when(chargeService.createRefundAvailabilityUpdatedEvent(Charge.from(chargeEntity), chargeEventEntity.getUpdated().toInstant()))
                .thenReturn(refundAvailabilityUpdated);

        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2)); // also creates RefundAvailabilityUpdated event
        CancelByExternalServiceSubmitted event = (CancelByExternalServiceSubmitted) events.getFirst();
        assertThat(event, instanceOf(CancelByExternalServiceSubmitted.class));
        assertThat(event.getEventDetails(), instanceOf(EmptyEventDetails.class));
    }

    static private Object[] statesAffectingRefundability() {
        return new Object[]{
                // non terminal events
                new Object[]{PaymentCreated.class, PaymentCreatedEventDetails.class},
                new Object[]{CaptureSubmitted.class, CaptureSubmittedEventDetails.class},
                new Object[]{GatewayErrorDuringAuthorisation.class, EmptyEventDetails.class},
                new Object[]{GatewayTimeoutDuringAuthorisation.class, EmptyEventDetails.class},
                new Object[]{UnexpectedGatewayErrorDuringAuthorisation.class, EmptyEventDetails.class},
                new Object[]{CancelByExternalServiceSubmitted.class, EmptyEventDetails.class},
                new Object[]{CancelByExpirationSubmitted.class, EmptyEventDetails.class},
                new Object[]{CancelByUserSubmitted.class, EmptyEventDetails.class},
                // terminal states
                new Object[]{AuthorisationCancelled.class, EmptyEventDetails.class},
                new Object[]{AuthorisationErrorCheckedWithGatewayChargeWasMissing.class, EmptyEventDetails.class},
                new Object[]{AuthorisationErrorCheckedWithGatewayChargeWasRejected.class, EmptyEventDetails.class},
                new Object[]{AuthorisationRejected.class, EmptyEventDetails.class},
                new Object[]{CancelByExpirationFailed.class, EmptyEventDetails.class},
                new Object[]{CancelByExternalServiceFailed.class, EmptyEventDetails.class},
                new Object[]{CancelByUserFailed.class, EmptyEventDetails.class},
                new Object[]{CancelledByExpiration.class, EmptyEventDetails.class},
                new Object[]{CancelledByExternalService.class, EmptyEventDetails.class},
                new Object[]{CancelledByUser.class, CancelledByUserEventDetails.class},
                new Object[]{CancelledWithGatewayAfterAuthorisationError.class, CancelledWithGatewayAfterAuthorisationErrorEventDetails.class},
                new Object[]{CaptureAbandonedAfterTooManyRetries.class, EmptyEventDetails.class},
                new Object[]{CaptureConfirmed.class, CaptureConfirmedEventDetails.class},
                new Object[]{CaptureErrored.class, EmptyEventDetails.class},
                new Object[]{PaymentExpired.class, EmptyEventDetails.class},
                new Object[]{StatusCorrectedToCapturedToMatchGatewayStatus.class, CaptureConfirmedEventDetails.class}
        };
    }

    @ParameterizedTest
    @MethodSource("statesAffectingRefundability")
    void shouldCreatedRefundAvailabilityUpdatedEventForTerminalAndOtherStatesAffectingRefundability(
            Class eventClass, Class eventDetailsClass) throws Exception {

        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(chargeEntity)
                .withId(chargeEventEntityId)
                .build();
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, eventClass);

        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);
        when(chargeService.createRefundAvailabilityUpdatedEvent(Charge.from(chargeEntity), chargeEventEntity.getUpdated().toInstant()))
                .thenReturn(refundAvailabilityUpdated);

        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2));
        Event event1 = events.getFirst();
        assertThat(event1, instanceOf(eventClass));
        assertThat(event1.getEventDetails(), instanceOf(eventDetailsClass));

        assertThat(events, hasItem(refundAvailabilityUpdated));
    }

    @Test
    void shouldCreatedARefundAvailabilityUpdatedEvent_ifPaymentIsHistoric() throws Exception {
        ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
        refundSummary.setStatus("available");
        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionId(chargeEntity.getExternalId());
        transaction.setPaymentProvider("sandbox");
        transaction.setRefundSummary(refundSummary);
        transaction.setAmount(chargeEntity.getAmount());
        transaction.setTotalAmount(chargeEntity.getAmount());
        transaction.setCreatedDate(Instant.now().toString());
        transaction.setGatewayAccountId(chargeEntity.getGatewayAccount().getId().toString());
        transaction.setServiceId(chargeEntity.getServiceId());
        transaction.setLive(chargeEntity.getGatewayAccount().isLive());
        Charge chargeFromTransaction = Charge.from(transaction);
        when(chargeService.findCharge(transaction.getTransactionId())).thenReturn(Optional.of(chargeFromTransaction));

        RefundHistory refundErrorHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.REFUND_ERROR.getValue())
                .withUserExternalId("user_external_id")
                .withChargeExternalId(chargeEntity.getExternalId())
                .withAmount(chargeEntity.getAmount())
                .build();
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundErrorHistory.getExternalId(),
                refundErrorHistory.getStatus())
        ).thenReturn(Optional.of(refundErrorHistory));

        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);
        when(chargeService.createRefundAvailabilityUpdatedEvent(chargeFromTransaction, refundErrorHistory.getHistoryStartDate().toInstant()))
                .thenReturn(refundAvailabilityUpdated);

        StateTransition refundStateTransition = new RefundStateTransition(
                refundErrorHistory.getExternalId(), refundErrorHistory.getStatus(), RefundError.class
        );
        List<Event> refundEvents = eventFactory.createEvents(refundStateTransition);

        assertThat(refundEvents.size(), is(2));

        assertThat(refundEvents, hasItem(refundAvailabilityUpdated));
    }

    @Test
    void shouldCreatedCorrectEventForPaymentNotificationCreated() throws Exception {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(ChargeStatus.PAYMENT_NOTIFICATION_CREATED)
                .build();
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withId(chargeEventEntityId)
                .build();
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, PaymentNotificationCreated.class);
        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(1));

        PaymentNotificationCreated event = (PaymentNotificationCreated) events.getFirst();
        assertThat(event, is(instanceOf(PaymentNotificationCreated.class)));

        assertThat(event.getEventDetails(), instanceOf(PaymentNotificationCreatedEventDetails.class));
        assertThat(event.getResourceExternalId(), is(chargeEventEntity.getChargeEntity().getExternalId()));

        PaymentNotificationCreatedEventDetails eventDetails = (PaymentNotificationCreatedEventDetails) event.getEventDetails();
        assertThat(eventDetails.getGatewayTransactionId(), is(charge.getGatewayTransactionId()));
    }

    @Test
    void shouldCreateCorrectEventWithEventDetailsForAuthorisationRejectedAgreementAuthorisationMode() throws Exception {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(ChargeStatus.AUTHORISATION_REJECTED)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withCanRetry(true)
                .build();
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withChargeStatus(AUTHORISATION_REJECTED)
                .withId(chargeEventEntityId)
                .build();
        charge.getEvents().add(chargeEventEntity);
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);
        when(chargeService.createRefundAvailabilityUpdatedEvent(Charge.from(charge), chargeEventEntity.getUpdated().toInstant()))
                .thenReturn(refundAvailabilityUpdated);

        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, AuthorisationRejected.class);
        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2));

        AuthorisationRejected event = (AuthorisationRejected) events.getFirst();
        assertThat(event, is(instanceOf(AuthorisationRejected.class)));

        assertThat(event.getEventDetails(), instanceOf(AuthorisationRejectedEventDetails.class));
        assertThat(event.getResourceExternalId(), is(chargeEventEntity.getChargeEntity().getExternalId()));

        AuthorisationRejectedEventDetails eventDetails = (AuthorisationRejectedEventDetails) event.getEventDetails();
        assertThat(eventDetails.getCanRetry(), is(true));
    }

    @Test
    void shouldCreateCorrectEventWithoutEventDetailsForAuthorisationRejectedWebAuthorisationMode() throws Exception {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(ChargeStatus.AUTHORISATION_REJECTED)
                .withAuthorisationMode(AuthorisationMode.WEB)
                .withCanRetry(true)
                .build();
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withChargeStatus(AUTHORISATION_REJECTED)
                .withId(chargeEventEntityId)
                .build();
        charge.getEvents().add(chargeEventEntity);
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);
        when(chargeService.createRefundAvailabilityUpdatedEvent(Charge.from(charge), chargeEventEntity.getUpdated().toInstant()))
                .thenReturn(refundAvailabilityUpdated);

        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, AuthorisationRejected.class);
        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2));

        AuthorisationRejected event = (AuthorisationRejected) events.getFirst();
        assertThat(event, is(instanceOf(AuthorisationRejected.class)));

        assertThat(event.getEventDetails(), instanceOf(EmptyEventDetails.class));
        assertThat(event.getResourceExternalId(), is(chargeEventEntity.getChargeEntity().getExternalId()));
    }

    @Test
    void shouldCreatedCorrectEventForBackfillRecreatedUserEmailCollectedEvent() throws Exception {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(ChargeStatus.USER_CANCELLED)
                .withEmail("test@example.org")
                .build();
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .withId(chargeEventEntityId)
                .build();
        charge.getEvents().add(chargeEventEntity);
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId,
                BackfillerRecreatedUserEmailCollected.class);
        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(1));

        BackfillerRecreatedUserEmailCollected event = (BackfillerRecreatedUserEmailCollected) events.getFirst();
        assertThat(event, is(instanceOf(BackfillerRecreatedUserEmailCollected.class)));

        assertThat(event.getEventDetails(), instanceOf(UserEmailCollectedEventDetails.class));
        assertThat(event.getResourceExternalId(), is(chargeEventEntity.getChargeEntity().getExternalId()));

        UserEmailCollectedEventDetails eventDetails = (UserEmailCollectedEventDetails) event.getEventDetails();
        assertThat(eventDetails.getEmail(), is(charge.getEmail()));
    }

    @Test
    void shouldCreatedCorrectEventForGatewayRequires3dsAuthorisationEvent() throws Exception {
        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setThreeDsVersion("2.1.0");
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(ChargeStatus.USER_CANCELLED)
                .withEmail("test@example.org")
                .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
                .build();
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withChargeStatus(AUTHORISATION_3DS_REQUIRED)
                .withId(chargeEventEntityId)
                .build();
        charge.getEvents().add(chargeEventEntity);
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId,
                GatewayRequires3dsAuthorisation.class);
        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(1));

        GatewayRequires3dsAuthorisation event = (GatewayRequires3dsAuthorisation) events.getFirst();
        assertThat(event, is(instanceOf(GatewayRequires3dsAuthorisation.class)));

        assertThat(event.getEventDetails(), instanceOf(GatewayRequires3dsAuthorisationEventDetails.class));
        assertThat(event.getResourceExternalId(), is(chargeEventEntity.getChargeEntity().getExternalId()));

        GatewayRequires3dsAuthorisationEventDetails eventDetails = (GatewayRequires3dsAuthorisationEventDetails) event.getEventDetails();
        assertThat(eventDetails.getVersion3DS(), is("2.1.0"));
        assertThat(eventDetails.isRequires3DS(), is(true));
    }

    @Test
    void shouldCreateCorrectEventForCancelledWithGatewayAfterAuthorisationErrorEvent() throws EventCreationException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(ChargeStatus.AUTHORISATION_ERROR)
                .withGatewayTransactionId("gateway_transaction_id")
                .withEmail("test@example.org")
                .build();
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withChargeStatus(AUTHORISATION_3DS_REQUIRED)
                .withId(chargeEventEntityId)
                .build();
        charge.getEvents().add(chargeEventEntity);
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId,
                CancelledWithGatewayAfterAuthorisationError.class);
        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);
        when(chargeService.createRefundAvailabilityUpdatedEvent(Charge.from(charge), chargeEventEntity.getUpdated().toInstant()))
                .thenReturn(refundAvailabilityUpdated);
        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2));
        
        CancelledWithGatewayAfterAuthorisationError event = (CancelledWithGatewayAfterAuthorisationError) events.getFirst();
        assertThat(event.getEventDetails(), is(instanceOf(CancelledWithGatewayAfterAuthorisationErrorEventDetails.class)));
        assertThat(event.getResourceExternalId(), is(chargeEventEntity.getChargeEntity().getExternalId()));

        CancelledWithGatewayAfterAuthorisationErrorEventDetails eventDetails = (CancelledWithGatewayAfterAuthorisationErrorEventDetails) event.getEventDetails();
        assertThat(eventDetails.getGatewayTransactionId(), is("gateway_transaction_id"));
    }

    @Test
    void shouldThrowExceptionIfNoChargeFoundForPaymentStateTransitionEvent() throws EventCreationException {
        long chargeEventEntityId = 100L;
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.empty()
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, PaymentCreated.class);

        var thrown = assertThrows(EventCreationException.class, () -> eventFactory.createEvents(paymentStateTransition));
        assertThat(thrown.getMessage(), Matchers.is(String.format("Event id = [%s], exception = Failed to create PaymentStateTransition event because the associated charge event could not be found", paymentStateTransition.getChargeEventId())));
    }

    @Test
    void shouldThrowExceptionIfNoRefundHistoryFoundForRefundStateTransitionEvent() {
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                "refund-history-external-id",
                RefundStatus.CREATED)
        ).thenReturn(Optional.empty());

        StateTransition refundStateTransition = new RefundStateTransition(
                "refund-history-external-id", RefundStatus.CREATED, RefundCreatedByUser.class
        );

        var thrown = assertThrows(EventCreationException.class, () -> eventFactory.createEvents(refundStateTransition));
        assertThat(thrown.getMessage(), Matchers.is(String.format("Event id = [%s], exception = Failed to create RefundStateTransition event because refund history could not be found", refundStateTransition.getIdentifier())));
    }

    @Test
    void shouldThrowExceptionIfChargeNotFoundForRefundStateTransitionEvent() {
        when(chargeService.findCharge("charge_external_id")).thenReturn(Optional.empty());
        RefundHistory refundCreatedHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.CREATED.getValue())
                .withUserExternalId("user_external_id")
                .withUserEmail("test@example.com")
                .withChargeExternalId("charge_external_id")
                .withAmount(1000L)
                .build();
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundCreatedHistory.getExternalId(),
                refundCreatedHistory.getStatus())
        ).thenReturn(Optional.of(refundCreatedHistory));

        StateTransition refundStateTransition = new RefundStateTransition(
                refundCreatedHistory.getExternalId(), refundCreatedHistory.getStatus(), RefundCreatedByUser.class
        );

        var thrown = assertThrows(EventCreationException.class, () -> eventFactory.createEvents(refundStateTransition));
        assertThat(thrown.getMessage(), Matchers.is(String.format("Event id = [%s], exception = Failed to create RefundStateTransition event because the charge could not be found", refundCreatedHistory.getChargeExternalId())));
    }
}
