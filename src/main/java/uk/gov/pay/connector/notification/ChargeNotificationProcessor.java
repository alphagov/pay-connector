package uk.gov.pay.connector.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.ChargeStatusUpdater;
import uk.gov.pay.connector.service.PaymentGatewayName;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.Optional;

public class ChargeNotificationProcessor {

    private ChargeEventDao chargeEventDao;
    private ChargeStatusUpdater chargeStatusUpdater;
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    ChargeNotificationProcessor(ChargeEventDao chargeEventDao, ChargeStatusUpdater chargeStatusUpdater) {
        this.chargeEventDao = chargeEventDao;
        this.chargeStatusUpdater = chargeStatusUpdater;
    }

    private PaymentGatewayName gatewayName() {
        return PaymentGatewayName.WORLDPAY;
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
        chargeStatusUpdater.updateChargeTransactionStatus(chargeEntity.getExternalId(), newStatus, gatewayEventDate);
    }

}
