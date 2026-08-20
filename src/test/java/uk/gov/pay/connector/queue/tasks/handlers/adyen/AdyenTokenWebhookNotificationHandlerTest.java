package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.charge.service.LinkPaymentInstrumentToAgreementService;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.response.AdyenTokenEventData;
import uk.gov.pay.connector.gateway.adyen.response.AdyenTokenNotification;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;

@ExtendWith(MockitoExtension.class)
class AdyenTokenWebhookNotificationHandlerTest {

    private static final String AGREEMENT_EXTERNAL_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CHARGE_EXTERNAL_ID    = "bbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SHOPPER_REFERENCE = AGREEMENT_EXTERNAL_ID + "-" + CHARGE_EXTERNAL_ID;
    private static final String STORED_PAYMENT_METHOD_ID = "pm-id-123";
    private static final String PAYLOAD = "{}";

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenTokenWebhookNotificationHandler.class);

    @Mock private AgreementDao agreementDao;
    @Mock private PaymentInstrumentDao paymentInstrumentDao;
    @Mock private LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService;
    @Mock private AdyenNotificationService adyenNotificationService;

    @Captor
    private ArgumentCaptor<PaymentInstrumentEntity> paymentInstrumentCaptor;

    @InjectMocks
    private AdyenTokenWebhookNotificationHandler handler;

    @Test
    void shouldSetTokenAndLinkPaymentInstrumentToAgreement() {
        var paymentInstrument = aPaymentInstrumentEntity()
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.CREATED)
                .withChargeExternalId(CHARGE_EXTERNAL_ID)
                .build();
        var agreement = anAgreementEntity().withExternalId(AGREEMENT_EXTERNAL_ID).build();

        given(adyenNotificationService.deserialiseTokenPayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                .willReturn(tokenCreatedNotification(SHOPPER_REFERENCE, STORED_PAYMENT_METHOD_ID));
        given(agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID)).willReturn(Optional.of(agreement));
        given(paymentInstrumentDao.findByChargeExternalId(CHARGE_EXTERNAL_ID)).willReturn(Optional.of(paymentInstrument));

        handler.process(PAYLOAD);

        then(linkPaymentInstrumentToAgreementService)
                .should()
                .linkPaymentInstrumentToAgreement(eq(agreement), paymentInstrumentCaptor.capture());

        var captured = paymentInstrumentCaptor.getValue();
        assertThat(captured.getRecurringAuthToken().orElseThrow().get(AdyenRequestFactory.STORED_PAYMENT_METHOD_ID),
                is(STORED_PAYMENT_METHOD_ID));
    }

    @Test
    void shouldIgnoreAndLogWhenPaymentInstrumentIsAlreadyActive() {
        var paymentInstrument = aPaymentInstrumentEntity()
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE)
                .withChargeExternalId(CHARGE_EXTERNAL_ID)
                .build();
        var agreement = anAgreementEntity().withExternalId(AGREEMENT_EXTERNAL_ID).build();

        given(adyenNotificationService.deserialiseTokenPayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                .willReturn(tokenCreatedNotification(SHOPPER_REFERENCE, STORED_PAYMENT_METHOD_ID));
        given(agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID)).willReturn(Optional.of(agreement));
        given(paymentInstrumentDao.findByChargeExternalId(CHARGE_EXTERNAL_ID)).willReturn(Optional.of(paymentInstrument));

        handler.process(PAYLOAD);

        then(linkPaymentInstrumentToAgreementService).shouldHaveNoInteractions();
        logs.assertContains("Payment instrument is already in ACTIVE state, ignoring Adyen token webhook");
    }

    @Test
    void shouldIgnoreAndLogWhenAgreementNotFound() {
        given(adyenNotificationService.deserialiseTokenPayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                .willReturn(tokenCreatedNotification(SHOPPER_REFERENCE, STORED_PAYMENT_METHOD_ID));
        given(agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID)).willReturn(Optional.empty());

        handler.process(PAYLOAD);

        then(paymentInstrumentDao).shouldHaveNoInteractions();
        then(linkPaymentInstrumentToAgreementService).shouldHaveNoInteractions();
        logs.assertContains("Agreement not found, ignoring Adyen token webhook");
    }

    @Test
    void shouldIgnoreUnsupportedWebhookType() {
        given(adyenNotificationService.deserialiseTokenPayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                .willReturn(new AdyenTokenNotification(null, null, null,
                        new AdyenTokenEventData(null, STORED_PAYMENT_METHOD_ID, null, null, SHOPPER_REFERENCE),
                        "recurring.token.deleted"));

        handler.process(PAYLOAD);

        then(agreementDao).shouldHaveNoInteractions();
        then(linkPaymentInstrumentToAgreementService).shouldHaveNoInteractions();
        logs.assertContains("Ignoring Adyen token webhook notification with unsupported type");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"badId", "one-two-three", "aaaaaaaaaaaaaaaaaaaaaaaaaa-short", "short-bbbbbbbbbbbbbbbbbbbbbbbbbb"})
    void shouldLogErrorForInvalidShopperReferenceLengthFormat(String invalidShopperReference) {
        given(adyenNotificationService.deserialiseTokenPayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                .willReturn(tokenCreatedNotification(invalidShopperReference, STORED_PAYMENT_METHOD_ID));

        handler.process(PAYLOAD);

        then(agreementDao).shouldHaveNoInteractions();
        then(linkPaymentInstrumentToAgreementService).shouldHaveNoInteractions();
        logs.assertContains("Invalid shopper reference");

    }

    @Test
    void shouldNotLogStoredPaymentMethodId() {
        var paymentInstrument = aPaymentInstrumentEntity()
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.CREATED)
                .withChargeExternalId(CHARGE_EXTERNAL_ID)
                .build();
        var agreement = anAgreementEntity().withExternalId(AGREEMENT_EXTERNAL_ID).build();

        given(adyenNotificationService.deserialiseTokenPayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                .willReturn(tokenCreatedNotification(SHOPPER_REFERENCE, STORED_PAYMENT_METHOD_ID));
        given(agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID)).willReturn(Optional.of(agreement));
        given(paymentInstrumentDao.findByChargeExternalId(CHARGE_EXTERNAL_ID)).willReturn(Optional.of(paymentInstrument));

        handler.process(PAYLOAD);

        logs.getEvents().forEach(event ->
                assertThat("Log must not contain storedPaymentMethodId value",
                        event.getMessage().contains(STORED_PAYMENT_METHOD_ID), is(false)));
    }

    private AdyenTokenNotification tokenCreatedNotification(String shopperReference, String storedPaymentMethodId) {
        return new AdyenTokenNotification(
                "2026-07-14T18:10:49+01:00",
                "event-id-123",
                "test",
                new AdyenTokenEventData("YOUR_MERCHANT_ACCOUNT", storedPaymentMethodId, "visa", "created", shopperReference),
                "recurring.token.created"
        );
    }
}
