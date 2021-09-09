package uk.gov.pay.connector.events.model.refund;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import uk.gov.pay.connector.events.eventdetails.charge.CancelledByUserEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureConfirmedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureSubmittedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.GatewayRequires3dsAuthorisationEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentNotificationCreatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.RefundAvailabilityUpdatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.UserEmailCollectedEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByUserEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithGatewayTransactionIdDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.EventFactory;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.events.model.charge.AuthorisationCancelled;
import uk.gov.pay.connector.events.model.charge.AuthorisationErrorCheckedWithGatewayChargeWasMissing;
import uk.gov.pay.connector.events.model.charge.AuthorisationErrorCheckedWithGatewayChargeWasRejected;
import uk.gov.pay.connector.events.model.charge.AuthorisationRejected;
import uk.gov.pay.connector.events.model.charge.BackfillerRecreatedUserEmailCollected;
import uk.gov.pay.connector.events.model.charge.CancelByExpirationFailed;
import uk.gov.pay.connector.events.model.charge.CancelByExternalServiceFailed;
import uk.gov.pay.connector.events.model.charge.CancelByExternalServiceSubmitted;
import uk.gov.pay.connector.events.model.charge.CancelByUserFailed;
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
import uk.gov.pay.connector.events.model.charge.UnexpectedGatewayErrorDuringAuthorisation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.pact.RefundHistoryEntityFixture;
import uk.gov.pay.connector.queue.statetransition.PaymentStateTransition;
import uk.gov.pay.connector.queue.statetransition.RefundStateTransition;
import uk.gov.pay.connector.queue.statetransition.StateTransition;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

@RunWith(JUnitParamsRunner.class)
public class EventFactoryTest {

    private final ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();

    private ChargeService chargeService;
    private RefundDao refundDao;
    private RefundService refundService;
    private ChargeEventDao chargeEventDao;
    private PaymentProviders paymentProviders;

    private EventFactory eventFactory;

    @Before
    public void setUp() {
        chargeService = mock(ChargeService.class);
        refundDao = mock(RefundDao.class);
        refundService = mock(RefundService.class);
        chargeEventDao = mock(ChargeEventDao.class);
        paymentProviders = mock(PaymentProviders.class);

        PaymentProvider paymentProvider = new SandboxPaymentProvider();
        when(paymentProviders.byName(any(PaymentGatewayName.class))).thenReturn(paymentProvider);

        eventFactory = new EventFactory(chargeService, refundDao, refundService, chargeEventDao, paymentProviders);
    }

    @Test
    public void shouldCreateCorrectEventsFromRefundCreatedStateTransition() throws Exception {
        when(chargeService.findCharge(charge.getExternalId())).thenReturn(Optional.of(Charge.from(charge)));
        RefundHistory refundCreatedHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.CREATED.getValue())
                .withUserExternalId("user_external_id")
                .withUserEmail("test@example.com")
                .withChargeExternalId(charge.getExternalId())
                .withAmount(charge.getAmount())
                .build();
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundCreatedHistory.getExternalId(),
                refundCreatedHistory.getStatus())
        ).thenReturn(Optional.of(refundCreatedHistory));


        StateTransition refundStateTransition = new RefundStateTransition(
                refundCreatedHistory.getExternalId(), refundCreatedHistory.getStatus(), RefundCreatedByUser.class
        );
        List<Event> refundEvents = eventFactory.createEvents(refundStateTransition);

        assertThat(refundEvents.size(), is(2));

        RefundCreatedByUser refundCreatedByUser = (RefundCreatedByUser) refundEvents.stream()
                .filter(e -> ResourceType.REFUND.equals(e.getResourceType()))
                .findFirst().get();

        RefundCreatedByUserEventDetails eventDetails = (RefundCreatedByUserEventDetails) refundCreatedByUser.getEventDetails();
        assertThat(refundCreatedByUser.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(eventDetails.getRefundedBy(), is("user_external_id"));
        assertThat(eventDetails.getUserEmail(), is("test@example.com"));
        assertThat(refundCreatedByUser.getResourceType(), is(ResourceType.REFUND));
        assertThat(refundCreatedByUser.getEventDetails(), is(instanceOf(RefundCreatedByUserEventDetails.class)));

        RefundAvailabilityUpdated refundAvailabilityUpdated = (RefundAvailabilityUpdated) refundEvents.stream()
                .filter(e -> ResourceType.PAYMENT.equals(e.getResourceType()))
                .findFirst().get();
        assertThat(refundAvailabilityUpdated.getResourceExternalId(), is(charge.getExternalId()));
        assertThat(refundAvailabilityUpdated.getResourceType(), is(ResourceType.PAYMENT));
        assertThat(refundAvailabilityUpdated.getEventDetails(), is(instanceOf(RefundAvailabilityUpdatedEventDetails.class)));
    }

    @Test
    public void shouldCreateCorrectEventsFromRefundSubmittedStateTransition() throws Exception {
        when(chargeService.findCharge(charge.getExternalId())).thenReturn(Optional.of(Charge.from(charge)));
        RefundHistory refundSubmittedHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.REFUND_SUBMITTED.getValue())
                .withUserExternalId("user_external_id")
                .withChargeExternalId(charge.getExternalId())
                .withAmount(charge.getAmount())
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

        RefundSubmitted refundSubmitted = (RefundSubmitted) refundEvents.get(0);

        assertThat(refundSubmitted.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(refundSubmitted.getResourceExternalId(), is(refundSubmittedHistory.getExternalId()));
        assertThat(refundSubmitted.getResourceType(), is(ResourceType.REFUND));
        assertThat(refundSubmitted.getEventDetails(), is(instanceOf(RefundEventWithGatewayTransactionIdDetails.class)));
    }

    @Test
    public void shouldCreateCorrectEventsFromRefundSucceededStateTransition() throws Exception {
        when(chargeService.findCharge(charge.getExternalId())).thenReturn(Optional.of(Charge.from(charge)));
        RefundHistory refundSucceededHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.REFUNDED.getValue())
                .withUserExternalId("user_external_id")
                .withChargeExternalId(charge.getExternalId())
                .withAmount(charge.getAmount())
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

        RefundSucceeded refundSucceeded = (RefundSucceeded) refundEvents.get(0);

        assertThat(refundSucceeded.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(refundSucceeded.getResourceExternalId(), is(refundSucceededHistory.getExternalId()));
        assertThat(refundSucceeded.getResourceType(), is(ResourceType.REFUND));
        assertThat(refundSucceeded.getEventDetails(), is(instanceOf(RefundEventWithGatewayTransactionIdDetails.class)));
    }

    @Test
    public void shouldCreateCorrectEventsFromRefundErrorStateTransition() throws Exception {
        when(chargeService.findCharge(charge.getExternalId())).thenReturn(Optional.of(Charge.from(charge)));
        RefundHistory refundErrorHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.REFUND_ERROR.getValue())
                .withUserExternalId("user_external_id")
                .withChargeExternalId(charge.getExternalId())
                .withGatewayTransactionId(randomAlphanumeric(30))
                .withAmount(charge.getAmount())
                .build();
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundErrorHistory.getExternalId(),
                refundErrorHistory.getStatus())
        ).thenReturn(Optional.of(refundErrorHistory));


        StateTransition refundStateTransition = new RefundStateTransition(
                refundErrorHistory.getExternalId(), refundErrorHistory.getStatus(), RefundError.class
        );
        List<Event> refundEvents = eventFactory.createEvents(refundStateTransition);


        assertThat(refundEvents.size(), is(2));

        RefundError refundError = (RefundError) refundEvents.stream()
                .filter(e -> ResourceType.REFUND.equals(e.getResourceType()))
                .findFirst().get();
        assertThat(refundError.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(((RefundEventWithGatewayTransactionIdDetails) refundError.getEventDetails()).getGatewayTransactionId(), is(refundErrorHistory.getGatewayTransactionId()));
        assertThat(refundError.getResourceType(), is(ResourceType.REFUND));
        assertThat(refundError.getEventDetails(), is(instanceOf(RefundEventWithGatewayTransactionIdDetails.class)));

        RefundAvailabilityUpdated refundAvailabilityUpdated = (RefundAvailabilityUpdated) refundEvents.stream()
                .filter(e -> ResourceType.PAYMENT.equals(e.getResourceType()))
                .findFirst().get();
        assertThat(refundAvailabilityUpdated.getResourceExternalId(), is(charge.getExternalId()));
        assertThat(refundAvailabilityUpdated.getResourceType(), is(ResourceType.PAYMENT));
        assertThat(refundAvailabilityUpdated.getEventDetails(), is(instanceOf(RefundAvailabilityUpdatedEventDetails.class)));
    }

    @Test
    public void shouldCreatePaymentCreatedEventWithCorrectPayloadForPaymentCreatedStateTransition() throws Exception {
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withId(chargeEventEntityId)
                .build();
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, PaymentCreated.class);

        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2));
        PaymentCreated event = (PaymentCreated) events.get(0);
        assertThat(event, instanceOf(PaymentCreated.class));
        assertThat(event.getEventDetails(), instanceOf(PaymentCreatedEventDetails.class));
        assertThat(event.getResourceExternalId(), Is.is(chargeEventEntity.getChargeEntity().getExternalId()));

        RefundAvailabilityUpdated event2 = (RefundAvailabilityUpdated) events.get(1);
        assertThat(event2, instanceOf(RefundAvailabilityUpdated.class));
        assertThat(event2.getEventDetails(), instanceOf(RefundAvailabilityUpdatedEventDetails.class));
    }

    @Test
    public void shouldCreateEventWithNoPayloadForNonPayloadEventStateTransition() throws Exception {
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withId(chargeEventEntityId)
                .build();
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, CancelByExternalServiceSubmitted.class);

        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(1));
        CancelByExternalServiceSubmitted event = (CancelByExternalServiceSubmitted) events.get(0);
        assertThat(event, instanceOf(CancelByExternalServiceSubmitted.class));
        assertThat(event.getEventDetails(), instanceOf(EmptyEventDetails.class));
    }

    private Object[] statesAffectingRefundability() {
        return new Object[]{
                // non terminal events
                new Object[]{PaymentCreated.class, PaymentCreatedEventDetails.class},
                new Object[]{CaptureSubmitted.class, CaptureSubmittedEventDetails.class},
                new Object[]{GatewayErrorDuringAuthorisation.class, EmptyEventDetails.class},
                new Object[]{GatewayTimeoutDuringAuthorisation.class, EmptyEventDetails.class},
                new Object[]{UnexpectedGatewayErrorDuringAuthorisation.class, EmptyEventDetails.class},
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
                new Object[]{CancelledWithGatewayAfterAuthorisationError.class, EmptyEventDetails.class},
                new Object[]{CaptureAbandonedAfterTooManyRetries.class, EmptyEventDetails.class},
                new Object[]{CaptureConfirmed.class, CaptureConfirmedEventDetails.class},
                new Object[]{CaptureErrored.class, EmptyEventDetails.class},
                new Object[]{PaymentExpired.class, EmptyEventDetails.class},
        };
    }

    @Test
    @Parameters(method = "statesAffectingRefundability")
    public void shouldCreatedRefundAvailabilityUpdatedEventForTerminalAndOtherStatesAffectingRefundability(
            Class eventClass, Class eventDetailsClass) throws Exception {

        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withId(chargeEventEntityId)
                .build();
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, eventClass);

        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2));
        Event event1 = events.get(0);
        assertThat(event1, instanceOf(eventClass));
        assertThat(event1.getEventDetails(), instanceOf(eventDetailsClass));

        RefundAvailabilityUpdated event2 = (RefundAvailabilityUpdated) events.get(1);
        assertThat(event2, instanceOf(RefundAvailabilityUpdated.class));
        assertThat(event2.getEventDetails(), instanceOf(RefundAvailabilityUpdatedEventDetails.class));
    }

    @Test
    public void shouldCreatedARefundAvailabilityUpdatedEvent_ifPaymentIsHistoric() throws Exception {
        ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
        refundSummary.setStatus("available");
        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionId(charge.getExternalId());
        transaction.setPaymentProvider("sandbox");
        transaction.setRefundSummary(refundSummary);
        transaction.setAmount(charge.getAmount());
        transaction.setTotalAmount(charge.getAmount());
        transaction.setCreatedDate(Instant.now().toString());
        transaction.setGatewayAccountId(charge.getGatewayAccount().getId().toString());
        when(chargeService.findCharge(transaction.getTransactionId())).thenReturn(Optional.of(Charge.from(transaction)));

        RefundHistory refundErrorHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.REFUND_ERROR.getValue())
                .withUserExternalId("user_external_id")
                .withChargeExternalId(charge.getExternalId())
                .withAmount(charge.getAmount())
                .build();
        when(refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundErrorHistory.getExternalId(),
                refundErrorHistory.getStatus())
        ).thenReturn(Optional.of(refundErrorHistory));


        StateTransition refundStateTransition = new RefundStateTransition(
                refundErrorHistory.getExternalId(), refundErrorHistory.getStatus(), RefundError.class
        );
        List<Event> refundEvents = eventFactory.createEvents(refundStateTransition);

        assertThat(refundEvents.size(), is(2));

        RefundAvailabilityUpdated refundAvailabilityUpdated = (RefundAvailabilityUpdated) refundEvents.stream()
                .filter(e -> ResourceType.PAYMENT.equals(e.getResourceType()))
                .findFirst().get();
        assertThat(refundAvailabilityUpdated.getResourceExternalId(), is(transaction.getTransactionId()));
        assertThat(refundAvailabilityUpdated.getResourceType(), is(ResourceType.PAYMENT));
        assertThat(refundAvailabilityUpdated.getEventDetails(), is(instanceOf(RefundAvailabilityUpdatedEventDetails.class)));
    }

    @Test
    public void shouldCreatedCorrectEventForPaymentNotificationCreated() throws Exception {
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

        PaymentNotificationCreated event = (PaymentNotificationCreated) events.get(0);
        assertThat(event, is(instanceOf(PaymentNotificationCreated.class)));

        assertThat(event.getEventDetails(), instanceOf(PaymentNotificationCreatedEventDetails.class));
        assertThat(event.getResourceExternalId(), is(chargeEventEntity.getChargeEntity().getExternalId()));

        PaymentNotificationCreatedEventDetails eventDetails = (PaymentNotificationCreatedEventDetails) event.getEventDetails();
        assertThat(eventDetails.getGatewayTransactionId(), is(charge.getGatewayTransactionId()));
    }

    @Test
    public void shouldCreatedCorrectEventForBackfillRecreatedUserEmailCollectedEvent() throws Exception {
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

        BackfillerRecreatedUserEmailCollected event = (BackfillerRecreatedUserEmailCollected) events.get(0);
        assertThat(event, is(instanceOf(BackfillerRecreatedUserEmailCollected.class)));

        assertThat(event.getEventDetails(), instanceOf(UserEmailCollectedEventDetails.class));
        assertThat(event.getResourceExternalId(), is(chargeEventEntity.getChargeEntity().getExternalId()));

        UserEmailCollectedEventDetails eventDetails = (UserEmailCollectedEventDetails) event.getEventDetails();
        assertThat(eventDetails.getEmail(), is(charge.getEmail()));
    }

    @Test
    public void shouldCreatedCorrectEventForGatewayRequires3dsAuthorisationEvent() throws Exception {
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

        GatewayRequires3dsAuthorisation event = (GatewayRequires3dsAuthorisation) events.get(0);
        assertThat(event, is(instanceOf(GatewayRequires3dsAuthorisation.class)));

        assertThat(event.getEventDetails(), instanceOf(GatewayRequires3dsAuthorisationEventDetails.class));
        assertThat(event.getResourceExternalId(), is(chargeEventEntity.getChargeEntity().getExternalId()));

        GatewayRequires3dsAuthorisationEventDetails eventDetails = (GatewayRequires3dsAuthorisationEventDetails) event.getEventDetails();
        assertThat(eventDetails.getVersion3DS(), is("2.1.0"));
        assertThat(eventDetails.isRequires3DS(), is(true));
    }
}
