package uk.gov.pay.connector.gateway.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.InvalidForceStateTransitionException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmedByGatewayNotification;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.time.ZonedDateTime;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER_PAYMENT_ID;

public class ChargeNotificationProcessor {

    private final ChargeService chargeService;
    private final EventService eventService;
    
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    ChargeNotificationProcessor(ChargeService chargeService, EventService eventService) {
        this.chargeService = chargeService;
        this.eventService = eventService;
    }

    public void invoke(String gatewayTransactionId, Charge charge, ChargeStatus newStatus, ZonedDateTime gatewayEventDate) {
        ChargeEntity chargeEntity = chargeService.findChargeByExternalId(charge.getExternalId());
        GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
        String oldStatus = chargeEntity.getStatus();
        
        if (newStatus.getValue().equals(oldStatus)) {
            return;
        }

        try {
            chargeService.transitionChargeState(chargeEntity, newStatus, gatewayEventDate);
        } catch (InvalidStateTransitionException e) {
            if (!forceTransitionChargeState(gatewayAccount, gatewayTransactionId, chargeEntity, oldStatus, newStatus, gatewayEventDate)) {
                return;
            }
        } catch (OptimisticLockException e) {
            logger.warn("Optimistic lock exception encountered whilst processing notification for charge_external_id={} " +
                            "with error message: {}",
                    chargeEntity.getExternalId(), e.getMessage());
            return;
        }

        logger.info("Notification received. Updated charge - " +
                        "charge_external_id={}, " +
                        "status={}, " +
                        "status_to={}, " +
                        "transaction_id={}, " +
                        "account_id={}, " +
                        "provider={}, " +
                        "provider_type={}",
                chargeEntity.getExternalId(),
                oldStatus,
                newStatus,
                gatewayTransactionId,
                gatewayAccount.getId(),
                chargeEntity.getPaymentProvider(),
                gatewayAccount.getType());

    }
    
    private boolean forceTransitionChargeState(GatewayAccountEntity gatewayAccount, String gatewayTransactionId, ChargeEntity chargeEntity, String oldStatus, ChargeStatus newStatus, ZonedDateTime gatewayEventDate) {
        try {
            chargeService.forceTransitionChargeState(chargeEntity, newStatus, gatewayEventDate);
            return true;
        } catch (InvalidForceStateTransitionException ie) {
            logger.error(format("%s (%s) notification '%s' could not force transition from %s to %s",
                    chargeEntity.getPaymentProvider(), gatewayAccount.getId(), gatewayTransactionId, oldStatus, newStatus),
                    kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()),
                    kv(PROVIDER_PAYMENT_ID, gatewayTransactionId),
                    kv(GATEWAY_ACCOUNT_ID, gatewayAccount.getId()),
                    kv(PROVIDER, chargeEntity.getPaymentProvider()));
            return false;
        }
    }
    
    public void processCaptureNotificationForExpungedCharge(GatewayAccountEntity gatewayAccount, 
                                                            String gatewayTransactionId, 
                                                            Charge charge, 
                                                            ChargeStatus newStatus) {
        logger.info(format("Received capture notification for charge that was already expunged from Connector. " +
                        "Transitioning state from [%s] to [%s].", charge.getStatus(), newStatus.getValue()),
                kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()),
                kv(PROVIDER_PAYMENT_ID, gatewayTransactionId),
                kv(GATEWAY_ACCOUNT_ID, gatewayAccount.getId()),
                kv(PROVIDER, charge.getPaymentGatewayName()));
        
        Event event = new CaptureConfirmedByGatewayNotification(charge.getServiceId(), charge.isLive(), charge.getGatewayAccountId(), charge.getExternalId(), Instant.now());
        eventService.emitEvent(event);
    }
}
