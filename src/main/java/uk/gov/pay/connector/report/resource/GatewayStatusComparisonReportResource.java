package uk.gov.pay.connector.report.resource;

import com.google.inject.Inject;
import org.hibernate.validator.constraints.NotEmpty;
import uk.gov.pay.connector.report.model.GatewayStatusComparison;
import uk.gov.pay.connector.report.service.DiscrepancyService;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
public class GatewayStatusComparisonReportResource {

    private final DiscrepancyService discrepancyService;
    
    @Inject
    public GatewayStatusComparisonReportResource(DiscrepancyService discrepancyService) {
        this.discrepancyService = discrepancyService;
    }

    @POST
    @Path("/v1/api/reports/discrepancies")
    @Produces(APPLICATION_JSON)
    public List<GatewayStatusComparison> listDiscrepancies(@NotEmpty List<String> chargeIds) {
        return discrepancyService.listGatewayStatusComparisons(chargeIds);
    }
}
