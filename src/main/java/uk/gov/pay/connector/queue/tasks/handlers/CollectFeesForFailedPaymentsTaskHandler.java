package uk.gov.pay.connector.queue.tasks.handlers;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.exception.EventCreationException;
import uk.gov.pay.connector.events.model.charge.FeeIncurredEvent;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.service.payments.logging.LoggingKeys.LEDGER_EVENT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class CollectFeesForFailedPaymentsTaskHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectFeesForFailedPaymentsTaskHandler.class);

    private final StripePaymentProvider stripePaymentProvider;
    private final ChargeService chargeService;
    private final EventService eventService;
    private final Clock clock;

    @Inject
    public CollectFeesForFailedPaymentsTaskHandler(StripePaymentProvider stripePaymentProvider,
                                                   ChargeService chargeService,
                                                   EventService eventService,
                                                   Clock clock) {
        this.stripePaymentProvider = stripePaymentProvider;
        this.chargeService = chargeService;
        this.eventService = eventService;
        this.clock = clock;
    }
    
    @Transactional
    public void collectAndPersistFees(PaymentTaskData paymentTaskData) throws GatewayException {
        ChargeEntity charge = chargeService.findChargeByExternalId(paymentTaskData.getPaymentExternalId());
        List<Fee> fees = stripePaymentProvider.calculateAndTransferFeesForFailedPayments(charge);

        Instant now = clock.instant();
        fees.stream().map(fee -> new FeeEntity(charge, now, fee)).forEach(charge::addFee);

        emitFeeEvent(charge);
    }

    private void emitFeeEvent(ChargeEntity charge) {
        try {
            FeeIncurredEvent event = FeeIncurredEvent.from(charge);
            eventService.emitAndRecordEvent(event);
            LOGGER.info("Fee incurred event sent to event queue.",
                    kv(LEDGER_EVENT_TYPE, event.getEventType()));
        } catch (EventCreationException e) {
            LOGGER.warn("Failed to create fee incurred event [{}], exception: [{}]", charge.getExternalId(), e.getMessage());
        }
    }
}
