package uk.gov.pay.connector.util;


import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.util.*;

public class DnsUtils {

    public boolean ipMatchesDomain(String ipAddress, String domain) {
        try {
            Optional<String> host = reverseDnsLookup(ipAddress);
            return host.isPresent() && host.get().endsWith(domain + ".");
        } catch (Exception e) {
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
        String reverseIp = String.join(".", components.toArray(new String[0])) + ".in-addr.arpa";
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
