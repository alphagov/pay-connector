package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.service.LinkPaymentInstrumentToAgreementService;
import uk.gov.pay.connector.gateway.adyen.response.AdyenTokenNotification;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.Map;

import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.SHOPPER_REFERENCE_DELIMITER;
import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.ACTIVE;
import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_INSTRUMENT_EXTERNAL_ID;

public class AdyenTokenWebhookNotificationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenTokenWebhookNotificationHandler.class);
    private static final String RECURRING_TOKEN_CREATED = "recurring.token.created";

    private final PaymentInstrumentDao paymentInstrumentDao;
    private final ChargeDao chargeDao;
    private final LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService;
    private final AdyenNotificationService adyenNotificationService;
    private final AgreementDao agreementDao;

    @Inject
    public AdyenTokenWebhookNotificationHandler(PaymentInstrumentDao paymentInstrumentDao, AgreementDao agreementDao, ChargeDao chargeDao, LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService, AdyenNotificationService adyenNotificationService) {

        this.paymentInstrumentDao = paymentInstrumentDao;
        this.agreementDao = agreementDao;
        this.chargeDao = chargeDao;
        this.linkPaymentInstrumentToAgreementService = linkPaymentInstrumentToAgreementService;
        this.adyenNotificationService = adyenNotificationService;
    }

    @Transactional
    public void process(String payload) {
        AdyenTokenNotification notification = adyenNotificationService.deserialiseTokenPayload(payload, AdyenTokenNotification.class);

        if (!RECURRING_TOKEN_CREATED.equals(notification.type())) {
            LOGGER.atInfo().setMessage("Ignoring Adyen token webhook notification with unsupported type").addKeyValue("type", notification.type()).log();
            return;
        }

        var shopperReference = notification.data().shopperReference();

        if (shopperReference == null || shopperReference.isBlank()) {
            logInvalidShopperReference();
            return;
        }

        // shopperReference format: "{agreementExternalId}-{chargeExternalId}"
        var splitShopperReference = shopperReference.split(SHOPPER_REFERENCE_DELIMITER);

        if (splitShopperReference.length != 2 || !RandomIdGenerator.isValidIdLength(splitShopperReference)) {
            logInvalidShopperReference();
            return;
        }

        var webhookAgreementExternalId = splitShopperReference[0];
        var webhookChargeExternalId = splitShopperReference[1];

        var agreementEntity = agreementDao.findByExternalId(webhookAgreementExternalId);
        if (agreementEntity.isEmpty()) {
            LOGGER.atInfo().setMessage("Agreement not found, ignoring Adyen token webhook").addKeyValue(AGREEMENT_EXTERNAL_ID, webhookAgreementExternalId).log();
            return;
        }
        chargeDao.findLatestChargeForAgreementId(webhookAgreementExternalId).ifPresentOrElse(latestCharge ->
                        paymentInstrumentDao.findByChargeExternalId(webhookChargeExternalId).ifPresentOrElse(paymentInstrument -> {
                                    paymentInstrument.setRecurringAuthToken(Map.of(STORED_PAYMENT_METHOD_ID,
                                            notification.data().storedPaymentMethodId()));

                                    var latestChargeId = latestCharge.getExternalId();

                                    processWebhooks(latestChargeId, paymentInstrument, webhookChargeExternalId, webhookAgreementExternalId, agreementEntity.get());
                                },
                                () -> LOGGER.atInfo()
                                        .setMessage("Payment instrument not found for charge in Adyen token webhook, ignoring")
                                        .addKeyValue("EXTERNAL_CHARGE_ID", webhookChargeExternalId)
                                        .log()),
                () -> LOGGER.atInfo()
                        .setMessage("No charges for the agreement were found with payment instruments in a valid state (ACTIVE or CREATED)")
                        .addKeyValue(AGREEMENT_EXTERNAL_ID, webhookAgreementExternalId)
                        .log());
    }

    private void processWebhooks(String latestChargeId, PaymentInstrumentEntity paymentInstrument, String webhookChargeExternalId,
                                 String webhookAgreementExternalId, AgreementEntity agreementEntity) {
        // Webhook refers to latest Payment Instrument
        if (latestChargeId.equals(webhookChargeExternalId)) {
            if (paymentInstrument.getStatus() == ACTIVE) {
                LOGGER.atInfo().setMessage("Payment instrument is already in ACTIVE state, ignoring Adyen token webhook")
                        .addKeyValue(AGREEMENT_EXTERNAL_ID, webhookAgreementExternalId)
                        .addKeyValue(PAYMENT_INSTRUMENT_EXTERNAL_ID, paymentInstrument.getExternalId())
                        .log();
                return;
            }
            linkPaymentInstrumentToAgreementService.linkPaymentInstrumentToAgreement(agreementEntity, paymentInstrument);
        }
        // Webhook does not refer to latest - cancel payment instrument
        else {
            linkPaymentInstrumentToAgreementService.cancelPaymentInstrument(agreementEntity, paymentInstrument);
        }
    }

    private static void logInvalidShopperReference() {
        LOGGER.atInfo().setMessage("Invalid shopper reference").log();
    }
}
