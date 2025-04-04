package uk.gov.pay.connector.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;

import static java.lang.String.format;
import static java.util.Arrays.asList;

public class IpDomainMatcher {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(IpDomainMatcher.class);
    
    private final ReverseDnsLookup reverseDnsLookup;

    @Inject
    public IpDomainMatcher(ReverseDnsLookup reverseDnsLookup) {
        this.reverseDnsLookup = reverseDnsLookup;
    }

    private String extractForwardedIp(String forwardedAddress) {
        List<String> ipAddresses = asList(forwardedAddress.replaceAll("\\s","").split(","));
        // We want the last address in the forwardedAddress parameter as that's the address we trust 
        Collections.reverse(ipAddresses);
        String extractedIp = ipAddresses.get(0);
        LOGGER.debug("Extracted ip {} from X-Forwarded-For '{}'", extractedIp, forwardedAddress);
        return extractedIp;
    }
    
    public boolean ipMatchesDomain(String forwardedAddress, String domain) {
        try {
            String ipAddress = extractForwardedIp(forwardedAddress);
            return reverseDnsLookup.lookup(new DnsPointerResourceRecord(ipAddress)).map(host -> {
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
