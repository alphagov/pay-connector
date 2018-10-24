package uk.gov.pay.connector.refund.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.refund.service.SearchRefundsService;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.time.ZonedDateTime;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class SearchRefundsResource {

    private final Logger logger = LoggerFactory.getLogger(SearchRefundsResource.class);
    private final String PAGE = "page";
    private final String FROM_DATE = "from_date";
    private final String TO_DATE = "to_date";
    private final String DISPLAY_SIZE = "display_size";
    private final String ACCOUNT_ID = "accountId";
    private final SearchRefundsService searchRefundsService;
    private final GatewayAccountDao gatewayAccountDao;
    private final ConnectorConfiguration configuration;
    @Inject
    public SearchRefundsResource(ConnectorConfiguration configuration, SearchRefundsService searchRefundsService, GatewayAccountDao gatewayAccountDao) {
        this.searchRefundsService = searchRefundsService;
        this.gatewayAccountDao = gatewayAccountDao;
        this.configuration = configuration;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/refunds")
    @Produces(APPLICATION_JSON)
    public Response getRefundsByAccountId(@PathParam(ACCOUNT_ID) Long accountId,
                                          @QueryParam(FROM_DATE) String fromDate,
                                          @QueryParam(TO_DATE) String toDate,
                                          @QueryParam(PAGE) Long pageNumber,
                                          @QueryParam(DISPLAY_SIZE) Long displaySize,
                                          @Context UriInfo uriInfo) {
        logger.info("Getting all refunds for account id {}", accountId);
        SearchParams refundSearchParams = new SearchParams()
                .withGatewayAccountId(accountId)
                .withFromDate(parseDate(fromDate))
                .withToDate(parseDate(toDate))
                .withDisplaySize(displaySize != null ? displaySize : configuration.getTransactionsPaginationConfig().getDisplayPageSize())
                .withPage(pageNumber != null ? pageNumber : 1);

        
        return gatewayAccountDao.findById(accountId)
                .map(gatewayAccount -> searchRefundsService.getAllRefunds(uriInfo, refundSearchParams))
                .orElseGet(() -> notFoundResponse(format("Gateway account with id %s does not exist", accountId)));


    }

    //todo do not do this
    private ZonedDateTime parseDate(String date) {
        ZonedDateTime parse = null;
        if (isNotBlank(date)) {
            parse = ZonedDateTime.parse(date);
        }
        return parse;
    }
}
