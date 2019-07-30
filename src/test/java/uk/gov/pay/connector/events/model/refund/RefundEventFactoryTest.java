package uk.gov.pay.connector.events.model.refund;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.events.eventdetails.charge.RefundAvailabilityUpdatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByUserEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithReferenceDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.events.model.charge.RefundAvailabilityUpdated;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.pact.RefundHistoryEntityFixture;
import uk.gov.pay.connector.queue.RefundStateTransition;
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
public class RefundEventFactoryTest {

    private final ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();

    @Mock
    private ChargeService chargeService;
    @Mock
    private RefundDao refundDao;
    @Mock
    private PaymentProviders paymentProviders;
    
    private RefundEventFactory refundEventFactory;
    
    @Before
    public void setUp() {
        when(chargeService.findChargeById(charge.getExternalId())).thenReturn(charge);
        
        PaymentProvider paymentProvider = new SandboxPaymentProvider();
        when(paymentProviders.byName(any(PaymentGatewayName.class))).thenReturn(paymentProvider);
        
        refundEventFactory = new RefundEventFactory(chargeService, refundDao, paymentProviders);
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
        
        
        RefundStateTransition refundStateTransition = new RefundStateTransition(
                refundCreatedHistory.getExternalId(), refundCreatedHistory.getStatus(), RefundCreatedByUser.class
        );
        List<Event> refundEvents = refundEventFactory.create(refundStateTransition);
        
        
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
        
        
        RefundStateTransition refundStateTransition = new RefundStateTransition(
                refundSubmittedHistory.getExternalId(), refundSubmittedHistory.getStatus(), RefundSubmitted.class
        );
        List<Event> refundEvents = refundEventFactory.create(refundStateTransition);

        
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
        
        
        RefundStateTransition refundStateTransition = new RefundStateTransition(
                refundSucceededHistory.getExternalId(), refundSucceededHistory.getStatus(), RefundSucceeded.class
        );
        List<Event> refundEvents = refundEventFactory.create(refundStateTransition);

        
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
        
        
        RefundStateTransition refundStateTransition = new RefundStateTransition(
                refundErrorHistory.getExternalId(), refundErrorHistory.getStatus(), RefundError.class
        );
        List<Event> refundEvents = refundEventFactory.create(refundStateTransition);

        
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
}
