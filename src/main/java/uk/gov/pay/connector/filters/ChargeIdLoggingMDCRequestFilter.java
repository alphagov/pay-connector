package uk.gov.pay.connector.filters;

import org.slf4j.MDC;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class ChargeIdLoggingMDCRequestFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String chargeId = requestContext.getUriInfo().getPathParameters().getFirst("chargeId");
        
        if (chargeId != null)
            MDC.put("chargeId", chargeId);
    }
}
