package uk.gov.pay.connector.paymentprocessor.resource;

import com.google.inject.Inject;
import org.hibernate.validator.constraints.NotEmpty;
import uk.gov.pay.connector.paymentprocessor.service.DiscrepancyService;
import uk.gov.pay.connector.report.model.GatewayStatusComparison;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
public class DiscrepancyResource {

    private final DiscrepancyService discrepancyService;

    @Inject
    public DiscrepancyResource(DiscrepancyService discrepancyService) {
        this.discrepancyService = discrepancyService;
    }
    
    @POST
    @Path("/v1/api/discrepancies/report")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public List<GatewayStatusComparison> listDiscrepancies(@NotEmpty List<String> chargeIds) {
        return discrepancyService.listGatewayStatusComparisons(chargeIds);
    }

    @POST
    @Path("/v1/api/discrepancies/resolve")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public List<GatewayStatusComparison> resolveDiscrepancies(@NotEmpty List<String> chargeIds) {
        return discrepancyService.resolveDiscrepancies(chargeIds);
    }
}
