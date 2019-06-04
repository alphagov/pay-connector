package uk.gov.pay.connector.charge.service;

import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.service.search.AbstractSearchStrategy;
import uk.gov.pay.connector.common.service.search.SearchStrategy;

import javax.ws.rs.core.UriInfo;
import java.util.List;

import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;

public class ChargeSearchStrategy extends AbstractSearchStrategy<ChargeEntity, ChargeResponse> implements SearchStrategy {

    private ChargeService chargeService;
    private ChargeDao chargeDao;

    public ChargeSearchStrategy(ChargeService chargeService, ChargeDao chargeDao) {
        this.chargeService = chargeService;
        this.chargeDao = chargeDao;
    }

    @Override
    public long getTotalFor(SearchParams params) {
        return chargeDao.getTotalFor(params);
    }

    @Override
    public List<ChargeEntity> findAllBy(SearchParams params) {
        return chargeDao.findAllBy(params);
    }

    @Override
    public ChargeResponse buildResponse(UriInfo uriInfo, ChargeEntity chargeEntity) {
        return chargeService.populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, chargeEntity, true).build();
    }
}
