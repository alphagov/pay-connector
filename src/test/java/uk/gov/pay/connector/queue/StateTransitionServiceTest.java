package uk.gov.pay.connector.queue;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.events.model.charge.PaymentStarted;
import uk.gov.pay.connector.events.model.refund.RefundCreatedByUser;
import uk.gov.pay.connector.queue.statetransition.PaymentStateTransition;
import uk.gov.pay.connector.queue.statetransition.RefundStateTransition;
import uk.gov.pay.connector.queue.statetransition.StateTransitionQueue;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.Instant;
import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.events.model.ResourceType.PAYMENT;
import static uk.gov.pay.connector.events.model.ResourceType.REFUND;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.pact.ChargeEventEntityFixture.aValidChargeEventEntity;
import static uk.gov.pay.connector.pact.RefundHistoryEntityFixture.aValidRefundHistoryEntity;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;

@ExtendWith(MockitoExtension.class)
class StateTransitionServiceTest {

    StateTransitionService stateTransitionService;

    @Mock
    StateTransitionQueue mockStateTransitionQueue;
    @Mock
    EventService mockEventService;
    @Mock
    Environment environment;
    @Mock
    MetricRegistry metricRegistry;
    @Mock
    Counter counter;
    @Mock
    Meter meter;

    @BeforeEach
    void setUp() {
        when(environment.metrics()).thenReturn(metricRegistry);
        when(metricRegistry.counter(anyString())).thenReturn(counter);
        stateTransitionService = new StateTransitionService(mockStateTransitionQueue, mockEventService, environment);
    }

    @Test
    void shouldOfferPaymentStateTransitionMessageForAValidStateTransitionIntoNonLockingState() {
        when(metricRegistry.counter(anyString())).thenReturn(counter);
        when(metricRegistry.meter(anyString())).thenReturn(meter);
        ChargeEventEntity chargeEvent = aValidChargeEventEntity()
                .withId(100L)
                .build();

        stateTransitionService.offerPaymentStateTransition("external-id", ChargeStatus.CREATED, ENTERING_CARD_DETAILS, chargeEvent);
        ArgumentCaptor<PaymentStateTransition> paymentStateTransitionArgumentCaptor = ArgumentCaptor.forClass(PaymentStateTransition.class);
        verify(mockStateTransitionQueue).offer(paymentStateTransitionArgumentCaptor.capture());

        assertThat(paymentStateTransitionArgumentCaptor.getValue().getChargeEventId(), is(100L));
        assertThat(paymentStateTransitionArgumentCaptor.getValue().getStateTransitionEventClass(), is(PaymentStarted.class));

        verify(mockEventService).recordOfferedEvent(PAYMENT, "external-id", "PAYMENT_STARTED", chargeEvent.getUpdated().toInstant());
    }

    @Test
    void shouldNotOfferStateTransitionMessageForAValidStateTransitionIntoLockingState() {
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        stateTransitionService.offerPaymentStateTransition("external-id", ChargeStatus.CREATED, AUTHORISATION_READY, chargeEvent);

        verifyNoMoreInteractions(mockStateTransitionQueue);
        verifyNoMoreInteractions(mockEventService);
    }

    @Test
    void shouldOfferRefundStateTransitionMessageForAValidStateTransition() {
        RefundEntity refundEntity = aValidRefundEntity()
                .withExternalId("external-id")
                .withStatus(CREATED)
                .build();

        stateTransitionService.offerRefundStateTransition(refundEntity, CREATED);

        ArgumentCaptor<RefundStateTransition> refundStateTransitionArgumentCaptor = ArgumentCaptor.forClass(RefundStateTransition.class);
        verify(mockStateTransitionQueue).offer(refundStateTransitionArgumentCaptor.capture());

        assertThat(refundStateTransitionArgumentCaptor.getValue().getRefundExternalId(), is(refundEntity.getExternalId()));
        assertThat(refundStateTransitionArgumentCaptor.getValue().getStateTransitionEventClass(), is(RefundCreatedByUser.class));

        ArgumentCaptor<Instant> eventDateArgumentCaptor = forClass(Instant.class);
        ArgumentCaptor<ResourceType> resourceTypeCaptor = forClass(ResourceType.class);
        ArgumentCaptor<String> externalIdCaptor = forClass(String.class);
        ArgumentCaptor<String> eventTypeCaptor = forClass(String.class);

        verify(mockEventService).recordOfferedEvent(resourceTypeCaptor.capture(), externalIdCaptor.capture(),
                eventTypeCaptor.capture(), eventDateArgumentCaptor.capture());

        assertThat(resourceTypeCaptor.getValue(), is(REFUND));
        assertThat(externalIdCaptor.getValue(), is("external-id"));
        assertThat(eventTypeCaptor.getValue(), is("REFUND_CREATED_BY_USER"));
        assertThat(eventDateArgumentCaptor.getValue(), is(notNullValue()));
    }

    @Test
    void offerStateTransition_shouldOfferAndRecordEvent() {
        RefundStateTransition refundStateTransition = new RefundStateTransition("external-id", CREATED, RefundCreatedByUser.class);
        RefundHistory refundHistory = aValidRefundHistoryEntity()
                .withExternalId("external-id")
                .build();
        ChargeEntity chargeEntity = aValidChargeEntity().build();
        RefundCreatedByUser refundCreatedByUser = RefundCreatedByUser.from(refundHistory, Charge.from(chargeEntity) );
        ZonedDateTime doNotEmitRetryUntil = now(UTC);

        stateTransitionService.offerStateTransition(refundStateTransition, refundCreatedByUser, doNotEmitRetryUntil);

        ArgumentCaptor<RefundStateTransition> refundStateTransitionArgumentCaptor = ArgumentCaptor.forClass(RefundStateTransition.class);
        verify(mockStateTransitionQueue).offer(refundStateTransitionArgumentCaptor.capture());

        assertThat(refundStateTransitionArgumentCaptor.getValue().getRefundExternalId(), is(refundHistory.getExternalId()));
        assertThat(refundStateTransitionArgumentCaptor.getValue().getStateTransitionEventClass(), is(RefundCreatedByUser.class));

        verify(mockEventService).recordOfferedEvent(REFUND, refundHistory.getExternalId(),
                "REFUND_CREATED_BY_USER", refundHistory.getHistoryStartDate().toInstant(), doNotEmitRetryUntil);
    }
}
