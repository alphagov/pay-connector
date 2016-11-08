package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeCardDetailsDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeCardDetailsEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;

/**
 * TODO: not sure if this service is necessary or the DAO.
 * Given that we can treat ChargeCardDetails as a child entity we should be able to manage this through charge as there is no special business logic here
 * Will probably refactor out this later
 */

public class ChargeCardDetailsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ChargeDao chargeDao;
    private ChargeCardDetailsDao chargeCardDetailsDao;

    @Inject
    public ChargeCardDetailsService(ChargeCardDetailsDao chargeCardDetailsDao, ChargeDao chargeDao) {
        this.chargeCardDetailsDao = chargeCardDetailsDao;
        this.chargeDao = chargeDao;
    }

    @Transactional
    public ChargeCardDetailsEntity doStore(String externalId, Card cardDetails) {
        ChargeEntity chargeEntity = chargeDao.
                findByExternalId(externalId).
                orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));

        if (!chargeEntity.getStatus().equals(ChargeStatus.AUTHORISATION_SUCCESS.getValue())) {
            throw new IllegalStateRuntimeException(chargeEntity.getExternalId());
        }

        chargeEntity.setCardBrand(cardDetails.getCardBrand());

        ChargeCardDetailsEntity detailsEntity = new ChargeCardDetailsEntity(chargeEntity);
        detailsEntity.setBillingAddress(cardDetails.getAddress());
        detailsEntity.setCardHolderName(cardDetails.getCardHolder());
        detailsEntity.setExpiryDate(cardDetails.getEndDate());
        detailsEntity.setLastDigitsCardNumber(StringUtils.right(cardDetails.getCardNo(), 4));
        chargeEntity.setChargeCardDetailsEntity(detailsEntity);
        chargeCardDetailsDao.persist(detailsEntity);
        logger.info("Stored confirmation details for charge - charge_external_id={}", externalId);
        return detailsEntity;
    }
}
