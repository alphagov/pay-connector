package uk.gov.pay.connector.gateway.adyen.webhook;

import com.adyen.model.tokenizationwebhooks.TokenizationWebhooksHandler;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.LinkPaymentInstrumentToAgreementService;
import uk.gov.pay.connector.gateway.adyen.AdyenRecurringAuthTokenKeys;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;

import java.util.List;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_QUEUED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class AdyenRecurringTokenWebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenRecurringTokenWebhookService.class);

    private static final List<ChargeStatus> STATUSES_ELIGIBLE_FOR_AGREEMENT_LINK = List.of(
            CAPTURE_APPROVED,
            CAPTURE_APPROVED_RETRY,
            AWAITING_CAPTURE_REQUEST,
            CAPTURE_QUEUED
    );

    private final ChargeService chargeService;
    private final PaymentInstrumentService paymentInstrumentService;
    private final LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService;

    @Inject
    public AdyenRecurringTokenWebhookService(ChargeService chargeService,
                                             PaymentInstrumentService paymentInstrumentService,
                                             LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService) {
        this.chargeService = chargeService;
        this.paymentInstrumentService = paymentInstrumentService;
        this.linkPaymentInstrumentToAgreementService = linkPaymentInstrumentToAgreementService;
    }

    @Transactional
    public void processTokenWebhook(String payload) {
        TokenizationWebhooksHandler webhookHandler = new TokenizationWebhooksHandler(payload);

        webhookHandler.getTokenizationCreatedDetailsNotificationRequest().ifPresent(event -> {
            String eventId = event.getEventId();
            String shopperReference = event.getData().getShopperReference();
            String storedPaymentMethodId = event.getData().getStoredPaymentMethodId();

            Map<String, String> recurringAuthToken = Map.of(
                    AdyenRecurringAuthTokenKeys.SHOPPER_REFERENCE, shopperReference,
                    AdyenRecurringAuthTokenKeys.STORED_PAYMENT_METHOD_ID, storedPaymentMethodId
            );

            chargeService.findByProviderAndTransactionId(ADYEN.getName(), eventId)
                    .ifPresentOrElse(charge -> handleTokenCreated(charge, recurringAuthToken),
                            () -> LOGGER.warn("Charge not found for Adyen token created webhook",
                                    kv("gatewayTransactionId", eventId),
                                    kv("shopperReference", shopperReference)));
        });

        webhookHandler.getTokenizationDisabledDetailsNotificationRequest().ifPresent(event -> {
            String storedPaymentMethodId = event.getData().getStoredPaymentMethodId();
            LOGGER.info("Received Adyen recurring token disabled webhook",
                    kv("storedPaymentMethodId", storedPaymentMethodId));
        });
    }

    private void handleTokenCreated(ChargeEntity charge, Map<String, String> recurringAuthToken) {
        if (!charge.isSavePaymentInstrumentToAgreement()) {
            LOGGER.info("Ignoring Adyen token created webhook for charge that is not setting up an agreement",
                    kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()));
            return;
        }

        if (charge.getPaymentInstrument().isPresent()) {
            charge.getPaymentInstrument().get().setRecurringAuthToken(recurringAuthToken);
        } else {
            var paymentInstrument = paymentInstrumentService.createPaymentInstrument(charge, recurringAuthToken);
            charge.setPaymentInstrument(paymentInstrument);
        }

        ChargeStatus chargeStatus = ChargeStatus.fromString(charge.getStatus());
        if (STATUSES_ELIGIBLE_FOR_AGREEMENT_LINK.contains(chargeStatus)) {
            linkPaymentInstrumentToAgreementService.linkPaymentInstrumentFromChargeToAgreement(charge);
        }

        LOGGER.info("Stored Adyen recurring token for charge",
                kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()),
                kv("storedPaymentMethodId", recurringAuthToken.get(AdyenRecurringAuthTokenKeys.STORED_PAYMENT_METHOD_ID)));
    }
}
