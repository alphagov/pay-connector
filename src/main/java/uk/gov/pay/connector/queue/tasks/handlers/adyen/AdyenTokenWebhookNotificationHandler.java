package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.charge.service.LinkPaymentInstrumentToAgreementService;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.response.AdyenTokenNotification;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.Map;

import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.SHOPPER_REFERENCE_DELIMITER;
import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_INSTRUMENT_EXTERNAL_ID;

public class AdyenTokenWebhookNotificationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenTokenWebhookNotificationHandler.class);
    private static final String RECURRING_TOKEN_CREATED = "recurring.token.created";

    private final PaymentInstrumentDao paymentInstrumentDao;
    private final LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService;
    private final AdyenNotificationService adyenNotificationService;
    private final AgreementDao agreementDao;

    @Inject
    public AdyenTokenWebhookNotificationHandler(PaymentInstrumentDao paymentInstrumentDao, AgreementDao agreementDao, LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService, AdyenNotificationService adyenNotificationService) {

        this.paymentInstrumentDao = paymentInstrumentDao;
        this.agreementDao = agreementDao;
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

        var agreementExternalId = splitShopperReference[0];

        var agreementEntity = agreementDao.findByExternalId(agreementExternalId);
        if (agreementEntity.isEmpty()) {
            LOGGER.atInfo().setMessage("Agreement not found, ignoring Adyen token webhook").addKeyValue(AGREEMENT_EXTERNAL_ID, agreementExternalId).log();
            return;
        }

        var chargeExternalId = splitShopperReference[1];

        paymentInstrumentDao.findByChargeExternalId(chargeExternalId).ifPresentOrElse(paymentInstrument -> {
            if (paymentInstrument.getStatus() == PaymentInstrumentStatus.ACTIVE) {
                LOGGER.atInfo().setMessage("Payment instrument is already in ACTIVE state, ignoring Adyen token webhook")
                        .addKeyValue(AGREEMENT_EXTERNAL_ID, agreementExternalId).addKeyValue(PAYMENT_INSTRUMENT_EXTERNAL_ID, paymentInstrument.getExternalId())
                        .log();
                return;
            }
            paymentInstrument.setRecurringAuthToken(Map.of(AdyenRequestFactory.STORED_PAYMENT_METHOD_ID, notification.data().storedPaymentMethodId()));

            linkPaymentInstrumentToAgreementService.linkPaymentInstrumentToAgreement(agreementEntity.get(), paymentInstrument);
        }, () -> LOGGER.atInfo().setMessage("Payment instrument not found for charge in Adyen token webhook, ignoring")
                .addKeyValue("EXTERNAL_CHARGE_ID", chargeExternalId)
                .log());
    }

    private static void logInvalidShopperReference() {
        LOGGER.atInfo().setMessage("Invalid shopper reference").log();
    }
}
