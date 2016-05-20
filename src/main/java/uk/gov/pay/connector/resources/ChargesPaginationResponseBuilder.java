package uk.gov.pay.connector.resources;

import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.model.ChargeResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGES_API_PATH;

public class ChargesPaginationResponseBuilder {

    private ChargeSearchParams searchParams;
    private UriInfo uriInfo;
    private List<ChargeResponse> chargeResponses;

    private Long totalCount;
    private Long selfPageNum;
    private URI selfLink;
    private URI firstLink;
    private URI lastLink;
    private URI prevLink;
    private URI nextLink;

    public ChargesPaginationResponseBuilder(ChargeSearchParams searchParams, UriInfo uriInfo) {
        this.searchParams = searchParams;
        this.uriInfo = uriInfo;
        selfPageNum = searchParams.getPage();
        selfLink = uriWithParams(searchParams.buildQueryParams());
    }

    public ChargesPaginationResponseBuilder withChargeResponses(List<ChargeResponse> chargeResponses) {
        this.chargeResponses = chargeResponses;
        return this;
    }

    public ChargesPaginationResponseBuilder withTotalCount(Long total) {
        this.totalCount = total;
        return this;
    }

    public Response buildResponse() {
        double lastPage = Math.ceil(new Double(totalCount) / searchParams.getDisplaySize());
        buildLinks(lastPage);

        String halString = new HalResourceBuilder()
                .withProperty("results", chargeResponses)
                .withProperty("count", chargeResponses.size())
                .withProperty("total", totalCount)
                .withProperty("page", selfPageNum)
                .withSelfLink(selfLink)
                .withLink("first_page", firstLink)
                .withLink("last_page", lastLink)
                .withLink("prev_page", prevLink)
                .withLink("next_page", nextLink)
                .build();

        return ok(halString).build();
    }

    private void buildLinks(double lastPage) {
        searchParams.withPage(1L);
        firstLink = uriWithParams(searchParams.buildQueryParams());

        searchParams.withPage((long) lastPage);
        lastLink = uriWithParams(searchParams.buildQueryParams());

        searchParams.withPage(selfPageNum - 1);
        prevLink = selfPageNum == 1L ? null : uriWithParams(searchParams.buildQueryParams());

        searchParams.withPage(selfPageNum + 1);
        nextLink = selfPageNum == lastPage ? null : uriWithParams(searchParams.buildQueryParams());
    }

    private URI uriWithParams(String params) {
        return uriInfo.getBaseUriBuilder()
                .path(CHARGES_API_PATH)
                .replaceQuery(params)
                .build(searchParams.getGatewayAccountId());
    }

}
