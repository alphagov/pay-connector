package uk.gov.pay.connector.common.service;

import black.door.hate.HalRepresentation;
import black.door.hate.JacksonHalResource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.SearchParams;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static javax.ws.rs.core.Response.ok;

public class PaginationResponseBuilder<T> {

    private static final Logger logger = LoggerFactory.getLogger(PaginationResponseBuilder.class);

    private SearchParams searchParams;
    private UriInfo uriInfo;
    private List<T> responses;
    private Long totalCount;
    private Long selfPageNum;
    private String selfLink;
    private String firstLink;
    private String lastLink;
    private String prevLink;
    private String nextLink;

    public PaginationResponseBuilder(SearchParams searchParams, UriInfo uriInfo) {
        this.searchParams = searchParams;
        this.uriInfo = uriInfo;
        selfPageNum = searchParams.getPage();
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
        Long size = searchParams.getDisplaySize();
        long lastPage = totalCount > 0 ? (totalCount + size - 1) / size : 1;
        buildLinks(lastPage);

        HalRepresentation.HalRepresentationBuilder halRepresentationBuilder = HalRepresentation.builder()
                .addProperty("results", responses)
                .addProperty("count", responses.size())
                .addProperty("total", totalCount)
                .addProperty("page", selfPageNum)
                .addLink("self", new Link(selfLink))
                .addLink("first_page", new Link(firstLink))
                .addLink("last_page", new Link(lastLink));

        addLinkNotNull(halRepresentationBuilder, "prev_page", prevLink);
        addLinkNotNull(halRepresentationBuilder, "next_page", nextLink);

        return ok(halRepresentationBuilder.build().toString()).build();
    }

    private void addLinkNotNull(HalRepresentation.HalRepresentationBuilder halRepresentationBuilder, String name, String uri) {
        if (uri != null) {
            halRepresentationBuilder.addLink(name, new Link(uri));
        }
    }

    private void buildLinks(long lastPage) {
        searchParams.withPage(1L);
        firstLink = uriWithParams(searchParams.buildQueryParams());

        searchParams.withPage(lastPage);
        lastLink = uriWithParams(searchParams.buildQueryParams());

        searchParams.withPage(selfPageNum - 1);
        prevLink = selfPageNum == 1L ? null : uriWithParams(searchParams.buildQueryParams());

        searchParams.withPage(selfPageNum + 1);
        nextLink = selfPageNum == lastPage ? null : uriWithParams(searchParams.buildQueryParams());
    }

    private String uriWithParams(String params) {
        URI uri = uriInfo.getBaseUriBuilder().path(uriInfo.getPath()).replaceQuery(params).build(searchParams.getGatewayAccountId());

        if(uri.getPath().contains("/v2/api/accounts")) {
            String query = (uri.getRawQuery() == null) ? StringUtils.EMPTY : ("?" + uri.getRawQuery());
            return (uri.getPath() + query);
        }
        else{
            return uri.toString();       
        }
    }

    private static class Link implements JacksonHalResource {
        String location;

        Link(String location) {
            this.location = location;
        }

        @Override
        public URI location() {
            try {
                return new URI(location);
            } catch (URISyntaxException e) {
                logger.warn("Cannot construct URL from location {}", location);
            }
            return null;
        }
    }
}
