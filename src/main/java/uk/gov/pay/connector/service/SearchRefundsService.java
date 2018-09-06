package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.SearchRefundsResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.resources.ChargesPaginationResponseBuilder;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static javax.ws.rs.HttpMethod.GET;
import static uk.gov.pay.connector.model.SearchRefundsResponse.aAllRefundsResponseBuilder;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class SearchRefundsService {

    private RefundDao refundDao;
    private final PaymentProviders providers;

    @Inject
    public SearchRefundsService(RefundDao refundDao, PaymentProviders providers) {
        this.refundDao = refundDao;
        this.providers = providers;
    }

    public Response getAllRefunds(UriInfo uriInfo, Long accountId, Long pageNumber, Long displaySize) {
        List<Pair<String, Long>> queryParams = ImmutableList.of(
                Pair.of("page", pageNumber),
                Pair.of("display_size", displaySize));

        return validateQueryParams(queryParams)
                .map(ResponseUtil::badRequestResponse)
                .orElseGet(() -> {
                    ChargeSearchParams searchParams = new ChargeSearchParams()
                            .withGatewayAccountId(accountId)
                            .withDisplaySize(displaySize != null ? displaySize : 0)
                            .withPage(pageNumber != null ? pageNumber : 1);
                    return search(searchParams, uriInfo);
                });
    }
    
    private Response search(ChargeSearchParams searchParams, UriInfo uriInfo) {
        Long totalCount = refundDao.getTotalFor(searchParams);
        Long size = searchParams.getDisplaySize();
        if (totalCount > 0 && size > 0) {
            long lastPage = (totalCount + size - 1) / size;
            if (searchParams.getPage() > lastPage || searchParams.getPage() < 1) {
                return notFoundResponse("the requested page not found");
            }
        }

        List<RefundEntity> refunds = refundDao.findAllBy(searchParams);
        List<SearchRefundsResponse> refundResponses =
                refunds.stream()
                        .map(refund -> buildResponse(uriInfo, refund))
                        .collect(Collectors.toList());

        return new ChargesPaginationResponseBuilder(searchParams, uriInfo)
                .withResponses(refundResponses)
                .withTotalCount(totalCount)
                .buildResponse();
    }
    
    private SearchRefundsResponse buildResponse(UriInfo uriInfo, RefundEntity refundEntity){
        return populateResponseBuilderWith(aAllRefundsResponseBuilder(), uriInfo, refundEntity).build();
    }

    private SearchRefundsResponse.SearchRefundsResponseBuilder populateResponseBuilderWith(
            SearchRefundsResponse.SearchRefundsResponseBuilder responseBuilder, 
            UriInfo uriInfo, RefundEntity refundEntity) {

        Long accountId = refundEntity.getChargeEntity().getGatewayAccount().getId();
       return responseBuilder
                .withRefundId(refundEntity.getExternalId())
                .withRefunds(buildRefundSummary(refundEntity))
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(refundEntity.getCreatedDate()))
                .withLink("self", GET, selfLinkFor(uriInfo, accountId));
    }
                
    private URI selfLinkFor(UriInfo uriInfo, Long accountId) {
    return uriInfo.getBaseUriBuilder()
            .path("/v1/api/accounts/{accountId}/refunds")
            .build(accountId);
    }

    private ChargeResponse.RefundSummary buildRefundSummary(RefundEntity refundEntity) {
        ChargeEntity chargeEntity = refundEntity.getChargeEntity();
        ChargeResponse.RefundSummary refund = new ChargeResponse.RefundSummary();
        refund.setStatus(providers.byName(chargeEntity.getPaymentGatewayName()).getExternalChargeRefundAvailability(chargeEntity).getStatus());
        refund.setAmountSubmitted(chargeEntity.getRefundedAmount());
        refund.setAmountAvailable(chargeEntity.getTotalAmountToBeRefunded());
        return refund;
    }

    private static Optional<List> validateQueryParams(List<Pair<String, Long>> nonNegativePairMap) {
        Map<String, String> invalidQueryParams = new HashMap<>();

        nonNegativePairMap.forEach(param -> {
            if (param.getRight() != null && param.getRight() < 1) {
                invalidQueryParams.put(param.getLeft(), "query param '%s' should be a non zero positive integer");
            }
        });

        if (!invalidQueryParams.isEmpty()) {
            List<String> invalidResponse = newArrayList();
            invalidResponse.addAll(invalidQueryParams.keySet()
                    .stream()
                    .map(param -> String.format(invalidQueryParams.get(param), param))
                    .collect(Collectors.toList()));
            return Optional.of(invalidResponse);
        }
        return Optional.empty();
    }
}
