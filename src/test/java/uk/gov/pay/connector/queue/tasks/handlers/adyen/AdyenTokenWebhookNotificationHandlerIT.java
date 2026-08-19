package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.util.AddChargeParams;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.ACTIVE;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.CANCELLED;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus.CREATED;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TOKEN_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

class AdyenTokenWebhookNotificationHandlerIT {

    private static final String AGREEMENT_EXTERNAL_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String WEBHOOK_CHARGE_EXTERNAL_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SHOPPER_REFERENCE = AGREEMENT_EXTERNAL_ID + "-" + WEBHOOK_CHARGE_EXTERNAL_ID;
    private static final String STORED_PAYMENT_METHOD_ID_VALUE = "pm-id-123";
    private static final String FIRST_PAYMENT_INSTRUMENT_EXTERNAL_ID = "cccccccccccccccccccccccccc";
    private static final String SECOND_PAYMENT_INSTRUMENT_EXTERNAL_ID = "dddddddddddddddddddddddddd";
    private static final String OTHER_CHARGE_EXTERNAL_ID = "eeeeeeeeeeeeeeeeeeeeeeeeee";

    private static final long AGREEMENT_ID = secureRandomLong();
    private static final long FIRST_PAYMENT_INSTRUMENT_ID = secureRandomLong();
    private static final long SECOND_PAYMENT_INSTRUMENT_ID = secureRandomLong();

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenTokenWebhookNotificationHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AdyenTokenWebhookNotificationHandler handler;

    private DatabaseFixtures.TestAccount defaultTestAccount;

    @BeforeEach
    void setUp() {
        handler = app.getInstanceFromGuiceContainer(AdyenTokenWebhookNotificationHandler.class);

        app.getDatabaseFixtures().validTestCardDetails();
        insertTestAccount();

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of())
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(defaultTestAccount.getPaymentProvider())
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();
        gatewayAccountCredentialsEntity.setId(defaultTestAccount.getCredentials().getFirst().getId());
    }

    @Test
    void shouldActivatePaymentInstrumentAndLinkToAgreement() throws Exception {
        insertAgreement();
        insertPaymentInstrument(
                FIRST_PAYMENT_INSTRUMENT_ID,
                FIRST_PAYMENT_INSTRUMENT_EXTERNAL_ID,
                ACTIVE,
                AGREEMENT_EXTERNAL_ID,
                OTHER_CHARGE_EXTERNAL_ID,
                "old-token",
                Instant.now()
        );
        insertPaymentInstrument(
                SECOND_PAYMENT_INSTRUMENT_ID,
                SECOND_PAYMENT_INSTRUMENT_EXTERNAL_ID,
                CREATED,
                null,
                WEBHOOK_CHARGE_EXTERNAL_ID,
                STORED_PAYMENT_METHOD_ID_VALUE,
                Instant.now().plusSeconds(120)
        );


        insertChargeForAgreement(OTHER_CHARGE_EXTERNAL_ID, FIRST_PAYMENT_INSTRUMENT_ID);
        insertChargeForAgreement(WEBHOOK_CHARGE_EXTERNAL_ID, SECOND_PAYMENT_INSTRUMENT_ID);

        handler.process(tokenNotificationPayload());

        var updatedAgreement = app.getDatabaseTestHelper().getAgreementByExternalId(AGREEMENT_EXTERNAL_ID);
        var updatedFirstPaymentInstrument = app.getDatabaseTestHelper().getPaymentInstrument(FIRST_PAYMENT_INSTRUMENT_ID);
        var updatedSecondPaymentInstrument = app.getDatabaseTestHelper().getPaymentInstrument(SECOND_PAYMENT_INSTRUMENT_ID);

        assertThat(((Number) updatedAgreement.get("payment_instrument_id")).longValue(), is(SECOND_PAYMENT_INSTRUMENT_ID));
        assertThat(updatedSecondPaymentInstrument.get("status"), is(ACTIVE.name()));
        assertThat(updatedSecondPaymentInstrument.get("agreement_external_id"), is(AGREEMENT_EXTERNAL_ID));
        assertThat(recurringAuthToken(updatedSecondPaymentInstrument), is(Map.of(STORED_PAYMENT_METHOD_ID, STORED_PAYMENT_METHOD_ID_VALUE)));
        assertThat(updatedFirstPaymentInstrument.get("status"), is(PaymentInstrumentStatus.CANCELLED.name()));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentInstrumentStatus.class, names = {"CREATED", "ACTIVE"})
    void shouldCancelWebhookPaymentInstrumentWhenNewerActiveOneExists(PaymentInstrumentStatus paymentInstrumentTwoStatus) {
        insertAgreement();
        //First Payment Instrument
        insertPaymentInstrument(
                FIRST_PAYMENT_INSTRUMENT_ID,
                FIRST_PAYMENT_INSTRUMENT_EXTERNAL_ID,
                CREATED,
                null,
                WEBHOOK_CHARGE_EXTERNAL_ID,
                "",
                Instant.parse("2026-08-17T10:43:52Z")
        );
        // Second Payment Instrument
        insertPaymentInstrument(
                SECOND_PAYMENT_INSTRUMENT_ID,
                SECOND_PAYMENT_INSTRUMENT_EXTERNAL_ID,
                paymentInstrumentTwoStatus,
                AGREEMENT_EXTERNAL_ID,
                OTHER_CHARGE_EXTERNAL_ID,
                STORED_PAYMENT_METHOD_ID_VALUE,
                Instant.parse("2026-08-18T10:43:52Z")
        );

        insertChargeForAgreement(WEBHOOK_CHARGE_EXTERNAL_ID, FIRST_PAYMENT_INSTRUMENT_ID);
        insertChargeForAgreement(OTHER_CHARGE_EXTERNAL_ID, SECOND_PAYMENT_INSTRUMENT_ID);

        handler.process(tokenNotificationPayload());

        var firstPaymentInstrument = app.getDatabaseTestHelper().getPaymentInstrument(FIRST_PAYMENT_INSTRUMENT_ID);
        var secondPaymentInstrument = app.getDatabaseTestHelper().getPaymentInstrument(SECOND_PAYMENT_INSTRUMENT_ID);

        assertThat(firstPaymentInstrument.get("status"), is(CANCELLED.name()));
        assertThat(firstPaymentInstrument.get("recurring_auth_token").toString(), containsString(STORED_PAYMENT_METHOD_ID_VALUE));
        assertThat(secondPaymentInstrument.get("status"), is(paymentInstrumentTwoStatus.name()));
        assertThat(secondPaymentInstrument.get("recurring_auth_token").toString(), containsString(STORED_PAYMENT_METHOD_ID_VALUE));
    }


    private void insertChargeForAgreement(String chargeExternalId2, Long paymentInstrumentIdLatest) {
        app.getDatabaseTestHelper().addCharge(
                AddChargeParams.AddChargeParamsBuilder
                        .anAddChargeParams()
                        .withExternalChargeId(chargeExternalId2)
                        .withGatewayAccountId(String.valueOf(defaultTestAccount.getAccountId()))
                        .withAgreementExternalId(AGREEMENT_EXTERNAL_ID)
                        .withPaymentInstrumentId(paymentInstrumentIdLatest)
                        .build()
        );
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
                                         String storedPaymentMethodId,
                                         Instant createdDate) {
        app.getDatabaseTestHelper().addPaymentInstrument(anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withExternalPaymentInstrumentId(externalPaymentInstrumentId)
                .withPaymentInstrumentStatus(status)
                .withAgreementExternalId(agreementExternalId)
                .withChargeExternalId(chargeExternalId)
                .withRecurringAuthToken(Map.of(STORED_PAYMENT_METHOD_ID, storedPaymentMethodId))
                .withCreatedDate(createdDate)
                .build());
        app.getDatabaseTestHelper().getPaymentInstrument(paymentInstrumentId);
    }

    private String tokenNotificationPayload() {
        return load(ADYEN_TOKEN_NOTIFICATION)
                .replace("YOUR_SHOPPER_REFERENCE", SHOPPER_REFERENCE)
                .replace("M5N7TQ4TG5PFWR50", STORED_PAYMENT_METHOD_ID_VALUE);
    }

    private void insertTestAccount() {
        this.defaultTestAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withAccountId(secureRandomLong())
                .insert();
    }
}
