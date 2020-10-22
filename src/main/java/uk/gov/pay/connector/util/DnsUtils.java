package uk.gov.pay.connector.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static java.lang.String.join;

public class DnsUtils {
    private static final Logger logger = LoggerFactory.getLogger(DnsUtils.class);

    private String extractForwardedIp(String forwardedAddress) {
        String extractedIp = forwardedAddress.split(",")[0];
        logger.debug("Extracted ip {} from X-Forwarded-For '{}'", extractedIp, forwardedAddress);
        return extractedIp;
    }
    public boolean ipMatchesDomain(String forwardedAddress, String domain) {
        try {
            String ipAddress = extractForwardedIp(forwardedAddress);
            Optional<String> host = reverseDnsLookup(ipAddress);
            if (host.isEmpty()) {
                throw new Exception(format("Host not found for ip address '%s'", ipAddress));
            }
            if (!host.get().endsWith(domain + ".")) {
                logger.error("Reverse DNS lookup on ip '{}' - resolved domain '{}' does not match '{}'", ipAddress, host.get(), domain);
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.error("Reverse DNS Lookup failed: {}", e.getLocalizedMessage());
            return false;
        }
    }

    public Optional<String> reverseDnsLookup(String hostIp) {
        List<String> components = Arrays.asList(hostIp.split("\\."));
        Collections.reverse(components);
        String reverseIp = join(".", components.toArray(new String[0])) + ".in-addr.arpa";
        try {
            Hashtable env = new Hashtable();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(reverseIp, new String[]{"PTR"});
            ctx.close();
            return Optional.ofNullable(attrs.get("ptr").get().toString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
