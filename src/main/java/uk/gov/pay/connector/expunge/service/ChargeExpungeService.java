package uk.gov.pay.connector.expunge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

import javax.inject.Inject;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class ChargeExpungeService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ChargeDao chargeDao;
    private final ExpungeConfig expungeConfig;

    @Inject
    public ChargeExpungeService(ChargeDao chargeDao, ConnectorConfiguration connectorConfiguration) {
        this.chargeDao = chargeDao;
        expungeConfig = connectorConfiguration.getExpungeConfig();
    }

    public void expunge(Integer noOfChargesToExpungeQueryParam) {
        if (expungeConfig.isExpungeChargesEnabled()) {
            int noOfChargesToExpunge = getNumberOfChargesToExpunge(noOfChargesToExpungeQueryParam);

            int noOfChargesProcessed = 0;

            while (noOfChargesProcessed < noOfChargesToExpunge) {
                Optional<ChargeEntity> mayBeChargeEntity =
                        chargeDao.findChargeToExpunge(expungeConfig.getMinimumAgeOfChargeInDays(),
                                expungeConfig.getExcludeChargesParityCheckedWithInDays()
                        );

                mayBeChargeEntity.ifPresent(chargeEntity -> {
                    // TODO: in PP-6098 
                    // Parity check charges with Ledger and 1. delete charge if matches or 2. update charge with parity_check_date

                    logger.info("Charge expunged from connector", kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
                });

                if (mayBeChargeEntity.isEmpty())
                    break;
                noOfChargesProcessed++;
            }
        } else {
            logger.info("Charge expunging feature is disabled. No charges have been expunged");
        }
    }

    private int getNumberOfChargesToExpunge(Integer noOfChargesToExpungeQueryParam) {
        if (noOfChargesToExpungeQueryParam != null && noOfChargesToExpungeQueryParam > 0) {
            return noOfChargesToExpungeQueryParam;
        }
        return expungeConfig.getNumberOfChargesToExpunge();
    }
}
