package uk.gov.pay.connector.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.ChargeStatusRequest;

import javax.inject.Inject;
import java.util.Optional;

import static java.lang.String.format;

public class NotificationUtil {

    private static final Logger logger = LoggerFactory.getLogger(NotificationUtil.class);
    
    boolean payloadChecks(ChargeStatusRequest chargeStatusRequest) {
        if (StringUtils.isEmpty(chargeStatusRequest.getTransactionId())) {
            logger.error(format("Invalid transaction ID %s", chargeStatusRequest.getTransactionId()));
            return false;
        }
        return true;
    }
}
