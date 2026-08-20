package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.service.LinkPaymentInstrumentToAgreementService;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.model.agreement.AgreementInactivated;
import uk.gov.pay.connector.gateway.adyen.response.AdyenTokenNotification;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenRecurringTokenNotificationService;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static com.adyen.model.tokenizationwebhooks.TokenizationCreatedDetailsNotificationRequest.TypeEnum.RECURRING_TOKEN_CREATED;
import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.SHOPPER_REFERENCE_DELIMITER;
import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.ACTIVE;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.CANCELLED;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.CREATED;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.INACTIVE;
import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_INSTRUMENT_EXTERNAL_ID;

public class AdyenTokenWebhookNotificationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenTokenWebhookNotificationHandler.class);
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "recurring.token.created",
            "recurring.token.disabled"
    );

    private final PaymentInstrumentDao paymentInstrumentDao;
    private final LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService;
    private final AdyenRecurringTokenNotificationService adyenRecurringTokenNotificationService;
    private final AgreementDao agreementDao;
    private LedgerService ledgerService;

    @Inject
    public AdyenTokenWebhookNotificationHandler(PaymentInstrumentDao paymentInstrumentDao, AgreementDao agreementDao, LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService, AdyenRecurringTokenNotificationService adyenRecurringTokenNotificationService, LedgerService ledgerService) {

        this.paymentInstrumentDao = paymentInstrumentDao;
        this.agreementDao = agreementDao;
        this.linkPaymentInstrumentToAgreementService = linkPaymentInstrumentToAgreementService;
        this.adyenRecurringTokenNotificationService = adyenRecurringTokenNotificationService;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public void process(String payload) {
        AdyenTokenNotification notification = adyenRecurringTokenNotificationService.deserialisePayload(payload, AdyenTokenNotification.class);

        if (!SUPPORTED_EVENT_TYPES.contains(notification.type())) {
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
            if (RECURRING_TOKEN_CREATED.getValue().equals(notification.type())) {
                activateAgreement(agreementEntity.get(), paymentInstrument, notification);
            } else {
                inactivateAgreement(agreementEntity.get(), paymentInstrument);
            }
        }, () -> LOGGER.atInfo().setMessage("Payment instrument not found for charge in Adyen token webhook, ignoring")
                .addKeyValue("EXTERNAL_CHARGE_ID", chargeExternalId)
                .log());
    }

    private void activateAgreement(AgreementEntity agreementEntity, PaymentInstrumentEntity paymentInstrument, AdyenTokenNotification notification) {
        if (paymentInstrument.getStatus() == ACTIVE) {
            logIgnoreWebhookBasedOnDuplicateStatus(paymentInstrument, agreementEntity.getExternalId());
            return;
        }
        paymentInstrument.setRecurringAuthToken(Map.of(STORED_PAYMENT_METHOD_ID, notification.data().storedPaymentMethodId()));

        linkPaymentInstrumentToAgreementService.linkPaymentInstrumentToAgreement(agreementEntity, paymentInstrument);
    }

    private void inactivateAgreement(AgreementEntity agreementEntity, PaymentInstrumentEntity paymentInstrument) {
        if (paymentInstrument.getStatus() == INACTIVE || paymentInstrument.getStatus() == CANCELLED) {
            logIgnoreWebhookBasedOnDuplicateStatus(paymentInstrument, agreementEntity.getExternalId());
            return;
        }

        if (paymentInstrument.getStatus() == CREATED) {
            LOGGER.atError().setMessage("Payment instrument is not in the correct state to be inactivated, ignoring Adyen token webhook")
                    .addKeyValue(AGREEMENT_EXTERNAL_ID, agreementEntity.getExternalId())
                    .addKeyValue(PAYMENT_INSTRUMENT_EXTERNAL_ID, paymentInstrument.getExternalId())
                    .log();
            return;
        }
        var inactivatedEvent = AgreementInactivated.from(agreementEntity, "Adyen agreement inactivated", Instant.now());
        ledgerService.postEvent(inactivatedEvent);

        paymentInstrument.setStatus(INACTIVE);
        LOGGER.atInfo().setMessage("Payment instrument and agreement successfully inactivated")
                .addKeyValue(AGREEMENT_EXTERNAL_ID, agreementEntity.getExternalId())
                .addKeyValue(PAYMENT_INSTRUMENT_EXTERNAL_ID, paymentInstrument.getExternalId())
                .log();
    }

    private static void logInvalidShopperReference() {
        LOGGER.atInfo().setMessage("Invalid shopper reference").log();
    }

    private static void logIgnoreWebhookBasedOnDuplicateStatus(PaymentInstrumentEntity paymentInstrument, String agreementExternalId) {
        LOGGER.atInfo().setMessage("Payment instrument is already in " + paymentInstrument.getStatus() + " state, ignoring Adyen token webhook")
                .addKeyValue(AGREEMENT_EXTERNAL_ID, agreementExternalId)
                .addKeyValue(PAYMENT_INSTRUMENT_EXTERNAL_ID, paymentInstrument.getExternalId())
                .log();
    }

}
