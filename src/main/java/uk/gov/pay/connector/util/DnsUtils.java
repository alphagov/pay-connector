package uk.gov.pay.connector.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.util.*;

import static java.lang.String.format;
import static java.lang.String.join;

public class DnsUtils {
    private static final Logger logger = LoggerFactory.getLogger(DnsUtils.class);
    public boolean ipMatchesDomain(String ipAddress, String domain) {
        try {
            Optional<String> host = reverseDnsLookup(ipAddress);
            if (!host.isPresent()) {
                throw new Exception("Host not found");
            }
            if (!host.get().endsWith(domain + ".")) {
                logger.error(
                        format("Reverse DNS lookup on ip %s - resolved domain: %s does not match %s", ipAddress, host.get(), domain));
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.error(format("Reverse DNS Lookup failed: didn't find any host for ip address %s", ipAddress));
            return false;
        }
    }

    public Optional<String> dnsLookup(String hostName) throws Exception {
        try {
            InetAddress inetAddress = InetAddress.getByName(hostName);
            return Optional.ofNullable(inetAddress.getHostAddress());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<String> reverseDnsLookup(String hostIp) throws Exception {
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
