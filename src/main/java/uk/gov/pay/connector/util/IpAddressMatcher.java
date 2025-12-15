package uk.gov.pay.connector.util;

import org.apache.commons.validator.routines.InetAddressValidator;

import jakarta.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Arrays.asList;

@Singleton
public class IpAddressMatcher {
    private final InetAddressValidator ipAddressValidator;

    public IpAddressMatcher(InetAddressValidator ipAddressValidator) {
        this.ipAddressValidator = ipAddressValidator;
    }

    public boolean isMatch(String forwardedIpAddresses, Set<String> allowedIpAddresses) {
        if (Objects.nonNull(forwardedIpAddresses) && Objects.nonNull(allowedIpAddresses)) {
            String ipAddress = getLastIpAddress(forwardedIpAddresses);
            if (ipAddressValidator.isValid(ipAddress)) {
                return allowedIpAddresses.contains(ipAddress);
            }
        }
        return false;
    }

    private String getLastIpAddress(String forwardedAddresses) { 
        List<String> ipAddresses = asList(forwardedAddresses.replaceAll("\\s","").split(","));
        // We want the last address in the forwardedAddress parameter as that's the address we trust 
        Collections.reverse(ipAddresses);
        return ipAddresses.getFirst();
    }
}
