package uk.gov.pay.connector.gateway.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.Optional;

public class ChargeNotificationProcessor {

    private ChargeEventDao chargeEventDao;
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    ChargeNotificationProcessor(ChargeEventDao chargeEventDao) {
        this.chargeEventDao = chargeEventDao;
    }

    public void invoke(String transactionId, ChargeEntity chargeEntity, ChargeStatus newStatus, ZonedDateTime gatewayEventDate) {
        GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
        String oldStatus = chargeEntity.getStatus();

        try {
            chargeEntity.setStatus(newStatus);
        } catch (InvalidStateTransitionException e) {
            logger.error("{} ({}) notification '{}' could not be used to update charge: {}",
                    gatewayAccount.getGatewayName(), gatewayAccount.getId(), transactionId, e.getMessage());
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

        chargeEventDao.persistChargeEventOf(chargeEntity, Optional.ofNullable(gatewayEventDate));
    }
}
