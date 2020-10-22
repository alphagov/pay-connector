package uk.gov.pay.connector.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static java.lang.String.format;

public class IpDomainMatcher {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(IpDomainMatcher.class);
    
    private final ReverseDnsLookup reverseDnsLookup;

    @Inject
    public IpDomainMatcher(ReverseDnsLookup reverseDnsLookup) {
        this.reverseDnsLookup = reverseDnsLookup;
    }

    private String extractForwardedIp(String forwardedAddress) {
        String extractedIp = forwardedAddress.split(",")[0];
        LOGGER.debug("Extracted ip {} from X-Forwarded-For '{}'", extractedIp, forwardedAddress);
        return extractedIp;
    }
    
    public boolean ipMatchesDomain(String forwardedAddress, String domain) {
        try {
            String ipAddress = extractForwardedIp(forwardedAddress);
            return reverseDnsLookup.lookup(ipAddress).map(host -> {
                if (!host.endsWith(domain + ".")) {
                    LOGGER.error("Reverse DNS lookup on ip '{}' - resolved domain '{}' does not match '{}'", ipAddress, host, domain);
                    return false;
                }
                return true;
            }).orElseThrow(() -> new Exception(format("Host not found for ip address '%s'", ipAddress)));
        } catch (Exception e) {
            LOGGER.error("Reverse DNS Lookup failed: {}", e.getLocalizedMessage());
            return false;
        }
    }
}
