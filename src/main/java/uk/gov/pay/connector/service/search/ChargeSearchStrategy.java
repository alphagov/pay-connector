package uk.gov.pay.connector.service.search;

import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.SearchParams;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.service.ChargeService;

import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;

import static uk.gov.pay.connector.model.ChargeResponse.aChargeResponseBuilder;

public class ChargeSearchStrategy extends AbstractSearchStrategy<ChargeEntity> implements SearchStrategy {

    private ChargeService chargeService;
    private ChargeDao chargeDao;

    public ChargeSearchStrategy(ChargeService chargeService, ChargeDao chargeDao, CardTypeDao cardTypeDao) {
        super(cardTypeDao);
        this.chargeService = chargeService;
        this.chargeDao = chargeDao;
    }

    @Override
    protected long getTotalFor(SearchParams params) {
        return chargeDao.getTotalFor(params);
    }

    @Override
    protected List<ChargeEntity> findAllBy(SearchParams params) {
        return chargeDao.findAllBy(params);
    }

    @Override
    protected ChargeResponse buildResponse(UriInfo uriInfo, ChargeEntity chargeEntity, Map<String, String> cardBrandToLabel) {
        return chargeService.populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, chargeEntity).build();
    }

}
