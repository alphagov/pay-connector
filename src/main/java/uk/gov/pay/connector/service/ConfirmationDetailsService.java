package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ConfirmationDetailsDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.ConfirmationDetailsEntity;

import javax.inject.Inject;

public class ConfirmationDetailsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ChargeDao chargeDao;
    private ConfirmationDetailsDao confirmationDetailsDao;

    @Inject
    public ConfirmationDetailsService(ConfirmationDetailsDao confirmationDetailsDao, ChargeDao chargeDao) {
        this.confirmationDetailsDao = confirmationDetailsDao;
        this.chargeDao = chargeDao;
    }

    @Transactional
    public ConfirmationDetailsEntity doStore(String externalId, Card cardDetails) {
        logger.info("storing confirmation details for charge ID: {}", externalId);
        ChargeEntity chargeEntity = chargeDao.
                findByExternalId(externalId).
                orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));

        if (!chargeEntity.getStatus().equals(ChargeStatus.AUTHORISATION_SUCCESS.getValue())) {
            throw new IllegalStateRuntimeException(chargeEntity.getExternalId());
        }

        ConfirmationDetailsEntity detailsEntity = new ConfirmationDetailsEntity(chargeEntity);
        detailsEntity.setBillingAddress(cardDetails.getAddress());
        detailsEntity.setCardHolderName(cardDetails.getCardHolder());
        detailsEntity.setExpiryDate(cardDetails.getEndDate());
        detailsEntity.setLastDigitsCardNumber(StringUtils.right(cardDetails.getCardNo(), 4));
        chargeEntity.setConfirmationDetailsEntity(detailsEntity);
        confirmationDetailsDao.persist(detailsEntity);
        return detailsEntity;
    }
}
