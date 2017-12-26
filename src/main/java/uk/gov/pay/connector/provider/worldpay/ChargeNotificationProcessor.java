package uk.gov.pay.connector.provider.worldpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.ChargeStatusUpdater;
import uk.gov.pay.connector.service.PaymentGatewayName;
import uk.gov.pay.connector.service.worldpay.WorldpayNotification;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.Optional;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;

class ChargeNotificationProcessor {

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

    public void invoke(ChargeEntity chargeEntity, String transactionId, ZonedDateTime gatewayEventDate) {
        GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
        String oldStatus = chargeEntity.getStatus();
        ChargeStatus newStatus = CAPTURED;

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
