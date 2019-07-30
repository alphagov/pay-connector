package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.events.eventdetails.charge.RefundAvailabilityUpdatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithReferenceDetails;
import uk.gov.pay.connector.events.exception.StateTransitionMessageProcessException;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.RefundAvailabilityUpdated;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.queue.RefundStateTransition;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RefundEventFactory {
    private final ChargeService chargeService;
    private final RefundDao refundDao;
    private final PaymentProviders paymentProviders;
    private static final List<Class<? extends RefundEvent>> eventsAffectingRefundAvailability = List.of(
            RefundCreatedByUser.class,
            RefundCreatedByService.class,
            RefundError.class
    );

    @Inject
    public RefundEventFactory(ChargeService chargeService, RefundDao refundDao, PaymentProviders paymentProviders) {
        this.chargeService = chargeService;
        this.refundDao = refundDao;
        this.paymentProviders = paymentProviders;
    }

    public List<Event> create(RefundStateTransition refundStateTransition) throws StateTransitionMessageProcessException {
        RefundHistory refundHistory = refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundStateTransition.getRefundExternalId(),
                refundStateTransition.getRefundStatus())
                .orElseThrow(() -> new StateTransitionMessageProcessException(refundStateTransition.getIdentifier()));

        Event refundEvent = createRefundEvent(refundHistory, refundStateTransition.getStateTransitionEventClass());
        Optional<Event> refundAvailabilityEvent = createRefundAvailabilityUpdatedEvent(
                refundHistory, refundStateTransition.getStateTransitionEventClass());

        return List.of(Optional.of(refundEvent), refundAvailabilityEvent)
                .stream()
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    private Event createRefundEvent(RefundHistory refundHistory, Class<? extends RefundEvent> eventClass) {
        try {
            if (eventClass == RefundCreatedByService.class) {
                return RefundCreatedByService.from(refundHistory);
            } else if (eventClass == RefundCreatedByUser.class) {
                return RefundCreatedByUser.from(refundHistory);
            } else {
                return eventClass.getConstructor(String.class, String.class, RefundEventWithReferenceDetails.class, ZonedDateTime.class).newInstance(
                        refundHistory.getExternalId(),
                        refundHistory.getChargeEntity().getExternalId(),
                        new RefundEventWithReferenceDetails(refundHistory.getReference()),
                        refundHistory.getHistoryStartDate()
                );
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException(String.format("Could not construct refund event: %s", eventClass));
        }
    }

    private Optional<Event> createRefundAvailabilityUpdatedEvent(
            RefundHistory refundHistory, Class<? extends RefundEvent> eventClass) {
        if (eventsAffectingRefundAvailability.contains(eventClass)) {
            return Optional.ofNullable(chargeService.findChargeById(refundHistory.getChargeEntity().getExternalId()))
                    .map(charge -> new RefundAvailabilityUpdated(
                                charge.getExternalId(),
                                RefundAvailabilityUpdatedEventDetails.from(
                                        charge,
                                        paymentProviders
                                                .byName(charge.getPaymentGatewayName())
                                                .getExternalChargeRefundAvailability(charge)
                                ),
                                refundHistory.getHistoryStartDate()
                        )
                    );
        }

        return Optional.empty();
    }

}
