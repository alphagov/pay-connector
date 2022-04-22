package uk.gov.pay.connector.charge.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import javax.inject.Inject;

public class LinkPaymentInstrumentToAgreementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkPaymentInstrumentToAgreementService.class);

    private final AgreementDao agreementDao;

    @Inject
    public LinkPaymentInstrumentToAgreementService(AgreementDao agreementDao) {
        this.agreementDao = agreementDao;
    }

    @Transactional
    public void linkPaymentInstrumentFromChargeToAgreementFromCharge(ChargeEntity chargeEntity) {
        chargeEntity.getPaymentInstrument().ifPresentOrElse(paymentInstrumentEntity -> {
            chargeEntity.getAgreementId().ifPresentOrElse(agreementId -> {
                agreementDao.findByExternalId(agreementId).ifPresentOrElse(agreementEntity -> {
                    agreementEntity.setPaymentInstrument(paymentInstrumentEntity);
                    paymentInstrumentEntity.setPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE);
                }, () -> LOGGER.error("Charge {} references agreement {} but that agreement does not exist", chargeEntity.getExternalId(), agreementId));
            }, () -> LOGGER.error("Expected charge {} to have an agreement but it does not have one", chargeEntity.getExternalId()));
        }, () -> LOGGER.error("Expected charge {} to have a payment instrument but it does not have one", chargeEntity.getExternalId()));
    }

}
