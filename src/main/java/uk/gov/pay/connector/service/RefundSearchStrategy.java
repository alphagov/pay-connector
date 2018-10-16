package uk.gov.pay.connector.service;

import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.charge.dao.SearchParams;
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

import static java.lang.String.format;
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
        String accountId = String.valueOf(refundEntity.getChargeEntity().getGatewayAccount().getId());
        String externalChargeId = refundEntity.getChargeEntity().getExternalId();
        String externalRefundId = refundEntity.getExternalId();

        return responseBuilder
                .withRefundId(externalRefundId)
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(refundEntity.getCreatedDate()))
                .withStatus(String.valueOf(refundEntity.getStatus()))
                .withChargeId(externalChargeId)
                .withAmountSubmitted(refundEntity.getAmount())
                .withLink("self", GET, selfUriFor(uriInfo, accountId, externalChargeId, externalRefundId))
                .withLink("payment_url", GET, paymentLinkFor(uriInfo, accountId, externalChargeId));
    }

    private URI selfUriFor(UriInfo uriInfo, String accountId, String externalChargeId, String externalRefundId) {
        String targetPath = format("/v1/api/accounts/%s/charges/%s/refunds/%s", accountId, externalChargeId, externalRefundId);
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path(targetPath)
                .build();
    }

    private URI paymentLinkFor(UriInfo uriInfo, String accountId, String externalChargeId) {
        String targetPath = format("/v1/api/accounts/%s/charges/%s", accountId, externalChargeId);
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path(targetPath)
                .build();
    }
}
