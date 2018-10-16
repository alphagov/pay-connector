package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.charge.dao.SearchParams;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class SearchRefundsService {

    private static final Long MAX_DISPLAY_SIZE = 500L;
    private RefundDao refundDao;
    private RefundSearchStrategy refundSearchStrategy;

    @Inject
    public SearchRefundsService(RefundDao refundDao) {
        this.refundDao = refundDao;
    }

    public Response getAllRefunds(UriInfo uriInfo, Long accountId, Long pageNumber, Long displaySize) {
        List<Pair<String, Long>> queryParams = ImmutableList.of(
                Pair.of("page", pageNumber),
                Pair.of("display_size", displaySize));
        refundSearchStrategy = new RefundSearchStrategy(refundDao);
        List<String> errors = validateQueryParams(queryParams);
        if (errors.isEmpty()) {
            SearchParams searchParams = new SearchParams()
                    .withGatewayAccountId(accountId)
                    .withDisplaySize(calculateDisplaySize(displaySize))
                    .withPage(pageNumber != null ? pageNumber : 1);
            return refundSearchStrategy.search(searchParams, uriInfo);
        } else {
            return badRequestResponse(errors);
        }
    }

    private Long calculateDisplaySize(Long displaySize) {
        return displaySize == null ? MAX_DISPLAY_SIZE :
                (displaySize > MAX_DISPLAY_SIZE) ? MAX_DISPLAY_SIZE : displaySize;
    }

    private static List<String> validateQueryParams(List<Pair<String, Long>> nonNegativePairMap) {
        Map<String, String> invalidQueryParams = new HashMap<>();
        List<String> invalidResponse = newArrayList();

        nonNegativePairMap.forEach(param -> {
            if (param.getRight() != null && param.getRight() < 1) {
                invalidQueryParams.put(param.getLeft(), "query param '%s' should be a non zero positive integer");
            }
        });

        if (!invalidQueryParams.isEmpty()) {
            invalidResponse = invalidQueryParams.keySet().stream()
                    .map(param -> String.format(invalidQueryParams.get(param), param))
                    .collect(Collectors.toList());
        }
        return invalidResponse;
    }
}
