package uk.gov.pay.connector.util;


import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.*;

public class DnsUtils {

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
        }
        catch(Exception e) {}
        return Optional.empty();
    }
}
