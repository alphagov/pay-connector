package uk.gov.pay.connector.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.ChargeStatusRequest;

import javax.inject.Inject;
import java.util.Optional;

import static java.lang.String.format;

public class NotificationUtil {
    private DnsUtils dnsUtils;

    public NotificationUtil() {
        this.dnsUtils = new DnsUtils();
    }

    public NotificationUtil(DnsUtils dnsUtils) {
        this.dnsUtils = dnsUtils;
    }

    private static final Logger logger = LoggerFactory.getLogger(NotificationUtil.class);

    public boolean notificationIpBelongsToDomain(String ipAddress, String domain) {
        try {
            Optional<String> host = dnsUtils.reverseDnsLookup(ipAddress);
            return host.isPresent() && host.get().contains(domain);
        } catch (Exception e) {
            return false;
        }
    }

    boolean payloadChecks(ChargeStatusRequest chargeStatusRequest) {
        if (StringUtils.isEmpty(chargeStatusRequest.getTransactionId())) {
            logger.error(format("Invalid transaction ID %s", chargeStatusRequest.getTransactionId()));
            return false;
        }
        return true;
    }
}
