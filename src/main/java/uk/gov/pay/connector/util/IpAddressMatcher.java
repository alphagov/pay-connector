package uk.gov.pay.connector.util;

import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;

@Singleton
public class IpAddressMatcher {
    public boolean isMatch(String forwardedIpAddresses, List<String> allowedIpAddresses) {
        if (Objects.nonNull(forwardedIpAddresses) && Objects.nonNull(allowedIpAddresses)) {
            return allowedIpAddresses.contains(getFirstIpAddress(forwardedIpAddresses));
        }
        return false;
    }

    private String getFirstIpAddress(String forwardedIpAddresses) {
       return forwardedIpAddresses.split(",")[0];
    }
}
