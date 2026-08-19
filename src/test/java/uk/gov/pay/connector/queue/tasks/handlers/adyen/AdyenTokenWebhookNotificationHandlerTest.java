package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import io.github.netmikey.logunit.api.LogCapturer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.charge.service.LinkPaymentInstrumentToAgreementService;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.model.agreement.AgreementInactivated;
import uk.gov.pay.connector.events.model.agreement.AgreementInactivated.AgreementInactivatedEventDetails;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.response.AdyenTokenEventData;
import uk.gov.pay.connector.gateway.adyen.response.AdyenTokenNotification;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenRecurringTokenNotificationService;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.slf4j.event.Level.ERROR;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.CREATED;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.INACTIVE;

@ExtendWith(MockitoExtension.class)
class AdyenTokenWebhookNotificationHandlerTest {

    private static final String AGREEMENT_EXTERNAL_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CHARGE_EXTERNAL_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SHOPPER_REFERENCE = AGREEMENT_EXTERNAL_ID + "-" + CHARGE_EXTERNAL_ID;
    private static final String STORED_PAYMENT_METHOD_ID = "pm-id-123";
    private static final String PAYLOAD = "{}";

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenTokenWebhookNotificationHandler.class);

    @Mock
    private AgreementDao agreementDao;
    @Mock
    private PaymentInstrumentDao paymentInstrumentDao;
    @Mock
    private LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService;
    @Mock
    private AdyenRecurringTokenNotificationService adyenRecurringTokenNotificationService;
    @Mock
    private LedgerService ledgerService;

    @Captor
    private ArgumentCaptor<PaymentInstrumentEntity> paymentInstrumentCaptor;

    @InjectMocks
    private AdyenTokenWebhookNotificationHandler handler;

    @Nested
    class TokenCreatedWebhooks {
        @Test
        void shouldSetTokenAndLinkPaymentInstrumentToAgreement() {
            var paymentInstrument = aPaymentInstrumentEntity()
                    .withPaymentInstrumentStatus(PaymentInstrumentStatus.CREATED)
                    .withChargeExternalId(CHARGE_EXTERNAL_ID)
                    .build();
            var agreement = anAgreementEntity().withExternalId(AGREEMENT_EXTERNAL_ID).withGatewayAccount(aGatewayAccountEntity().build()).build();

            given(adyenRecurringTokenNotificationService.deserialisePayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                    .willReturn(tokenNotification(SHOPPER_REFERENCE, STORED_PAYMENT_METHOD_ID, "created"));
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

            given(adyenRecurringTokenNotificationService.deserialisePayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                    .willReturn(tokenNotification(SHOPPER_REFERENCE, STORED_PAYMENT_METHOD_ID, "created"));
            given(agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID)).willReturn(Optional.of(agreement));
            given(paymentInstrumentDao.findByChargeExternalId(CHARGE_EXTERNAL_ID)).willReturn(Optional.of(paymentInstrument));

            handler.process(PAYLOAD);

            then(linkPaymentInstrumentToAgreementService).shouldHaveNoInteractions();
            logs.assertContains("Payment instrument is already in ACTIVE state, ignoring Adyen token webhook");
        }
    }

    @Nested
    class TokenDisabledWebhooks {

        @Captor
        private ArgumentCaptor<AgreementInactivated> agreementInactivatedArgumentCaptor;

        @Test
        void shouldSetPaymentInstrumentToInactive() {
            var paymentInstrument = createAndMockPaymentInstrument(PaymentInstrumentStatus.ACTIVE);

            handler.process(PAYLOAD);

            assertThat(paymentInstrument.getStatus(), is(INACTIVE));
            logs.assertContains("Payment instrument and agreement successfully inactivated");
        }

        @Test
        void shouldEmitAgreementInactivatedEventToLedger() {
            createAndMockPaymentInstrument(PaymentInstrumentStatus.ACTIVE);

            handler.process(PAYLOAD);

            verify(ledgerService).postEvent(agreementInactivatedArgumentCaptor.capture());

            var event = agreementInactivatedArgumentCaptor.getValue();
            assertThat(event.getEventType(), is("AGREEMENT_INACTIVATED"));
            var eventDetails = (AgreementInactivatedEventDetails) event.getEventDetails();
            assertThat(eventDetails.getReason(), equalTo("Adyen agreement inactivated"));
        }

        @ParameterizedTest
        @EnumSource(value = PaymentInstrumentStatus.class, names = {"CANCELLED", "INACTIVE"})
        void shouldIgnoreWhenInvalidPaymentInstrumentStatus(PaymentInstrumentStatus status) {
            var paymentInstrument = createAndMockPaymentInstrument(status);

            handler.process(PAYLOAD);
            assertThat(paymentInstrument.getStatus(), is(status));
            verifyNoInteractions(ledgerService);
            logs.assertContains("Payment instrument is already in " + status + " state, ignoring Adyen token webhook");
        }

        @Test
        void shouldIgnoreAndErrorWhenDisablingPaymentInstrumentWithCreatedStatus() {
            var paymentInstrument = createAndMockPaymentInstrument(PaymentInstrumentStatus.CREATED);

            handler.process(PAYLOAD);
            assertThat(paymentInstrument.getStatus(), is(CREATED));
            verifyNoInteractions(ledgerService);
            logs.forLevel(ERROR).assertContains("Payment instrument is not in the correct state to be inactivated, ignoring Adyen token webhook");
        }

        private @NotNull PaymentInstrumentEntity createAndMockPaymentInstrument(PaymentInstrumentStatus active) {
            var paymentInstrument = aPaymentInstrumentEntity()
                    .withPaymentInstrumentStatus(active)
                    .withChargeExternalId(CHARGE_EXTERNAL_ID)
                    .build();
            var agreement = anAgreementEntity().withExternalId(AGREEMENT_EXTERNAL_ID).withGatewayAccount(aGatewayAccountEntity().build()).build();

            given(adyenRecurringTokenNotificationService.deserialisePayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                    .willReturn(tokenNotification(SHOPPER_REFERENCE, STORED_PAYMENT_METHOD_ID, "disabled"));
            given(agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID)).willReturn(Optional.of(agreement));
            given(paymentInstrumentDao.findByChargeExternalId(CHARGE_EXTERNAL_ID)).willReturn(Optional.of(paymentInstrument));
            return paymentInstrument;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"created", "disabled"})
    void shouldIgnoreAndLogWhenAgreementNotFound(String operationType) {
        given(adyenRecurringTokenNotificationService.deserialisePayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                .willReturn(tokenNotification(SHOPPER_REFERENCE, STORED_PAYMENT_METHOD_ID, operationType));
        given(agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID)).willReturn(Optional.empty());

        handler.process(PAYLOAD);

        then(paymentInstrumentDao).shouldHaveNoInteractions();
        then(linkPaymentInstrumentToAgreementService).shouldHaveNoInteractions();
        logs.assertContains("Agreement not found, ignoring Adyen token webhook");
    }

    @ParameterizedTest
    @ValueSource(strings = {"created", "disabled"})
    void shouldIgnoreAndLogWhenPaymentInstrumentNotFound(String operationType) {
        var agreement = anAgreementEntity().withExternalId(AGREEMENT_EXTERNAL_ID).withGatewayAccount(aGatewayAccountEntity().build()).build();

        given(adyenRecurringTokenNotificationService.deserialisePayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                .willReturn(tokenNotification(SHOPPER_REFERENCE, STORED_PAYMENT_METHOD_ID, operationType));
        given(paymentInstrumentDao.findByChargeExternalId(CHARGE_EXTERNAL_ID)).willReturn(Optional.empty());
        given(agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID)).willReturn(Optional.of(agreement));

        handler.process(PAYLOAD);

        then(linkPaymentInstrumentToAgreementService).shouldHaveNoInteractions();
        verifyNoInteractions(ledgerService);

        logs.assertContains("Payment instrument not found for charge in Adyen token webhook, ignoring");
    }

    @Test
    void shouldIgnoreUnsupportedWebhookType() {
        given(adyenRecurringTokenNotificationService.deserialisePayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
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
        given(adyenRecurringTokenNotificationService.deserialisePayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                .willReturn(tokenNotification(invalidShopperReference, STORED_PAYMENT_METHOD_ID, "created"));

        handler.process(PAYLOAD);

        then(agreementDao).shouldHaveNoInteractions();
        then(linkPaymentInstrumentToAgreementService).shouldHaveNoInteractions();
        logs.assertContains("Invalid shopper reference");

    }

    @ParameterizedTest
    @ValueSource(strings = {"created", "disabled"})
    void shouldNotLogStoredPaymentMethodId(String operationType) {
        var paymentInstrument = aPaymentInstrumentEntity()
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.CREATED)
                .withChargeExternalId(CHARGE_EXTERNAL_ID)
                .build();
        var agreement = anAgreementEntity().withExternalId(AGREEMENT_EXTERNAL_ID).withGatewayAccount(aGatewayAccountEntity().build()).build();

        given(adyenRecurringTokenNotificationService.deserialisePayload(eq(PAYLOAD), eq(AdyenTokenNotification.class)))
                .willReturn(tokenNotification(SHOPPER_REFERENCE, STORED_PAYMENT_METHOD_ID, operationType));
        given(agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID)).willReturn(Optional.of(agreement));
        given(paymentInstrumentDao.findByChargeExternalId(CHARGE_EXTERNAL_ID)).willReturn(Optional.of(paymentInstrument));

        handler.process(PAYLOAD);

        logs.getEvents().forEach(event ->
                assertThat("Log must not contain storedPaymentMethodId value",
                        event.getMessage().contains(STORED_PAYMENT_METHOD_ID), is(false)));
    }

    private AdyenTokenNotification tokenNotification(String shopperReference, String storedPaymentMethodId, String operation) {
        return new AdyenTokenNotification(
                "2026-07-14T18:10:49+01:00",
                "event-id-123",
                "test",
                new AdyenTokenEventData("YOUR_MERCHANT_ACCOUNT", storedPaymentMethodId, "visa", operation.equals("created") ? "created" : null, shopperReference),
                "recurring.token." + operation
        );
    }
}
