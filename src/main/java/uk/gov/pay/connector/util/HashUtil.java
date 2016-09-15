package uk.gov.pay.connector.util;

import org.mindrot.jbcrypt.BCrypt;

public class HashUtil {
    public String hash(String value) {
        return BCrypt.hashpw(value, BCrypt.gensalt());
    }

    public boolean check(String value, String hashed) {
        return BCrypt.checkpw(value, hashed);
    }
}
