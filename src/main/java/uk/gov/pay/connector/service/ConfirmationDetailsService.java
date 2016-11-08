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

public class ConfirmationDetailsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ChargeDao chargeDao;
    private ChargeCardDetailsDao chargeCardDetailsDao;

    @Inject
    public ConfirmationDetailsService(ChargeCardDetailsDao chargeCardDetailsDao, ChargeDao chargeDao) {
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

    @Transactional
    public void doRemove(ChargeEntity chargeEntity) {
        if (chargeEntity.getStatus().equals(ChargeStatus.AUTHORISATION_SUCCESS.getValue())) {
            throw new IllegalStateRuntimeException(chargeEntity.getExternalId());
        }
        ChargeCardDetailsEntity entity = chargeEntity.getChargeCardDetailsEntity();
        if (entity != null) {
            chargeEntity.setChargeCardDetailsEntity(null);
            ChargeCardDetailsEntity reloadedEntity = chargeCardDetailsDao.merge(entity);
            chargeCardDetailsDao.remove(reloadedEntity);
            logger.info("Removed confirmation details for charge - charge_external_id={}", chargeEntity.getExternalId());
        }
    }
}
