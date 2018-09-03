package uk.gov.pay.connector.util;

import com.amazonaws.xray.AWSXRay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XrayUtils {

    private final Logger LOGGER = LoggerFactory.getLogger(XrayUtils.class);

    private final boolean xrayEnabled;
    private final String segmentName;

    public XrayUtils(Boolean isXrayEnabled) {
        this.xrayEnabled = isXrayEnabled;
        this.segmentName = "pay-connector";
    }

    public void beginSegment() {
        if (xrayEnabled) {
            try {
                AWSXRay.beginSegment(segmentName);
            } catch (Exception e) {
                LOGGER.error("An error occurred beginning an x-ray segment.", e);
            }
        }
    }

    public void endSegment() {
        if (xrayEnabled) {
            try {
                AWSXRay.endSegment();
            } catch (Exception e) {
                LOGGER.error("An error occurred ending an x-ray segment.", e);
            }
        }
    }
}
