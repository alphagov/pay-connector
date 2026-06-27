package uk.gov.pay.connector.gateway.adyen.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.LinkPaymentInstrumentToAgreementService;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.adyen.AdyenRecurringAuthTokenKeys.SHOPPER_REFERENCE;
import static uk.gov.pay.connector.gateway.adyen.AdyenRecurringAuthTokenKeys.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TOKEN_CREATED_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TOKEN_DISABLED_NOTIFICATION;

@ExtendWith(MockitoExtension.class)
class AdyenRecurringTokenWebhookServiceTest {

    private static final String EVENT_ID = "QBQQ9DLNRHHKGK38";
    private static final Map<String, String> EXPECTED_RECURRING_AUTH_TOKEN = Map.of(
            SHOPPER_REFERENCE, "agreement-external-id",
            STORED_PAYMENT_METHOD_ID, "M5N7TQ4TG5PFWR50");

    @Mock
    private ChargeService chargeService;

    @Mock
    private PaymentInstrumentService paymentInstrumentService;

    @Mock
    private LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService;

    private AdyenRecurringTokenWebhookService adyenRecurringTokenWebhookService;

    @BeforeEach
    void setUp() {
        adyenRecurringTokenWebhookService = new AdyenRecurringTokenWebhookService(
                chargeService,
                paymentInstrumentService,
                linkPaymentInstrumentToAgreementService);
    }

    @Test
    void shouldCreatePaymentInstrumentAndLinkAgreementWhenTokenCreatedForSetupAgreementCharge() {
        ChargeEntity charge = aValidChargeEntity()
                .withSavePaymentInstrumentToAgreement(true)
                .withStatus(CAPTURE_APPROVED)
                .build();
        PaymentInstrumentEntity paymentInstrument = aPaymentInstrumentEntity().build();
        when(chargeService.findByProviderAndTransactionId(ADYEN.getName(), EVENT_ID)).thenReturn(Optional.of(charge));
        when(paymentInstrumentService.createPaymentInstrument(charge, EXPECTED_RECURRING_AUTH_TOKEN))
                .thenReturn(paymentInstrument);

        adyenRecurringTokenWebhookService.processTokenWebhook(TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION));

        verify(paymentInstrumentService).createPaymentInstrument(charge, EXPECTED_RECURRING_AUTH_TOKEN);
        verify(linkPaymentInstrumentToAgreementService).linkPaymentInstrumentFromChargeToAgreement(charge);
    }

    @Test
    void shouldUpdateExistingPaymentInstrumentWhenTokenCreatedForChargeWithInstrument() {
        PaymentInstrumentEntity paymentInstrument = aPaymentInstrumentEntity().build();
        ChargeEntity charge = aValidChargeEntity()
                .withSavePaymentInstrumentToAgreement(true)
                .withStatus(AWAITING_CAPTURE_REQUEST)
                .withPaymentInstrument(paymentInstrument)
                .build();
        when(chargeService.findByProviderAndTransactionId(ADYEN.getName(), EVENT_ID)).thenReturn(Optional.of(charge));

        adyenRecurringTokenWebhookService.processTokenWebhook(TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION));

        assertThat(paymentInstrument.getRecurringAuthToken().orElseThrow(), is(EXPECTED_RECURRING_AUTH_TOKEN));
        verify(paymentInstrumentService, never()).createPaymentInstrument(any(), any());
        verify(linkPaymentInstrumentToAgreementService).linkPaymentInstrumentFromChargeToAgreement(charge);
    }

    @Test
    void shouldNotLinkAgreementWhenChargeIsNotEligibleForCapture() {
        ChargeEntity charge = aValidChargeEntity()
                .withSavePaymentInstrumentToAgreement(true)
                .withStatus(AUTHORISATION_SUCCESS)
                .build();
        PaymentInstrumentEntity paymentInstrument = aPaymentInstrumentEntity().build();
        when(chargeService.findByProviderAndTransactionId(ADYEN.getName(), EVENT_ID)).thenReturn(Optional.of(charge));
        when(paymentInstrumentService.createPaymentInstrument(charge, EXPECTED_RECURRING_AUTH_TOKEN))
                .thenReturn(paymentInstrument);

        adyenRecurringTokenWebhookService.processTokenWebhook(TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION));

        verify(paymentInstrumentService).createPaymentInstrument(charge, EXPECTED_RECURRING_AUTH_TOKEN);
        verify(linkPaymentInstrumentToAgreementService, never()).linkPaymentInstrumentFromChargeToAgreement(any());
    }

    @Test
    void shouldIgnoreTokenCreatedWhenChargeIsNotSettingUpAgreement() {
        ChargeEntity charge = aValidChargeEntity()
                .withSavePaymentInstrumentToAgreement(false)
                .withStatus(CAPTURE_APPROVED)
                .build();
        when(chargeService.findByProviderAndTransactionId(ADYEN.getName(), EVENT_ID)).thenReturn(Optional.of(charge));

        adyenRecurringTokenWebhookService.processTokenWebhook(TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION));

        verifyNoInteractions(paymentInstrumentService);
        verifyNoInteractions(linkPaymentInstrumentToAgreementService);
    }

    @Test
    void shouldDoNothingWhenChargeNotFoundForTokenCreatedWebhook() {
        when(chargeService.findByProviderAndTransactionId(eq(ADYEN.getName()), eq(EVENT_ID))).thenReturn(Optional.empty());

        adyenRecurringTokenWebhookService.processTokenWebhook(TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION));

        verifyNoInteractions(paymentInstrumentService);
        verifyNoInteractions(linkPaymentInstrumentToAgreementService);
    }

    @Test
    void shouldHandleTokenDisabledWebhookWithoutUpdatingPaymentInstrument() {
        adyenRecurringTokenWebhookService.processTokenWebhook(TestTemplateResourceLoader.load(ADYEN_TOKEN_DISABLED_NOTIFICATION));

        verifyNoInteractions(chargeService);
        verifyNoInteractions(paymentInstrumentService);
        verifyNoInteractions(linkPaymentInstrumentToAgreementService);
    }
}
