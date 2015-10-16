package uk.gov.pay.connector.util;

import java.util.UUID;

public class TransactionId {
    public static String randomId() {
        return UUID.randomUUID().toString();
    }
}
