package uk.gov.pay.connector.gateway.sandbox;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import uk.gov.pay.connector.util.IpAddressMatcher;

import java.util.Set;

public class SandboxNotificationService {
    private final IpAddressMatcher ipAddressMatcher;
    private final Set<String> allowedSandboxIpAddresses;

    @Inject
    public SandboxNotificationService(IpAddressMatcher ipAddressMatcher,
                                      @Named("AllowedSandboxIpAddresses") Set<String> allowedSandboxIpAddresses) {
        this.ipAddressMatcher = ipAddressMatcher;
        this.allowedSandboxIpAddresses = allowedSandboxIpAddresses;
    }

    public boolean handleNotificationFor(String forwardedIpAddresses) {
        if (!ipAddressMatcher.isMatch(forwardedIpAddresses, allowedSandboxIpAddresses)) {
            return false;
        }
        return true;
    }
}
