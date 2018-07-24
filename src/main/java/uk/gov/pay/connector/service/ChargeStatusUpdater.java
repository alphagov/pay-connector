package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.function.Consumer;

@Transactional
public class ChargeStatusUpdater {
    private static final Logger logger = LoggerFactory.getLogger(ChargeStatusUpdater.class);


    @Inject
    public ChargeStatusUpdater() {
    }

    public void updateChargeTransactionStatus(String externalId, ChargeStatus newChargeStatus, ZonedDateTime gatewayEventTime) { }

    public void updateChargeTransactionStatus(String externalId, ChargeStatus newChargeStatus) {
    }

    private void updateChargeTransactionStatus(String externalId, ChargeStatus newChargeStatus, Consumer<ChargeTransactionEntity> updateStatusFunction) {
    }
}
