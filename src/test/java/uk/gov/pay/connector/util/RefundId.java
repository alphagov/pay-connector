package uk.gov.pay.connector.util;

import org.apache.commons.lang.math.RandomUtils;

public class RefundId {
    public final long refundId;

    private RefundId(long refundId) {
        this.refundId = refundId;
    }
    
    public static RefundId generate() {
        return new RefundId(RandomUtils.nextInt());
    }
}
