package uk.gov.pay.connector.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.ChargeStatusRequest;
import uk.gov.pay.connector.service.ChargeStatusBlacklist;

import static java.lang.String.format;

public class NotificationUtil {

    private static final Logger logger = LoggerFactory.getLogger(NotificationUtil.class);

    ChargeStatusBlacklist chargeStatusBlacklist;

    public NotificationUtil(ChargeStatusBlacklist chargeStatusBlacklist) {
        this.chargeStatusBlacklist = chargeStatusBlacklist;
    }

    public boolean payloadChecks(ChargeStatusRequest chargeStatusRequest) {
        if (chargeStatusRequest.getChargeStatus().isPresent() &&
                chargeStatusBlacklist.has(chargeStatusRequest.getChargeStatus().get())) {
            logger.info(format("Ignored black listed notification of type %s", chargeStatusRequest.getChargeStatus()));
            return false;
        }

        if (StringUtils.isEmpty(chargeStatusRequest.getTransactionId())) {
            logger.error(format("Invalid transaction ID %s", chargeStatusRequest.getTransactionId()));
            return false;
        }

        return true;
    }
}
