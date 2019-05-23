package uk.gov.pay.connector.app;

import uk.gov.pay.connector.filters.ChargeIdLoggingMDCRequestFilter;
import uk.gov.pay.connector.filters.ChargeIdLoggingMDCResponseFilter;

import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

@Provider
public class ChargeIdMDCLoggingFeature implements DynamicFeature {
    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {
        if (resourceInfo.getResourceClass().getAnnotation(LogChargeIDToMDC.class) != null) {
            context.register(ChargeIdLoggingMDCRequestFilter.class);
            context.register(ChargeIdLoggingMDCResponseFilter.class);
        }
    }
}
