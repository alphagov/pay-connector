package uk.gov.pay.connector.charge.service;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_CHARGE_MISSING;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;

public class EpdqAuthorisationErrorGatewayCleanupService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    static final String CLEANUP_SUCCESS = "cleanup-success";
    static final String CLEANUP_FAILED = "cleanup-failed";

    private final ChargeDao chargeDao;
    private final ChargeService chargeService;
    private final QueryService queryService;
    private final PaymentProviders providers;

    @Inject
    public EpdqAuthorisationErrorGatewayCleanupService(ChargeDao chargeDao,
                                                       ChargeService chargeService,
                                                       QueryService queryService,
                                                       PaymentProviders providers) {
        this.chargeDao = chargeDao;
        this.chargeService = chargeService;
        this.queryService = queryService;
        this.providers = providers;
    }

    public Map<String, Integer> sweepAndCleanupAuthorisationErrors(int limit) {
        List<ChargeEntity> chargesToCleanUp = chargeDao.findWithPaymentProviderAndStatusIn(EPDQ.getName(), List.of(
                AUTHORISATION_ERROR,
                AUTHORISATION_TIMEOUT,
                AUTHORISATION_UNEXPECTED_ERROR
        ), limit);

        logger.info("Found {} epdq charges to clean up.", chargesToCleanUp.size());
        
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        chargesToCleanUp.forEach(chargeEntity -> {
            try {
                ChargeQueryResponse chargeQueryResponse = queryService.getChargeGatewayStatus(chargeEntity);
                boolean success = cleanUpChargeWithGateway(chargeEntity, chargeQueryResponse);
                if (success) {
                    successes.getAndIncrement();
                } else {
                    failures.getAndIncrement();
                }
            } catch (WebApplicationException | GatewayException e) {
                logger.info("Error when querying charge status with gateway: " + e.getMessage(),
                        chargeEntity.getStructuredLoggingArgs());
                failures.getAndIncrement();
            }
        });

        logger.info("Epdq charges cleaned up successfully: {}; epdq charges cleaned up failed: {}", 
                successes.intValue(), failures.intValue());
        
        return ImmutableMap.of(
                CLEANUP_SUCCESS, successes.intValue(),
                CLEANUP_FAILED, failures.intValue()
        );
    }

    private boolean cleanUpChargeWithGateway(ChargeEntity chargeEntity, ChargeQueryResponse chargeQueryResponse) {
        if (!chargeQueryResponse.foundCharge()) {
            // The charge might not be found with the gateway when the authorisation failed due to an error with ePDQ
            // before they tried to process the payment. One example of this is when the card type is not enabled in ePDQ.
            logger.info("Charge was not found on the gateway. Gateway response was: " +
                            chargeQueryResponse.getRawGatewayResponseString(),
                    chargeEntity.getStructuredLoggingArgs());
            chargeService.transitionChargeState(chargeEntity, AUTHORISATION_ERROR_CHARGE_MISSING);
            return true;
        }

        return chargeQueryResponse.getMappedStatus().map(mappedStatus -> {
            // Attempt to cancel the charge with the gateway if it is not in a terminal state with them
            if (!mappedStatus.toExternal().isFinished()) {
                if (attemptCancelWithGateway(chargeEntity)) {
                    chargeService.transitionChargeState(chargeEntity, AUTHORISATION_ERROR_CANCELLED);
                    return true;
                }
                return false;
            }

            // These are terminal states with the gateway for which no cleanup is required
            if (mappedStatus == AUTHORISATION_REJECTED || mappedStatus == AUTHORISATION_ERROR) {
                chargeService.transitionChargeState(chargeEntity, AUTHORISATION_ERROR_REJECTED);
                return true;
            }
            
            if (mappedStatus == USER_CANCELLED) {
                // The charge has already been cancelled with the gateway, probably manually
                chargeService.transitionChargeState(chargeEntity, AUTHORISATION_ERROR_CANCELLED);
                return true;
            }

            logger.error(format("Charge is in a mapped status of [%s] with the gateway, which is " +
                            "unexpected and we do not handle. If the gateway status is CAPTURED, it " +
                            "suggests the service has incorrect gateway settings.",
                    mappedStatus.getValue()),
                    chargeEntity.getStructuredLoggingArgs());
            return false;
        }).orElseGet(() -> {
            logger.error("Charge does not map to an internal charge state. Raw query response was: " +
                            chargeQueryResponse.getRawGatewayResponseString(),
                    chargeEntity.getStructuredLoggingArgs());
            return false;
        });
    }

    private boolean attemptCancelWithGateway(ChargeEntity chargeEntity) {
        try {
            logger.info("Attempting gateway cleanup for charge.", chargeEntity.getStructuredLoggingArgs());
            GatewayResponse<BaseCancelResponse> cancelResponse = providers.byName(chargeEntity.getPaymentGatewayName()).cancel(CancelGatewayRequest.valueOf(chargeEntity));
            return cancelResponse.getBaseResponse().map(baseCancelResponse -> {
                if (baseCancelResponse.cancelStatus() == BaseCancelResponse.CancelStatus.ERROR) {
                    logger.info("Could not cancel charge, gateway returned an error.", chargeEntity.getStructuredLoggingArgs());
                    return false;
                }
                return true;
            }).orElseGet(() -> {
                cancelResponse.getGatewayError().ifPresent(
                        e -> logger.info(format("Could not cancel charge. Gateway error: %s", e.getMessage()),
                                chargeEntity.getStructuredLoggingArgs()));
                return false;
            });
        } catch (GatewayException e) {
            logger.info(format("Gateway error when attempting to cancel charge with the gateway: %s",
                    e.getMessage()),
                    chargeEntity.getStructuredLoggingArgs());
            return false;
        }
    }
}
