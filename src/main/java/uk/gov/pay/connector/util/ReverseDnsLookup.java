package uk.gov.pay.connector.util;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;

public class ReverseDnsLookup {

    private static final Hashtable<String, String> INITIAL_DIR_CONTEXT_ENV = new Hashtable<>(Map.of(
            "java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory"));

    public Optional<String> lookup(DnsPointerResourceRecord pointerRecord) {
        try {
            DirContext ctx = new InitialDirContext(INITIAL_DIR_CONTEXT_ENV);
            Attributes attrs = ctx.getAttributes(pointerRecord.getReverseIp(), new String[]{"PTR"});
            ctx.close();
            return Optional.ofNullable(attrs.get("ptr").get().toString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
