package uk.gov.pay.connector.gateway.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.inject.Inject;
import java.time.ZonedDateTime;

public class ChargeNotificationProcessor {

    private ChargeService chargeService;
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    ChargeNotificationProcessor(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    public void invoke(String transactionId, ChargeEntity chargeEntity, ChargeStatus newStatus, ZonedDateTime gatewayEventDate) {
        GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
        String oldStatus = chargeEntity.getStatus();

        try {
            chargeService.transitionChargeState(chargeEntity, newStatus, gatewayEventDate);
        } catch (InvalidStateTransitionException e) {
            // don't log an error if we're trying to transition to the same state as this can happen if we've already
            // processed the notification
            if (!e.getCurrentState().equals(e.getTargetState())) {
                logger.error("{} ({}) notification '{}' could not be used to update charge: {}",
                        gatewayAccount.getGatewayName(), gatewayAccount.getId(), transactionId, e.getMessage());
            }
            return;
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
                transactionId,
                gatewayAccount.getId(),
                gatewayAccount.getGatewayName(),
                gatewayAccount.getType());
    }
}
