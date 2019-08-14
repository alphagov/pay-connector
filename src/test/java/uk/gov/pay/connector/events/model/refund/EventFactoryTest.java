package uk.gov.pay.connector.events.model.refund;

import org.hamcrest.core.Is;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.EmptyEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureConfirmedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.RefundAvailabilityUpdatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByUserEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithReferenceDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.EventFactory;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.events.model.charge.CancelByExternalServiceSubmitted;
import uk.gov.pay.connector.events.model.charge.CaptureAbandonedAfterTooManyRetries;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmed;
import uk.gov.pay.connector.events.model.charge.CaptureSubmitted;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.RefundAvailabilityUpdated;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.pact.ChargeEventEntityFixture;
import uk.gov.pay.connector.pact.RefundHistoryEntityFixture;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.RefundStateTransition;
import uk.gov.pay.connector.queue.StateTransition;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventFactoryTest {

    private final ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();

    @Mock
    private ChargeService chargeService;
    @Mock
    private RefundDao refundDao;
    @Mock
    private ChargeEventDao chargeEventDao;
    @Mock
    private PaymentProviders paymentProviders;
    
    private EventFactory eventFactory;
    
    @Before
    public void setUp() {
        when(chargeService.findChargeById(charge.getExternalId())).thenReturn(charge);
        
        PaymentProvider paymentProvider = new SandboxPaymentProvider();
        when(paymentProviders.byName(any(PaymentGatewayName.class))).thenReturn(paymentProvider);
        
        eventFactory = new EventFactory(chargeService, refundDao, chargeEventDao, paymentProviders);
    }
    
    @Test
    public void shouldCreateCorrectEventsFromRefundCreatedStateTransition() throws Exception {
        RefundHistory refundCreatedHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity()
                .withStatus(RefundStatus.CREATED.getValue())
                .withUserExternalId("user_external_id")
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
        assertThat(refundCreatedByUser.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(((RefundCreatedByUserEventDetails) refundCreatedByUser.getEventDetails()).getRefundedBy(), is("user_external_id"));
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
        assertThat(refundSubmitted.getEventDetails(), is(instanceOf(RefundEventWithReferenceDetails.class)));
    }

    @Test
    public void shouldCreateCorrectEventsFromRefundSucceededStateTransition() throws Exception {
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
        assertThat(refundSucceeded.getEventDetails(), is(instanceOf(RefundEventWithReferenceDetails.class)));
    }

    @Test
    public void shouldCreateCorrectEventsFromRefundErrorStateTransition() throws Exception {
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

        RefundError refundError = (RefundError) refundEvents.stream()
                .filter(e -> ResourceType.REFUND.equals(e.getResourceType()))
                .findFirst().get();
        assertThat(refundError.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(((RefundEventWithReferenceDetails) refundError.getEventDetails()).getReference(), is(refundErrorHistory.getReference()));
        assertThat(refundError.getResourceType(), is(ResourceType.REFUND));
        assertThat(refundError.getEventDetails(), is(instanceOf(RefundEventWithReferenceDetails.class)));

        RefundAvailabilityUpdated refundAvailabilityUpdated = (RefundAvailabilityUpdated) refundEvents.stream()
                .filter(e -> ResourceType.PAYMENT.equals(e.getResourceType()))
                .findFirst().get();
        assertThat(refundAvailabilityUpdated.getResourceExternalId(), is(charge.getExternalId()));
        assertThat(refundAvailabilityUpdated.getResourceType(), is(ResourceType.PAYMENT));
        assertThat(refundAvailabilityUpdated.getEventDetails(), is(instanceOf(RefundAvailabilityUpdatedEventDetails.class)));
    }

    @Test
    public void shouldCreatePaymentCreatedEventWithCorrectPayloadForPaymentCreatedStateTransition() throws Exception{
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
        Assert.assertThat(event, instanceOf(PaymentCreated.class));
        Assert.assertThat(event.getEventDetails(), instanceOf(PaymentCreatedEventDetails.class));
        Assert.assertThat(event.getResourceExternalId(), Is.is(chargeEventEntity.getChargeEntity().getExternalId()));

        RefundAvailabilityUpdated event2 = (RefundAvailabilityUpdated) events.get(1);
        Assert.assertThat(event2, instanceOf(RefundAvailabilityUpdated.class));
        Assert.assertThat(event2.getEventDetails(), instanceOf(RefundAvailabilityUpdatedEventDetails.class));
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
        Assert.assertThat(event, instanceOf(CancelByExternalServiceSubmitted.class));
        Assert.assertThat(event.getEventDetails(), instanceOf(EmptyEventDetails.class));
    }

    @Test
    public void shouldCreatedARefundAvailabilityUpdatedEventForCaptureConfirmedStateTransition() throws Exception {
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withId(chargeEventEntityId)
                .build();
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, CaptureConfirmed.class);

        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2));
        CaptureConfirmed event1 = (CaptureConfirmed) events.get(0);
        Assert.assertThat(event1, instanceOf(CaptureConfirmed.class));
        Assert.assertThat(event1.getEventDetails(), instanceOf(CaptureConfirmedEventDetails.class));

        RefundAvailabilityUpdated event2 = (RefundAvailabilityUpdated) events.get(1);
        Assert.assertThat(event2, instanceOf(RefundAvailabilityUpdated.class));
        Assert.assertThat(event2.getEventDetails(), instanceOf(RefundAvailabilityUpdatedEventDetails.class));
    }
    
    @Test
    public void shouldCreatedARefundAvailabilityUpdatedEventForCaptureSubmittedStateTransition() throws Exception {
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withId(chargeEventEntityId)
                .build();
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, CaptureSubmitted.class);

        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2));
        CaptureSubmitted event1 = (CaptureSubmitted) events.get(0);
        Assert.assertThat(event1, instanceOf(CaptureSubmitted.class));

        RefundAvailabilityUpdated event2 = (RefundAvailabilityUpdated) events.get(1);
        Assert.assertThat(event2, instanceOf(RefundAvailabilityUpdated.class));
        Assert.assertThat(event2.getEventDetails(), instanceOf(RefundAvailabilityUpdatedEventDetails.class));
    }

    @Test
    public void shouldCreatedARefundAvailabilityUpdatedEventForEventThatLeadsToTerminalState() throws Exception {
        Long chargeEventEntityId = 100L;
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withCharge(charge)
                .withId(chargeEventEntityId)
                .build();
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventEntityId)).thenReturn(
                Optional.of(chargeEventEntity)
        );
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventEntityId, CaptureAbandonedAfterTooManyRetries.class);

        List<Event> events = eventFactory.createEvents(paymentStateTransition);

        assertThat(events.size(), is(2));
        CaptureAbandonedAfterTooManyRetries event = (CaptureAbandonedAfterTooManyRetries) events.get(0);
        Assert.assertThat(event, instanceOf(CaptureAbandonedAfterTooManyRetries.class));
        Assert.assertThat(event.getEventDetails(), instanceOf(EmptyEventDetails.class));

        RefundAvailabilityUpdated event2 = (RefundAvailabilityUpdated) events.get(1);
        Assert.assertThat(event2, instanceOf(RefundAvailabilityUpdated.class));
        Assert.assertThat(event2.getEventDetails(), instanceOf(RefundAvailabilityUpdatedEventDetails.class));
    }
}
