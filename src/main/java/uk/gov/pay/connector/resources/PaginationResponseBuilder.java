package uk.gov.pay.connector.resources;

import black.door.hate.HalRepresentation;
import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.charge.model.PageNumber;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

import static javax.ws.rs.core.Response.ok;

public class PaginationResponseBuilder<T> {

    private SearchParams searchParams;
    private UriInfo uriInfo;
    private List<T> responses;
    private Long totalCount;
    private Long selfPageNum;
    private URI selfLink;
    private URI firstLink;
    private URI lastLink;
    private URI prevLink;
    private URI nextLink;

    public PaginationResponseBuilder(SearchParams searchParams, UriInfo uriInfo) {
        this.searchParams = searchParams;
        this.uriInfo = uriInfo;
        selfPageNum = searchParams.getPage().getRawValue();
        selfLink = uriWithParams(searchParams.buildQueryParams());
    }

    public PaginationResponseBuilder withResponses(List<T> responses) {
        this.responses = responses;
        return this;
    }

    public PaginationResponseBuilder withTotalCount(Long total) {
        this.totalCount = total;
        return this;
    }

    public Response buildResponse() {
        Long size = searchParams.getDisplaySize().getRawValue();
        long lastPage = totalCount > 0 ? (totalCount + size - 1) / size : 1;
        buildLinks(lastPage);

        HalRepresentation.HalRepresentationBuilder halRepresentationBuilder = HalRepresentation.builder()
                .addProperty("results", responses)
                .addProperty("count", responses.size())
                .addProperty("total", totalCount)
                .addProperty("page", selfPageNum)
                .addLink("self", selfLink)
                .addLink("first_page", firstLink)
                .addLink("last_page", lastLink);

        addLinkNotNull(halRepresentationBuilder, "prev_page", prevLink);
        addLinkNotNull(halRepresentationBuilder, "next_page", nextLink);

        return ok(halRepresentationBuilder.build().toString()).build();
    }

    private void addLinkNotNull(HalRepresentation.HalRepresentationBuilder halRepresentationBuilder, String name, URI uri) {
        if (uri != null) {
            halRepresentationBuilder.addLink(name, uri);
        }
    }

    private void buildLinks(long lastPage) {
        searchParams.withPage(PageNumber.of(1L));
        firstLink = uriWithParams(searchParams.buildQueryParams());

        searchParams.withPage(PageNumber.of(lastPage));
        lastLink = uriWithParams(searchParams.buildQueryParams());

        prevLink = selfPageNum == 1L ? null : uriWithParams(searchParams.withPage(
                PageNumber.of(selfPageNum - 1)).buildQueryParams());

        nextLink = selfPageNum == lastPage ? null : uriWithParams(searchParams.withPage(
                PageNumber.of(selfPageNum + 1)).buildQueryParams());
    }

    private URI uriWithParams(String params) {
        return uriInfo.getBaseUriBuilder()
                .path(uriInfo.getPath())
                .replaceQuery(params)
                .build(searchParams.getGatewayAccountId());
    }
}
