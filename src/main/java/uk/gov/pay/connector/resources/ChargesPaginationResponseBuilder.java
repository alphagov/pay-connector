package uk.gov.pay.connector.resources;

import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.model.ChargeResponse;

import javax.ws.rs.core.Response;
import java.util.List;

import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGES_API_PATH;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class ChargesPaginationResponseBuilder {

    private ChargeSearchParams searchParams;
    private List<ChargeResponse> chargeResponses;
    private Long totalCount;
    private Long selfPageNum;
    private String transactionsPath;
    private String selfLink;
    private String firstLink;
    private String lastLink;
    private String prevLink;
    private String nextLink;

    public ChargesPaginationResponseBuilder(ChargeSearchParams searchParams) {
        this.searchParams = searchParams;
        selfPageNum = searchParams.getPage();
        transactionsPath = CHARGES_API_PATH.replace("{accountId}", searchParams.getGatewayAccountId().toString());
        selfLink = transactionsPath + "?" + searchParams.buildQueryParams();
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
        if (totalCount > 0) {
            double lastPage = Math.ceil(new Double(totalCount) / searchParams.getDisplaySize());
            if (invalidPageRequest(lastPage)) {
                return notFoundResponse("the requested page is not found");
            }
            buildLinks(lastPage);
        }

        String halString = new HalResourceBuilder()
                .withProperty("results", chargeResponses)
                .withProperty("count", chargeResponses.size())
                .withProperty("total", totalCount)
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
        firstLink = transactionsPath + "?" + searchParams.buildQueryParams();

        searchParams.withPage((long) lastPage);
        lastLink = transactionsPath + "?" + searchParams.buildQueryParams();

        searchParams.withPage(selfPageNum - 1);
        prevLink = selfPageNum == 1L ? null : transactionsPath + "?" + searchParams.buildQueryParams();

        searchParams.withPage(selfPageNum + 1);
        nextLink = selfPageNum == lastPage ? null : transactionsPath + "?" + searchParams.buildQueryParams();
    }

    private boolean invalidPageRequest(double lastPage) {
        return searchParams.getPage() > lastPage || searchParams.getPage() < 1;
    }
}
