package uk.gov.pay.connector.expunge.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.tasks.ParityCheckService;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.util.stream.IntStream;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.SKIPPED;
import static uk.gov.pay.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class ChargeExpungeService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ChargeDao chargeDao;
    private final ExpungeConfig expungeConfig;
    private final ParityCheckService parityCheckService;
    private final ChargeService chargeService;

    @Inject
    public ChargeExpungeService(ChargeDao chargeDao, ConnectorConfiguration connectorConfiguration,
                                ParityCheckService parityCheckService,
                                ChargeService chargeService) {
        this.chargeDao = chargeDao;
        expungeConfig = connectorConfiguration.getExpungeConfig();
        this.parityCheckService = parityCheckService;
        this.chargeService = chargeService;
    }

    private static boolean inTerminalState(ChargeEntity chargeEntity) {
        return ChargeStatus.fromString(chargeEntity.getStatus()).isExpungeable();
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
                        .ifPresent(chargeEntity -> {
                            MDC.put(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId());
                            try {
                                parityCheckAndExpungeIfMet(chargeEntity);
                            } catch(OptimisticLockException error) {
                                logger.info("Expunging process conflicted with an already running process, exit");
                                throw error;
                            }
                            MDC.remove(PAYMENT_EXTERNAL_ID);
                        });
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
            chargeService.updateChargeParityStatus(chargeEntity.getExternalId(), SKIPPED);
            logger.info("Charge not expunged because it is not in a terminal state");
        } else if (parityCheckService.parityCheckChargeForExpunger(chargeEntity)) {
            expungeCharge(chargeEntity);
            logger.info("Charge expunged from connector");
        } else {
            if (hasChargeBeenParityCheckedBefore) {
                logger.error("Charge cannot be expunged because parity check with ledger repeatedly failed");
            } else {
                logger.info("Charge cannot be expunged because parity check with ledger failed");
            }
        }
    }

    @Transactional
    public void expungeCharge(ChargeEntity chargeEntity) {
        chargeDao.expungeCharge(chargeEntity.getId(), chargeEntity.getExternalId());
    }

}
