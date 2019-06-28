package uk.gov.pay.connector.common.model.domain;

import org.apache.commons.lang3.tuple.Triple;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.events.CaptureError;
import uk.gov.pay.connector.events.CaptureSubmitted;
import uk.gov.pay.connector.events.UnspecifiedEvent;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;

public class PaymentGatewayStateTransitionsTest {
    PaymentGatewayStateTransitions transitions = PaymentGatewayStateTransitions.getInstance();

    @Test
    public void allStatuses_hasEveryValidChargeStatus() {
        Set<ChargeStatus> expected = new HashSet<>(Arrays.asList(ChargeStatus.values()));
        assertThat(transitions.allStatuses(), is(expected));
    }

    @Test
    public void allTransitions_containsAValidTransitionAnnotatedWithEventDescription() {
        Set<Triple<ChargeStatus, ChargeStatus, String>> actual = transitions.allTransitions();
        assertThat(actual, hasItem(Triple.of(CREATED, EXPIRED, "PaymentExpiredEvent")));
    }

    @Test
    public void isValidTransition_indicatesValidAndInvalidTransition() {
        assertThat(PaymentGatewayStateTransitions.isValidTransition(CAPTURE_READY, CAPTURE_SUBMITTED, new UnspecifiedEvent()), is(true));
        assertThat(PaymentGatewayStateTransitions.isValidTransition(CREATED, AUTHORISATION_READY, new UnspecifiedEvent()), is(false));
    }

    @Test
    public void isValidTransition_deniesTransitionWithInvalidEvent() {
        assertThat(PaymentGatewayStateTransitions.isValidTransition(CAPTURE_READY, CAPTURE_SUBMITTED, new CaptureSubmitted("a", ZonedDateTime.now())), is(true));
        assertThat(PaymentGatewayStateTransitions.isValidTransition(CAPTURE_READY, CAPTURE_SUBMITTED, new CaptureError("a", ZonedDateTime.now())), is(false));
    }
}
