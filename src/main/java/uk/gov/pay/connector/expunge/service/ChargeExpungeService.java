package uk.gov.pay.connector.expunge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.tasks.ParityCheckService;

import javax.inject.Inject;
import java.util.stream.IntStream;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class ChargeExpungeService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ChargeDao chargeDao;
    private final ExpungeConfig expungeConfig;
    private final ParityCheckService parityCheckService;
    
    @Inject
    public ChargeExpungeService(ChargeDao chargeDao, ConnectorConfiguration connectorConfiguration,
                                ParityCheckService parityCheckService) {
        this.chargeDao = chargeDao;
        expungeConfig = connectorConfiguration.getExpungeConfig();
        this.parityCheckService = parityCheckService;
    }

    public void expunge(Integer noOfChargesToExpungeQueryParam) {
        if (!expungeConfig.isExpungeChargesEnabled()) {
            logger.info("Charge expunging feature is disabled. No charges have been expunged");
        } else {
            int noOfChargesToExpunge = getNumberOfChargesToExpunge(noOfChargesToExpungeQueryParam);
            int minimumAgeOfChargeInDays = expungeConfig.getMinimumAgeOfChargeInDays();
            int createdWithinLast = expungeConfig.getExcludeChargesParityCheckedWithInDays();

            IntStream.range(0, noOfChargesToExpunge).forEach(number -> {
                chargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, createdWithinLast)
                        .ifPresent(this::parityCheckAndExpungeIfMet);
            });
        }
    }

    private int getNumberOfChargesToExpunge(Integer noOfChargesToExpungeQueryParam) {
        if (noOfChargesToExpungeQueryParam != null && noOfChargesToExpungeQueryParam > 0) {
            return noOfChargesToExpungeQueryParam;
        }
        return expungeConfig.getNumberOfChargesToExpunge();
    }

    private void parityCheckAndExpungeIfMet(ChargeEntity chargeEntity) {
        boolean hasChargeBeenParityCheckedBefore = chargeEntity.getParityCheckDate() != null;
        
        if (!inTerminalState(chargeEntity)) {
            logger.info("Charge not expunged because it is not in a terminal state {}",
                    kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
        } else if (parityCheckService.parityCheckChargeForExpunger(chargeEntity)) {
            chargeDao.expungeCharge(chargeEntity.getId());
            logger.info("Charge expunged from connector {}",
                    kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
        } else {
            if (hasChargeBeenParityCheckedBefore) {
                logger.error("Charge cannot be expunged because parity check with ledger repeatedly failed {}",
                        kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
            } else {
                logger.info("Charge cannot be expunged because parity check with ledger failed {}",
                        kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
            }
        }
    }

    private static boolean inTerminalState(ChargeEntity chargeEntity) {
        return ChargeStatus.fromString(chargeEntity.getStatus()).isExpungeable();
    }
    
}
