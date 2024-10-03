package uk.gov.pay.connector.queue.tasks.handlers;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class QueryAndUpdatePaymentInSubmittedStateTaskHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryAndUpdatePaymentInSubmittedStateTaskHandler.class);
    private final ChargeService chargeService;
    private final QueryService queryService;

    @Inject
    public QueryAndUpdatePaymentInSubmittedStateTaskHandler(ChargeService chargeService,
                                                            QueryService queryService) {
        this.chargeService = chargeService;
        this.queryService = queryService;
    }

    public void process(PaymentTaskData paymentTaskData) {
        ChargeEntity chargeEntity = chargeService.findChargeByExternalId(paymentTaskData.getPaymentExternalId());

        try {
            ChargeQueryResponse chargeGatewayStatus = queryService.getChargeGatewayStatus(chargeEntity);

            if (chargeGatewayStatus.foundCharge()) {
                chargeGatewayStatus.getMappedStatus().ifPresent(newChargeStatus -> {
                    if (chargeEntity.getStatus().equals(CAPTURE_SUBMITTED.getValue()) &&
                            newChargeStatus == CAPTURED) {
                        chargeService.transitionChargeState(chargeEntity, newChargeStatus);
                    } else {
                        LOGGER.info("Skipped updating charge as it is not in CAPTURED state on gateway");
                    }
                });
            } else {
                LOGGER.warn("Charge not found on gateway",
                        kv(PROVIDER, chargeEntity.getPaymentProvider()));
            }
        } catch (GatewayException | UnsupportedOperationException e) {
            LOGGER.warn("Error querying charge with gateway",
                    kv(PROVIDER, chargeEntity.getPaymentProvider()),
                    kv("exception", e.getMessage())
            );
        }
    }

}
