package uk.gov.pay.connector.common.model.domain;

import org.apache.commons.lang3.tuple.Triple;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;

public class PaymentGatewayStateTransitionsTest {
    @Test
    public void allStatuses_hasEveryValidChargeStatus() {
        Set<ChargeStatus> expected = new HashSet<>(Arrays.asList(ChargeStatus.values()));
        assertThat(PaymentGatewayStateTransitions.allStatuses(), is(expected));
    }

    @Test
    public void allTransitions_containsAValidTransitionAnnotatedWithEventDescription() {
        Set<Triple<ChargeStatus, ChargeStatus, String>> actual = PaymentGatewayStateTransitions.allTransitions();
        assertThat(actual, hasItem(Triple.of(CREATED, EXPIRED, "ChargeExpiryService")));
    }

    @Test
    public void isValidTransition_indicatesValidAndInvalidTransition() {
        assertThat(PaymentGatewayStateTransitions.isValidTransition(CAPTURE_READY, CAPTURE_SUBMITTED), is(true));
        assertThat(PaymentGatewayStateTransitions.isValidTransition(CREATED, AUTHORISATION_READY), is(false));
    }
}
