package uk.gov.pay.connector.queue.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.eventdetails.charge.FeeIncurredEventDetails;
import uk.gov.pay.connector.events.model.charge.FeeIncurredEvent;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.queue.tasks.handlers.CollectFeesForFailedPaymentsTaskHandler;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.FeeType.RADAR;
import static uk.gov.pay.connector.charge.model.domain.FeeType.THREE_D_S;

@ExtendWith(MockitoExtension.class)
class CollectFeesForFailedPaymentsTaskHandlerTest {

    @Mock
    private ChargeService chargeService;

    @Mock
    private StripePaymentProvider stripePaymentProvider;

    @Mock
    private EventService eventService;

    @Captor
    private ArgumentCaptor<FeeIncurredEvent> feeIncurredEventArgumentCaptor;

    private static final InstantSource instantSource = InstantSource.fixed(Instant.parse("2020-01-01T10:10:10.100Z"));

    private final String chargeExternalId = "a-charge-external-id";

    private final ChargeEntity charge = aValidChargeEntity()
            .withExternalId(chargeExternalId)
            .withStatus(ChargeStatus.EXPIRED)
            .build();

    private CollectFeesForFailedPaymentsTaskHandler collectFeesForFailedPaymentsTaskHandler;

    @BeforeEach
    void setUp() {
        collectFeesForFailedPaymentsTaskHandler = new CollectFeesForFailedPaymentsTaskHandler(stripePaymentProvider, chargeService, eventService, instantSource);
        when(chargeService.findChargeByExternalId(chargeExternalId)).thenReturn(charge);
    }

    @Test
    void shouldPersistFeesAndEmitEvent() throws Exception {
        var paymentTaskData = new PaymentTaskData(chargeExternalId);

        List<Fee> fees = List.of(
                Fee.of(RADAR, 6L),
                Fee.of(THREE_D_S, 7L)
        );
        when(stripePaymentProvider.calculateAndTransferFeesForFailedPayments(charge)).thenReturn(fees);

        collectFeesForFailedPaymentsTaskHandler.collectAndPersistFees(paymentTaskData);

        assertThat(charge.getFees(), hasSize(2));
        assertThat(charge.getFees(), containsInAnyOrder(
                allOf(
                        hasProperty("amountCollected", is(6L)),
                        hasProperty("feeType", is(RADAR)),
                        hasProperty("createdDate", is(instantSource.instant()))
                ),
                allOf(
                        hasProperty("amountCollected", is(7L)),
                        hasProperty("feeType", is(THREE_D_S)),
                        hasProperty("createdDate", is(instantSource.instant()))
                )
        ));
        assertThat(charge.getNetAmount(), is(Optional.of(-13L)));

        verify(eventService).emitAndRecordEvent(feeIncurredEventArgumentCaptor.capture());
        FeeIncurredEventDetails eventDetails = (FeeIncurredEventDetails) feeIncurredEventArgumentCaptor.getValue().getEventDetails();
        assertThat(eventDetails.getFee(), is(13L));
        assertThat(eventDetails.getNetAmount(), is(-13L));
        assertThat(eventDetails.getFeeBreakdown(), is(fees));
    }
}
