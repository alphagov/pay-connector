package uk.gov.pay.connector.charge.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.model.charge.AgreementSetup;
import uk.gov.pay.connector.events.model.charge.PaymentInstrumentConfirmed;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import javax.inject.Inject;
import java.time.Clock;
import java.util.List;

public class LinkPaymentInstrumentToAgreementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkPaymentInstrumentToAgreementService.class);

    private final AgreementDao agreementDao;
    private final LedgerService ledgerService;
    private final Clock clock;

    @Inject
    public LinkPaymentInstrumentToAgreementService(AgreementDao agreementDao, LedgerService ledgerService, Clock clock) {
        this.agreementDao = agreementDao;
        this.ledgerService = ledgerService;
        this.clock = clock;
    }

    @Transactional
    public void linkPaymentInstrumentFromChargeToAgreementFromCharge(ChargeEntity chargeEntity) {
        chargeEntity.getPaymentInstrument().ifPresentOrElse(paymentInstrumentEntity -> {
            chargeEntity.getAgreementId().ifPresentOrElse(agreementId -> {
                agreementDao.findByExternalId(agreementId, chargeEntity.getGatewayAccount().getId()).ifPresentOrElse(agreementEntity -> {
                    agreementEntity.setPaymentInstrument(paymentInstrumentEntity);
                    paymentInstrumentEntity.setPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE);
                    ledgerService.postEvent(List.of(
                            AgreementSetup.from(agreementEntity, clock.instant()),
                            PaymentInstrumentConfirmed.from(agreementEntity, clock.instant())
                    ));
                }, () -> LOGGER.error("Charge {} references agreement {} but that agreement does not exist", chargeEntity.getExternalId(), agreementId));
            }, () -> LOGGER.error("Expected charge {} to have an agreement but it does not have one", chargeEntity.getExternalId()));
        }, () -> LOGGER.error("Expected charge {} to have a payment instrument but it does not have one", chargeEntity.getExternalId()));
    }

}
