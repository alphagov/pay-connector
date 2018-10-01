package uk.gov.pay.connector.service;

import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.dao.SearchParams;
import uk.gov.pay.connector.model.SearchRefundsResponse;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.service.search.AbstractSearchStrategy;
import uk.gov.pay.connector.service.search.BuildResponseStrategy;
import uk.gov.pay.connector.service.search.SearchStrategy;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

import static javax.ws.rs.HttpMethod.GET;
import static uk.gov.pay.connector.model.SearchRefundsResponse.anAllRefundsResponseBuilder;

public class RefundSearchStrategy extends AbstractSearchStrategy<RefundEntity, SearchRefundsResponse> implements SearchStrategy, BuildResponseStrategy<RefundEntity, SearchRefundsResponse> {

    private RefundDao refundDao;

    public RefundSearchStrategy(RefundDao refundDao) {
        this.refundDao = refundDao;
    }

    @Override
    public long getTotalFor(SearchParams params) {
        return refundDao.getTotalFor(params);
    }

    @Override
    public List<RefundEntity> findAllBy(SearchParams params) {
        return refundDao.findAllBy(params);
    }

    @Override
    public SearchRefundsResponse buildResponse(UriInfo uriInfo, RefundEntity refundEntity) {
        return populateResponseBuilderWith(anAllRefundsResponseBuilder(), uriInfo, refundEntity).build();
    }

    private SearchRefundsResponse.SearchRefundsResponseBuilder populateResponseBuilderWith(
            SearchRefundsResponse.SearchRefundsResponseBuilder responseBuilder,
            UriInfo uriInfo, RefundEntity refundEntity) {
        Long accountId = refundEntity.getChargeEntity().getGatewayAccount().getId();
        return responseBuilder
                .withRefundId(refundEntity.getExternalId())
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(refundEntity.getCreatedDate()))
                .withStatus(String.valueOf(refundEntity.getStatus()))
                .withChargeId(refundEntity.getChargeEntity().getExternalId())
                .withAmountSubmitted(refundEntity.getAmount())
                .withLink("self", GET, selfUriFor(uriInfo, accountId))
                .withLink("payment_url", GET, paymentLinkFor(uriInfo, refundEntity.getChargeEntity().getExternalId()));
    }

    private URI paymentLinkFor(UriInfo uriInfo, String externalId) {
        String targetPath = "/v1/payments/{externalId}"
                .replace("{externalId}", externalId);
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path(targetPath)
                .build();
    }

    private URI selfUriFor(UriInfo uriInfo, Long accountId) {
        String targetPath = "/v1/refunds/account/{accountId}"
                .replace("{accountId}", String.valueOf(accountId));
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path(targetPath)
                .build();
    }
}
