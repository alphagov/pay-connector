package uk.gov.pay.connector.refund.service;

import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.refund.dao.RefundDao;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

public class SearchRefundsService {

    private RefundDao refundDao;
    private RefundSearchStrategy refundSearchStrategy;

    @Inject
    public SearchRefundsService(RefundDao refundDao) {
        this.refundDao = refundDao;
    }

    public Response getAllRefunds(UriInfo uriInfo, SearchParams searchParams) {
        refundSearchStrategy = new RefundSearchStrategy(refundDao);
        
        //todo defaults?
        return refundSearchStrategy.search(searchParams, uriInfo);
    }
}

