package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.service.SearchRefundsService;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
public class SearchRefundsResource {

    private final Logger logger = LoggerFactory.getLogger(SearchRefundsResource.class);
    private final String PAGE = "page";
    private final String DISPLAY_SIZE = "display_size";
    private final String ACCOUNT_ID = "accountId";
    private final SearchRefundsService searchRefundsService;

    @Inject
    public SearchRefundsResource(SearchRefundsService searchRefundsService)  {
        this.searchRefundsService = searchRefundsService;
    }
    
    @GET
    @Path("/v1/api/accounts/{accountId}/refunds")
    @Produces(APPLICATION_JSON)
    public Response getRefundsByAccountId(@PathParam(ACCOUNT_ID) Long accountId,
                                          @QueryParam(PAGE) Long pageNumber,
                                          @QueryParam(DISPLAY_SIZE) Long displaySize,
                                          @Context UriInfo uriInfo) {
        logger.info("Getting all refunds for account id {}", accountId);
        return searchRefundsService.getAllRefunds(uriInfo, accountId, pageNumber, displaySize);
    }
}
