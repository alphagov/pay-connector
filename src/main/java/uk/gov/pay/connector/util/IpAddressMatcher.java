package uk.gov.pay.connector.util;

import org.apache.commons.validator.routines.InetAddressValidator;

import javax.inject.Singleton;
import java.util.Objects;
import java.util.Set;

@Singleton
public class IpAddressMatcher {
    private final InetAddressValidator ipAddressValidator;

    public IpAddressMatcher(InetAddressValidator ipAddressValidator) {
        this.ipAddressValidator = ipAddressValidator;
    }

    public boolean isMatch(String forwardedIpAddresses, Set<String> allowedIpAddresses) {
        if (Objects.nonNull(forwardedIpAddresses) && Objects.nonNull(allowedIpAddresses)) {
            String ipAddress = getFirstIpAddress(forwardedIpAddresses);
            if (ipAddressValidator.isValid(ipAddress)) {
                return allowedIpAddresses.contains(ipAddress);
            }
        }
        return false;
    }

    private String getFirstIpAddress(String forwardedIpAddresses) {
       return forwardedIpAddresses.split(",")[0];
    }
}
