package uk.gov.pay.connector.gateway.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.InvalidForceStateTransitionException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.inject.Inject;
import java.time.ZonedDateTime;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.pay.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.pay.logging.LoggingKeys.PROVIDER;
import static uk.gov.pay.logging.LoggingKeys.PROVIDER_PAYMENT_ID;

public class ChargeNotificationProcessor {

    private ChargeService chargeService;
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    ChargeNotificationProcessor(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    public void invoke(String gatewayTransactionId, Charge charge, ChargeStatus newStatus, ZonedDateTime gatewayEventDate) {
        ChargeEntity chargeEntity = chargeService.findChargeByExternalId(charge.getExternalId());
        GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
        String oldStatus = chargeEntity.getStatus();
        
        if (newStatus.equals(oldStatus)) {
            return;
        }

        try {
            chargeService.transitionChargeState(chargeEntity, newStatus, gatewayEventDate);
        } catch (InvalidStateTransitionException e) {
            logger.error(String.format("%s (%s) notification '%s' could not be used to update charge: %s",
                    gatewayAccount.getGatewayName(), gatewayAccount.getId(), gatewayTransactionId, e.getMessage()),
                    kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()),
                    kv(PROVIDER_PAYMENT_ID, gatewayTransactionId),
                    kv(GATEWAY_ACCOUNT_ID, gatewayAccount.getId()),
                    kv(PROVIDER, gatewayAccount.getGatewayName()));
            
            boolean success = forceTransitionChargeState(gatewayAccount, gatewayTransactionId, chargeEntity, oldStatus, newStatus);
            
            if (!success) {
                return;
            }
        }

        logger.info("Notification received. Updating charge - " +
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
                gatewayAccount.getGatewayName(),
                gatewayAccount.getType());
    }
    
    private boolean forceTransitionChargeState(GatewayAccountEntity gatewayAccount, String gatewayTransactionId, ChargeEntity chargeEntity, String oldStatus, ChargeStatus newStatus) {
        try {
            chargeService.forceTransitionChargeState(chargeEntity, newStatus);
            return true;
        } catch (InvalidForceStateTransitionException ie) {
            logger.error(String.format("%s (%s) notification '%s' could not force transition from %s to %s",
                    gatewayAccount.getGatewayName(), gatewayAccount.getId(), gatewayTransactionId, oldStatus, newStatus),
                    kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()),
                    kv(PROVIDER_PAYMENT_ID, gatewayTransactionId),
                    kv(GATEWAY_ACCOUNT_ID, gatewayAccount.getId()),
                    kv(PROVIDER, gatewayAccount.getGatewayName()));
            return false;
        }
    }
}
