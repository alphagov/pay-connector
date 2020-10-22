package uk.gov.pay.connector.util;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.join;

public class ReverseDnsLookup {

    private static final Hashtable<String, String> INITIAL_DIR_CONTEXT_ENV = new Hashtable<>(Map.of(
            "java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory"));

    public Optional<String> lookup(String hostIp) {
        List<String> components = Arrays.asList(hostIp.split("\\."));
        Collections.reverse(components);
        String reverseIp = join(".", components) + ".in-addr.arpa";
        try {
            DirContext ctx = new InitialDirContext(INITIAL_DIR_CONTEXT_ENV);
            Attributes attrs = ctx.getAttributes(reverseIp, new String[]{"PTR"});
            ctx.close();
            return Optional.ofNullable(attrs.get("ptr").get().toString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
