package uk.gov.pay.connector.util;

import com.amazonaws.xray.AWSXRay;

public class XrayUtils {

    private final boolean xrayEnabled;
    private final String segmentName;

    public XrayUtils(Boolean isXrayEnabled) {
        this.xrayEnabled = isXrayEnabled;
        this.segmentName = "pay-connector";
    }

    public void beginSegment() {
        if (xrayEnabled) 
            AWSXRay.beginSegment(segmentName);
    }

    public void endSegment() {
        if (xrayEnabled)
            AWSXRay.endSegment();
    }
}
