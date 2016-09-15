package uk.gov.pay.connector.util;

import org.mindrot.jbcrypt.BCrypt;

public class HashUtil {
    public String hash(String value) {
        return BCrypt.hashpw(value, BCrypt.gensalt());
    }
}
