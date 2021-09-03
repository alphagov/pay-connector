package uk.gov.pay.connector.common.model.domain;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.events.eventdetails.EmptyEventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.UnspecifiedEvent;
import uk.gov.pay.connector.events.model.charge.AuthorisationCancelled;
import uk.gov.pay.connector.events.model.charge.AuthorisationRejected;
import uk.gov.pay.connector.events.model.charge.CaptureAbandonedAfterTooManyRetries;
import uk.gov.pay.connector.events.model.charge.CaptureErrored;
import uk.gov.pay.connector.events.model.charge.CaptureSubmitted;
import uk.gov.pay.connector.events.model.charge.GatewayErrorDuringAuthorisation;
import uk.gov.pay.connector.events.model.charge.PaymentExpired;
import uk.gov.pay.connector.events.model.charge.PaymentNotificationCreated;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.UNDEFINED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;

@RunWith(JUnitParamsRunner.class)
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
        assertThat(actual, hasItem(Triple.of(CREATED, EXPIRED, PaymentExpired.class.getSimpleName())));
    }

    @Test
    public void isValidTransition_indicatesValidAndInvalidTransition() {
        assertThat(PaymentGatewayStateTransitions.isValidTransition(CAPTURE_READY, CAPTURE_SUBMITTED, new UnspecifiedEvent()), is(true));
        assertThat(PaymentGatewayStateTransitions.isValidTransition(CREATED, AUTHORISATION_READY, new UnspecifiedEvent()), is(false));
    }

    @Test
    public void isValidTransition_deniesTransitionWithInvalidEvent() {
        assertThat(PaymentGatewayStateTransitions.isValidTransition(CAPTURE_READY, CAPTURE_SUBMITTED, new CaptureSubmitted("id", true, "a", new EmptyEventDetails(), ZonedDateTime.now())), is(true));
        assertThat(PaymentGatewayStateTransitions.isValidTransition(CAPTURE_READY, CAPTURE_SUBMITTED, new CaptureErrored("id", true, "a", ZonedDateTime.now())), is(false));
    }

    @Test
    public void getEventTransitionFor_returnsEventForModelledTypedEvent() {
        Optional<Class<Event>> eventClassType = transitions.getEventForTransition(CAPTURE_APPROVED, CAPTURE_ERROR);
        assertThat(eventClassType.get(), is(CaptureAbandonedAfterTooManyRetries.class));
    }

    @Test
    public void getEventTransitionFor_returnsEmptyForInvalidTransition() {
        Optional<Class<Event>> eventClassType = transitions.getEventForTransition(CAPTURE_APPROVED, CREATED);
        assertThat(eventClassType, is(Optional.empty()));
    }

    @Test
    public void getEventTransitionFor_returnsEmptyForUnmodelledEvent() {
        Optional<Class<Event>> eventClassType = transitions.getEventForTransition(CAPTURE_APPROVED, CAPTURE_READY);
        assertThat(eventClassType, is(Optional.empty()));
    }

    private Object[] intermediateStatesForTransitions() {
        return new Object[]{
                new Object[]{AUTHORISATION_READY, ENTERING_CARD_DETAILS, AUTHORISATION_3DS_REQUIRED},
                new Object[]{AUTHORISATION_READY, ENTERING_CARD_DETAILS, AUTHORISATION_ABORTED},
                new Object[]{AUTHORISATION_READY, ENTERING_CARD_DETAILS, AUTHORISATION_CANCELLED},
                new Object[]{AUTHORISATION_READY, ENTERING_CARD_DETAILS, AUTHORISATION_ERROR},
                new Object[]{AUTHORISATION_READY, ENTERING_CARD_DETAILS, AUTHORISATION_REJECTED},
                new Object[]{AUTHORISATION_READY, ENTERING_CARD_DETAILS, AUTHORISATION_SUBMITTED},
                new Object[]{AUTHORISATION_READY, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS},
                new Object[]{AUTHORISATION_READY, ENTERING_CARD_DETAILS, AUTHORISATION_TIMEOUT},
                new Object[]{AUTHORISATION_READY, ENTERING_CARD_DETAILS, AUTHORISATION_UNEXPECTED_ERROR},

                new Object[]{AUTHORISATION_3DS_READY, AUTHORISATION_3DS_REQUIRED, AUTHORISATION_ERROR},
                new Object[]{AUTHORISATION_3DS_READY, AUTHORISATION_3DS_REQUIRED, AUTHORISATION_REJECTED},
                new Object[]{AUTHORISATION_3DS_READY, AUTHORISATION_3DS_REQUIRED, AUTHORISATION_SUCCESS},

                new Object[]{EXPIRE_CANCEL_READY, AUTHORISATION_3DS_REQUIRED, EXPIRED},

                new Object[]{CAPTURE_READY, AUTHORISATION_SUCCESS, CAPTURE_APPROVED_RETRY},
                new Object[]{CAPTURE_READY, AUTHORISATION_SUCCESS, CAPTURE_ERROR},
                new Object[]{CAPTURE_READY, AUTHORISATION_SUCCESS, CAPTURE_SUBMITTED},
                new Object[]{CAPTURE_READY, AUTHORISATION_SUCCESS, CAPTURED},
                new Object[]{CAPTURE_READY, CAPTURE_APPROVED, CAPTURE_APPROVED_RETRY},
                new Object[]{CAPTURE_READY, CAPTURE_APPROVED, CAPTURE_ERROR},
                new Object[]{CAPTURE_READY, CAPTURE_APPROVED, CAPTURE_SUBMITTED},
                new Object[]{CAPTURE_READY, CAPTURE_APPROVED, CAPTURED},
                new Object[]{CAPTURE_READY, CAPTURE_APPROVED_RETRY, CAPTURE_SUBMITTED},

                new Object[]{USER_CANCEL_READY, AUTHORISATION_SUCCESS, USER_CANCEL_ERROR},
                new Object[]{USER_CANCEL_READY, AUTHORISATION_SUCCESS, USER_CANCELLED},
                new Object[]{USER_CANCEL_READY, AUTHORISATION_SUCCESS, USER_CANCEL_SUBMITTED},

                new Object[]{SYSTEM_CANCEL_READY, AUTHORISATION_SUCCESS, SYSTEM_CANCEL_SUBMITTED},
                new Object[]{SYSTEM_CANCEL_READY, AUTHORISATION_SUCCESS, SYSTEM_CANCELLED},
                new Object[]{SYSTEM_CANCEL_READY, AUTHORISATION_SUCCESS, SYSTEM_CANCEL_ERROR},
                new Object[]{SYSTEM_CANCEL_READY, AWAITING_CAPTURE_REQUEST, SYSTEM_CANCEL_SUBMITTED},
                new Object[]{SYSTEM_CANCEL_READY, AWAITING_CAPTURE_REQUEST, SYSTEM_CANCELLED},
                new Object[]{SYSTEM_CANCEL_READY, AWAITING_CAPTURE_REQUEST, SYSTEM_CANCEL_ERROR},
        };
    }

    @Test
    @Parameters(method = "intermediateStatesForTransitions")
    public void getIntermediateChargeStatusShouldDeriveStatusCorrectly(ChargeStatus expectedIntermediateStatus,
                                                                       ChargeStatus fromStatus,
                                                                       ChargeStatus toStatus) {
        Optional<ChargeStatus> actualStatus = transitions.getIntermediateChargeStatus(fromStatus, toStatus);
        assertThat(actualStatus.get(), is(expectedIntermediateStatus));
    }

    @Test
    public void getEventTransitionForUndefinedToPaymentNotificationCreated_returnsEventForModelledTypedEvent() {
        Optional<Class<Event>> eventClassType = transitions.getEventForTransition(UNDEFINED, PAYMENT_NOTIFICATION_CREATED);
        assertThat(eventClassType.get(), is(PaymentNotificationCreated.class));
    }

    @Test
    public void getEventTransitionForPaymentNotificationCreated_returnsEventForModelledTypedEvent() {
        Optional<Class<Event>> eventClassType = transitions.getEventForTransition(PAYMENT_NOTIFICATION_CREATED, CAPTURE_SUBMITTED);
        assertThat(eventClassType.get(), is(CaptureSubmitted.class));

        eventClassType = transitions.getEventForTransition(PAYMENT_NOTIFICATION_CREATED, AUTHORISATION_REJECTED);
        assertThat(eventClassType.get(), is(AuthorisationRejected.class));

        eventClassType = transitions.getEventForTransition(PAYMENT_NOTIFICATION_CREATED, AUTHORISATION_ERROR);
        assertThat(eventClassType.get(), is(GatewayErrorDuringAuthorisation.class));

        eventClassType = transitions.getEventForTransition(PAYMENT_NOTIFICATION_CREATED, AUTHORISATION_CANCELLED);
        assertThat(eventClassType.get(), is(AuthorisationCancelled.class));
    }

    @Test
    public void getNextStatus_returnsAListOfFollowingStatuses() {
        assertThat(transitions.getNextStatus(AUTHORISATION_READY), hasItems(AUTHORISATION_ABORTED,
                AUTHORISATION_SUCCESS,
                AUTHORISATION_REJECTED,
                AUTHORISATION_ERROR,
                AUTHORISATION_TIMEOUT,
                AUTHORISATION_UNEXPECTED_ERROR,
                AUTHORISATION_3DS_REQUIRED,
                AUTHORISATION_CANCELLED,
                AUTHORISATION_SUBMITTED));
    }
}
