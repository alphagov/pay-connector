package uk.gov.pay.connector.service.search;

import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.resources.ChargesPaginationResponseBuilder;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public abstract class AbstractSearchStrategy<T> implements SearchStrategy {

    @Override
    public Response search(ChargeSearchParams searchParams, UriInfo uriInfo) {
        Long totalCount = getTotalFor(searchParams);
        Long size = searchParams.getDisplaySize();
        if (totalCount > 0 && size > 0) {
            long lastPage = (totalCount + size - 1) / size;
            if (searchParams.getPage() > lastPage || searchParams.getPage() < 1) {
                return notFoundResponse("the requested page not found");
            }
        }

        List<T> transactions = findAllBy(searchParams);
        List<ChargeResponse> chargesResponse =
                transactions.stream()
                        .map(transaction -> buildResponse(uriInfo, transaction)
                        ).collect(Collectors.toList());

        return new ChargesPaginationResponseBuilder(searchParams, uriInfo)
                .withChargeResponses(chargesResponse)
                .withTotalCount(totalCount)
                .buildResponse();
    }

    abstract protected long getTotalFor(ChargeSearchParams params);

    abstract protected List<T> findAllBy(ChargeSearchParams params);

    abstract protected ChargeResponse buildResponse(UriInfo uriInfo, T transaction);

}
