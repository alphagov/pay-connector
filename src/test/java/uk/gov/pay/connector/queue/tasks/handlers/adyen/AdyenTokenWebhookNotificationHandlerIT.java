package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.ACTIVE;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.CREATED;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TOKEN_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

class AdyenTokenWebhookNotificationHandlerIT {

    private static final String AGREEMENT_EXTERNAL_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CHARGE_EXTERNAL_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SHOPPER_REFERENCE = AGREEMENT_EXTERNAL_ID + "-" + CHARGE_EXTERNAL_ID;
    private static final String STORED_PAYMENT_METHOD_ID_VALUE = "pm-id-123";
    private static final String CREATED_PAYMENT_INSTRUMENT_EXTERNAL_ID = "cccccccccccccccccccccccccc";
    private static final String ACTIVE_PAYMENT_INSTRUMENT_EXTERNAL_ID = "dddddddddddddddddddddddddd";
    private static final String OTHER_CHARGE_EXTERNAL_ID = "eeeeeeeeeeeeeeeeeeeeeeeeee";

    private static final long AGREEMENT_ID = secureRandomLong();
    private static final long CREATED_PAYMENT_INSTRUMENT_ID = secureRandomLong();
    private static final long ACTIVE_PAYMENT_INSTRUMENT_ID = secureRandomLong();

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenTokenWebhookNotificationHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AdyenTokenWebhookNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = app.getInstanceFromGuiceContainer(AdyenTokenWebhookNotificationHandler.class);
    }

    @Test
    void shouldActivatePaymentInstrumentAndLinkToAgreement() throws Exception {
        insertAgreement();
        insertPaymentInstrument(
                CREATED_PAYMENT_INSTRUMENT_ID,
                CREATED_PAYMENT_INSTRUMENT_EXTERNAL_ID,
                CREATED,
                null,
                CHARGE_EXTERNAL_ID,
                STORED_PAYMENT_METHOD_ID_VALUE
        );
        insertPaymentInstrument(
                ACTIVE_PAYMENT_INSTRUMENT_ID,
                ACTIVE_PAYMENT_INSTRUMENT_EXTERNAL_ID,
                ACTIVE,
                AGREEMENT_EXTERNAL_ID,
                OTHER_CHARGE_EXTERNAL_ID,
                "old-token"
        );

        handler.process(tokenNotificationPayload());

        var updatedAgreement = app.getDatabaseTestHelper().getAgreementByExternalId(AGREEMENT_EXTERNAL_ID);
        var updatedCreatedPaymentInstrument = app.getDatabaseTestHelper().getPaymentInstrument(CREATED_PAYMENT_INSTRUMENT_ID);
        var updatedActivePaymentInstrument = app.getDatabaseTestHelper().getPaymentInstrument(ACTIVE_PAYMENT_INSTRUMENT_ID);

        assertThat(((Number) updatedAgreement.get("payment_instrument_id")).longValue(), is(CREATED_PAYMENT_INSTRUMENT_ID));
        assertThat(updatedCreatedPaymentInstrument.get("status"), is(ACTIVE.name()));
        assertThat(updatedCreatedPaymentInstrument.get("agreement_external_id"), is(AGREEMENT_EXTERNAL_ID));
        assertThat(recurringAuthToken(updatedCreatedPaymentInstrument), is(Map.of(STORED_PAYMENT_METHOD_ID, STORED_PAYMENT_METHOD_ID_VALUE)));
        assertThat(updatedActivePaymentInstrument.get("status"), is(PaymentInstrumentStatus.CANCELLED.name()));
    }


    private void insertAgreement() {
        var testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .insert();

        app.getDatabaseTestHelper().addAgreement(anAddAgreementParams()
                .withAgreementId(AGREEMENT_ID)
                .withExternalAgreementId(AGREEMENT_EXTERNAL_ID)
                .withGatewayAccountId(String.valueOf(testAccount.getAccountId()))
                .build());
    }

    private Map<String, String> recurringAuthToken(Map<String, Object> paymentInstrument) throws Exception {
        return objectMapper.readValue(paymentInstrument.get("recurring_auth_token").toString(),
                new TypeReference<>() {
                });
    }

    private void insertPaymentInstrument(long paymentInstrumentId,
                                         String externalPaymentInstrumentId,
                                         PaymentInstrumentStatus status,
                                         String agreementExternalId,
                                         String chargeExternalId,
                                         String storedPaymentMethodId) {
        app.getDatabaseTestHelper().addPaymentInstrument(anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withExternalPaymentInstrumentId(externalPaymentInstrumentId)
                .withPaymentInstrumentStatus(status)
                .withAgreementExternalId(agreementExternalId)
                .withChargeExternalId(chargeExternalId)
                .withRecurringAuthToken(Map.of(STORED_PAYMENT_METHOD_ID, storedPaymentMethodId))
                .build());
        app.getDatabaseTestHelper().getPaymentInstrument(paymentInstrumentId);
    }

    private String tokenNotificationPayload() {
        return load(ADYEN_TOKEN_NOTIFICATION)
                .replace("YOUR_SHOPPER_REFERENCE", SHOPPER_REFERENCE)
                .replace("M5N7TQ4TG5PFWR50", STORED_PAYMENT_METHOD_ID_VALUE);
    }
}
