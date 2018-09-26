package uk.gov.pay.connector.service.search;

import uk.gov.pay.connector.dao.SearchParams;
import uk.gov.pay.connector.resources.PaginationResponseBuilder;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public abstract class AbstractSearchStrategy<T, R> implements SearchStrategy, BuildResponseStrategy<T, R> {
    
    protected AbstractSearchStrategy() { }

    @Override
    public Response search(SearchParams searchParams, UriInfo uriInfo) {
        Long totalCount = getTotalFor(searchParams);
        Long size = searchParams.getDisplaySize();
        if (totalCount > 0 && size > 0) {
            long lastPage = (totalCount + size - 1) / size;
            if (searchParams.getPage() > lastPage || searchParams.getPage() < 1) {
                return notFoundResponse("the requested page not found");
            }
        }
        List<T> list = findAllBy(searchParams);
        List<R> chargesResponses =
                list.stream()
                        .map(c -> buildResponse(uriInfo, c))
                        .collect(Collectors.toList());

        return new PaginationResponseBuilder<R>(searchParams, uriInfo)
                .withResponses(chargesResponses)
                .withTotalCount(totalCount)
                .buildResponse();
    }
}
