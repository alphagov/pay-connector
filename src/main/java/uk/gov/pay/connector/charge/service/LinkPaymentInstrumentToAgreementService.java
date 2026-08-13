package uk.gov.pay.connector.charge.service;

import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.model.agreement.AgreementSetUp;
import uk.gov.pay.connector.events.model.charge.PaymentInstrumentConfirmed;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;

import java.time.InstantSource;
import java.util.List;
import java.util.Map;

import static java.util.function.Predicate.not;
import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_INSTRUMENT_EXTERNAL_ID;

public class LinkPaymentInstrumentToAgreementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkPaymentInstrumentToAgreementService.class);

    private final PaymentInstrumentDao paymentInstrumentDao;
    private final LedgerService ledgerService;
    private final TaskQueueService taskQueueService;
    private final InstantSource instantSource;

    @Inject
    public LinkPaymentInstrumentToAgreementService(PaymentInstrumentDao paymentInstrumentDao,
                                                   LedgerService ledgerService,
                                                   TaskQueueService taskQueueService,
                                                   InstantSource instantSource) {
        this.paymentInstrumentDao = paymentInstrumentDao;
        this.ledgerService = ledgerService;
        this.taskQueueService = taskQueueService;
        this.instantSource = instantSource;
    }

    @Transactional
    public void linkPaymentInstrumentFromChargeToAgreement(ChargeEntity chargeEntity) {
        chargeEntity.getPaymentInstrument().ifPresentOrElse(paymentInstrumentEntity ->
                chargeEntity.getAgreement().ifPresentOrElse(agreementEntity ->
                        paymentInstrumentEntity.getRecurringAuthToken().filter(not(Map::isEmpty)).ifPresentOrElse(_ ->
                                linkPaymentInstrumentToAgreement(agreementEntity, paymentInstrumentEntity), () -> LOGGER.atInfo()
                                .setMessage("Payment instrument doesn't have a token associated. Not linked the payment instrument to the agreement")
                                .addKeyValue(PAYMENT_INSTRUMENT_EXTERNAL_ID, paymentInstrumentEntity.getExternalId())
                                .addKeyValue(AGREEMENT_EXTERNAL_ID, agreementEntity.getExternalId())
                                .log()
                        ), () -> LOGGER.error("Expected charge {} to have an agreement but it does not have one", chargeEntity.getExternalId())), () -> LOGGER.error("Expected charge {} to have a payment instrument but it does not have one", chargeEntity.getExternalId()));
    }

    @Transactional
    public void linkPaymentInstrumentToAgreement(AgreementEntity agreementEntity, PaymentInstrumentEntity paymentInstrumentEntity) {
        cancelActivePaymentInstruments(agreementEntity);
        agreementEntity.setPaymentInstrument(paymentInstrumentEntity);
        paymentInstrumentEntity.setAgreementExternalId(agreementEntity.getExternalId());
        paymentInstrumentEntity.setStatus(PaymentInstrumentStatus.ACTIVE);
        ledgerService.postEvent(List.of(
                AgreementSetUp.from(agreementEntity, instantSource.instant()),
                PaymentInstrumentConfirmed.from(agreementEntity, instantSource.instant())
        ));
        LOGGER.atInfo()
                .setMessage("Agreement successfully set up with payment instrument")
                .addKeyValue(AGREEMENT_EXTERNAL_ID, agreementEntity.getExternalId())
                .addKeyValue(PAYMENT_INSTRUMENT_EXTERNAL_ID, paymentInstrumentEntity.getExternalId())
                .log();
    }

    private void cancelActivePaymentInstruments(AgreementEntity agreement) {
        List<PaymentInstrumentEntity> paymentInstruments = paymentInstrumentDao.findPaymentInstrumentsByAgreementAndStatus(
                agreement.getExternalId(), PaymentInstrumentStatus.ACTIVE);
        paymentInstruments.forEach(paymentInstrument -> {
            paymentInstrument.setStatus(PaymentInstrumentStatus.CANCELLED);
            taskQueueService.addDeleteStoredPaymentDetailsTask(agreement, paymentInstrument);
        });

    }

}
