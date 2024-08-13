package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.RandomUtils;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_STRIPE_ACCOUNT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_MERCHANT_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.resource.GatewayAccountCredentialsRequestValidator.FIELD_GATEWAY_MERCHANT_ID;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.ACCOUNTS_API_SERVICE_ID_URL;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.ACCOUNTS_API_URL;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.ACCOUNTS_FRONTEND_URL;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.CreateGatewayAccountPayloadBuilder.aCreateGatewayAccountPayloadBuilder;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    public static GatewayAccountResourceITHelpers testHelpers = new GatewayAccountResourceITHelpers(app.getLocalPort());
    private DatabaseFixtures.TestAccount defaultTestAccount;

    @Nested
    class GetByServiceIdAndAccountType {
        
        @Test
        void shouldReturnConflictWhenTryingToAddMultipleLiveGatewayAccounts() {
            String serviceId = randomUuid();

            Map<String, String> gatewayAccountRequest = new HashMap<>();
            gatewayAccountRequest.put("payment_provider", "stripe");
            gatewayAccountRequest.put("service_id", serviceId);
            gatewayAccountRequest.put("service_name", "Service Name");
            gatewayAccountRequest.put("type", "live");

            app.givenSetup().body(toJson(gatewayAccountRequest)).post("/v1/api/accounts/");

            gatewayAccountRequest.put("payment_provider", "worldpay");

            app.givenSetup().body(toJson(gatewayAccountRequest))
                    .post("/v1/api/accounts/")
                    .then()
                    .statusCode(CONFLICT.getStatusCode());
        }
        
        @Test
        void shouldReturnConflictWhenThereAreMultipleLiveGatewayAccounts() {
            String serviceId = randomUuid();

            app.givenSetup().body(toJson(
                            Map.of("payment_provider", "stripe",
                                    "service_id", serviceId,
                                    "service_name", "Service Name",
                                    "type", "live")))
                    .post("/v1/api/accounts/");
            
            //add another live gateway account directly into the database as it's not possible to add another via API 
            app.getDatabaseTestHelper().addGatewayAccount(
                    anAddGatewayAccountParams()
                            .withPaymentGateway("worldpay")
                            .withServiceId(serviceId)
                            .withAccountId(String.valueOf(RandomUtils.nextInt()))
                            .withServiceName("Service Name")
                            .withType(LIVE)
                            .build());
            
            app.givenSetup().get(format("/v1/api/service/%s/account/live", serviceId))
                    .then().statusCode(CONFLICT.getStatusCode());
        }
        
        @Test
        void shouldReturnStripeGatewayAccountWhenThereAreMultipleGatewayAccounts() {
            String serviceId = randomUuid();

            addGatewayAccountForServiceId(serviceId, "sandbox");

            app.givenSetup().get(format("/v1/api/service/%s/account/test", serviceId))
                    .then().statusCode(OK.getStatusCode())
                    .body("gateway_account_credentials", hasSize(1))
                    .body("gateway_account_credentials[0].payment_provider", is("sandbox"))
                    .body("service_name", is("Service Name"))
                    .body("service_id", is(serviceId));

            addGatewayAccountForServiceId(serviceId, "stripe");

            app.givenSetup().get(format("/v1/api/service/%s/account/test", serviceId))
                    .then().statusCode(OK.getStatusCode())
                    .body("gateway_account_credentials", hasSize(1))
                    .body("gateway_account_credentials[0].payment_provider", is("stripe"))
                    .body("service_name", is("Service Name"))
                    .body("service_id", is(serviceId));
        }

        private static void addGatewayAccountForServiceId(String serviceId, String paymentProvider) {
            Map<String, String> gatewayAccountRequest = Map.of(
                    "payment_provider", paymentProvider,
                    "service_id", serviceId,
                    "service_name", "Service Name",
                    "type", "test");

            app.givenSetup().body(toJson(gatewayAccountRequest)).post("/v1/api/accounts/");
        }

        @Test
        void shouldReturn404IfServiceIdIsUnknown() {
            String unknownServiceId = "unknown-service-id";
            String url = ACCOUNTS_API_SERVICE_ID_URL.replace("{serviceId}", unknownServiceId).replace("{accountType}", GatewayAccountType.TEST.name());
            app.givenSetup()
                    .get(url)
                    .then()
                    .statusCode(404);
        }

        @Test
        void shouldReturn404IfNoLiveAccountExists() {
            defaultTestAccount = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .insert();
            String url = ACCOUNTS_API_SERVICE_ID_URL.replace("{serviceId}", "valid-external-service-id").replace("{accountType}", GatewayAccountType.LIVE.name());
            app.givenSetup()
                    .get(url)
                    .then()
                    .statusCode(404);
        }

        @Test
        void shouldReturnWorldpayAccountInformation() {
            long accountId = RandomUtils.nextInt();
            AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider(WORLDPAY.getName())
                    .withGatewayAccountId(accountId)
                    .withState(ACTIVE)
                    .withCredentials(Map.of(
                            CREDENTIALS_MERCHANT_ID, "legacy-merchant-code",
                            CREDENTIALS_USERNAME, "legacy-username",
                            CREDENTIALS_PASSWORD, "legacy-password",
                            FIELD_GATEWAY_MERCHANT_ID, "google-pay-merchant-id",
                            ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                    CREDENTIALS_MERCHANT_CODE, "one-off-merchant-code",
                                    CREDENTIALS_USERNAME, "one-off-username",
                                    CREDENTIALS_PASSWORD, "one-off-password"),
                            RECURRING_CUSTOMER_INITIATED, Map.of(
                                    CREDENTIALS_MERCHANT_CODE, "cit-merchant-code",
                                    CREDENTIALS_USERNAME, "cit-username",
                                    CREDENTIALS_PASSWORD, "cit-password"),
                            RECURRING_MERCHANT_INITIATED, Map.of(
                                    CREDENTIALS_MERCHANT_CODE, "mit-merchant-code",
                                    CREDENTIALS_USERNAME, "mit-username",
                                    CREDENTIALS_PASSWORD, "mit-password")))
                    .build();

            defaultTestAccount = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withAccountId(accountId)
                    .withAllowTelephonePaymentNotifications(true)
                    .withAllowMoto(true)
                    .withCorporateCreditCardSurchargeAmount(250)
                    .withCorporateDebitCardSurchargeAmount(50)
                    .withAllowAuthApi(true)
                    .withRecurringEnabled(true)
                    .withPaymentProvider(WORLDPAY.getName())
                    .withDefaultCredentials()
                    .withGatewayAccountCredentials(List.of(credentialsParams))
                    .withRequires3ds(true)
                    .insert();

            app.getDatabaseTestHelper().allowApplePay(accountId);
            app.getDatabaseTestHelper().allowZeroAmount(accountId);
            app.getDatabaseTestHelper().blockPrepaidCards(accountId);
            app.getDatabaseTestHelper().enableProviderSwitch(accountId);
            app.getDatabaseTestHelper().setDisabled(accountId);
            app.getDatabaseTestHelper().setDisabledReason(accountId, "Disabled because reasons");
            app.getDatabaseTestHelper().insertWorldpay3dsFlexCredential(accountId, "macKey", "issuer", "org_unit_id", 2L);

            String url = ACCOUNTS_API_SERVICE_ID_URL.replace("{serviceId}", "valid-external-service-id").replace("{accountType}", GatewayAccountType.TEST.name());
            app.givenSetup()
                    .get(url)
                    .then()
                    .statusCode(200)
                    .body("payment_provider", is("worldpay"))
                    .body("gateway_account_id", is(Math.toIntExact(defaultTestAccount.getAccountId())))
                    .body("external_id", is(defaultTestAccount.getExternalId()))
                    .body("type", is(TEST.toString()))
                    .body("description", is("a description"))
                    .body("analytics_id", is("an analytics id"))
                    .body("email_collection_mode", is("OPTIONAL"))
                    .body("email_notifications.PAYMENT_CONFIRMED.template_body", is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."))
                    .body("email_notifications.PAYMENT_CONFIRMED.enabled", is(true))
                    .body("email_notifications.REFUND_ISSUED.template_body", is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."))
                    .body("email_notifications.REFUND_ISSUED.enabled", is(true))
                    .body("service_name", is("service_name"))
                    .body("corporate_credit_card_surcharge_amount", is(250))
                    .body("corporate_debit_card_surcharge_amount", is(50))
                    .body("allow_google_pay", is(false))
                    .body("allow_apple_pay", is(true))
                    .body("send_payer_ip_address_to_gateway", is(false))
                    .body("send_payer_email_to_gateway", is(false))
                    .body("allow_zero_amount", is(true))
                    .body("integration_version_3ds", is(2))
                    .body("allow_telephone_payment_notifications", is(true))
                    .body("provider_switch_enabled", is(true))
                    .body("service_id", is("valid-external-service-id"))
                    .body("send_reference_to_gateway", is(false))
                    .body("allow_authorisation_api", is(true))
                    .body("recurring_enabled", is(true))
                    .body("requires3ds", is(true))
                    .body("block_prepaid_cards", is(true))
                    .body("disabled", is(true))
                    .body("disabled_reason", is("Disabled because reasons"))
                    .body("gateway_account_credentials.size()", is(1))
                    .body("gateway_account_credentials[0].payment_provider", is("worldpay"))
                    .body("gateway_account_credentials[0].state", is("ACTIVE"))
                    .body("gateway_account_credentials[0].gateway_account_id", is(Math.toIntExact(defaultTestAccount.getAccountId())))
                    .body("gateway_account_credentials[0].credentials", hasEntry("gateway_merchant_id", "google-pay-merchant-id"))
                    .body("gateway_account_credentials[0].credentials", hasKey("one_off_customer_initiated"))
                    .body("gateway_account_credentials[0].credentials.one_off_customer_initiated", hasEntry("merchant_code", "one-off-merchant-code"))
                    .body("gateway_account_credentials[0].credentials.one_off_customer_initiated", hasEntry("username", "one-off-username"))
                    .body("gateway_account_credentials[0].credentials.one_off_customer_initiated", not(hasKey("password")))
                    .body("gateway_account_credentials[0].credentials", hasKey("recurring_customer_initiated"))
                    .body("gateway_account_credentials[0].credentials.recurring_customer_initiated", hasEntry("merchant_code", "cit-merchant-code"))
                    .body("gateway_account_credentials[0].credentials.recurring_customer_initiated", hasEntry("username", "cit-username"))
                    .body("gateway_account_credentials[0].credentials.recurring_customer_initiated", not(hasKey("password")))
                    .body("gateway_account_credentials[0].credentials", hasKey("recurring_merchant_initiated"))
                    .body("gateway_account_credentials[0].credentials.recurring_merchant_initiated", hasEntry("merchant_code", "mit-merchant-code"))
                    .body("gateway_account_credentials[0].credentials.recurring_merchant_initiated", hasEntry("username", "mit-username"))
                    .body("gateway_account_credentials[0].credentials.recurring_merchant_initiated", not(hasKey("password")))
                    .body("gateway_account_credentials[0]", not(Matchers.hasKey("gateway_account_credential_id")))
                    .body("$", hasKey("worldpay_3ds_flex"))
                    .body("worldpay_3ds_flex.issuer", is("issuer"))
                    .body("worldpay_3ds_flex.organisational_unit_id", is("org_unit_id"))
                    .body("worldpay_3ds_flex", not(hasKey("jwt_mac_key")))
                    .body("worldpay_3ds_flex", not(hasKey("version")))
                    .body("worldpay_3ds_flex", not(hasKey("gateway_account_id")))
                    .body("worldpay_3ds_flex.exemption_engine_enabled", is(false));
        }

        @Test
        void shouldReturnStripeAccountInformation() {
            long accountId = RandomUtils.nextInt();
            AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider(STRIPE.getName())
                    .withGatewayAccountId(accountId)
                    .withState(ACTIVE)
                    .withCredentials(Map.of(CREDENTIALS_STRIPE_ACCOUNT_ID, "a-stripe-account-id"))
                    .build();

            defaultTestAccount = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withAccountId(accountId)
                    .withPaymentProvider(STRIPE.getName())
                    .withAllowTelephonePaymentNotifications(true)
                    .withAllowMoto(true)
                    .withCorporateCreditCardSurchargeAmount(250)
                    .withCorporateDebitCardSurchargeAmount(50)
                    .withAllowAuthApi(true)
                    .withRecurringEnabled(true)
                    .withGatewayAccountCredentials(List.of(credentialsParams))
                    .withRequires3ds(true)
                    .insert();

            app.getDatabaseTestHelper().allowApplePay(accountId);
            app.getDatabaseTestHelper().allowZeroAmount(accountId);
            app.getDatabaseTestHelper().blockPrepaidCards(accountId);
            app.getDatabaseTestHelper().enableProviderSwitch(accountId);
            app.getDatabaseTestHelper().setDisabled(accountId);
            app.getDatabaseTestHelper().setDisabledReason(accountId, "Disabled because reasons");

            String url = ACCOUNTS_API_SERVICE_ID_URL.replace("{serviceId}", "valid-external-service-id").replace("{accountType}", GatewayAccountType.TEST.name());
            app.givenSetup()
                    .get(url)
                    .then()
                    .statusCode(200)
                    .body("payment_provider", is("stripe"))
                    .body("gateway_account_id", is(Math.toIntExact(defaultTestAccount.getAccountId())))
                    .body("external_id", is(defaultTestAccount.getExternalId()))
                    .body("type", is(TEST.toString()))
                    .body("description", is("a description"))
                    .body("analytics_id", is("an analytics id"))
                    .body("email_collection_mode", is("OPTIONAL"))
                    .body("email_notifications.PAYMENT_CONFIRMED.template_body", is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."))
                    .body("email_notifications.PAYMENT_CONFIRMED.enabled", is(true))
                    .body("email_notifications.REFUND_ISSUED.template_body", is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."))
                    .body("email_notifications.REFUND_ISSUED.enabled", is(true))
                    .body("service_name", is("service_name"))
                    .body("corporate_credit_card_surcharge_amount", is(250))
                    .body("corporate_debit_card_surcharge_amount", is(50))
                    .body("allow_google_pay", is(false))
                    .body("allow_apple_pay", is(true))
                    .body("send_payer_ip_address_to_gateway", is(false))
                    .body("send_payer_email_to_gateway", is(false))
                    .body("allow_zero_amount", is(true))
                    .body("integration_version_3ds", is(2))
                    .body("allow_telephone_payment_notifications", is(true))
                    .body("provider_switch_enabled", is(true))
                    .body("service_id", is("valid-external-service-id"))
                    .body("send_reference_to_gateway", is(false))
                    .body("allow_authorisation_api", is(true))
                    .body("recurring_enabled", is(true))
                    .body("requires3ds", is(true))
                    .body("block_prepaid_cards", is(true))
                    .body("disabled", is(true))
                    .body("disabled_reason", is("Disabled because reasons"))
                    .body("gateway_account_credentials.size()", is(1))
                    .body("gateway_account_credentials[0].payment_provider", is("stripe"))
                    .body("gateway_account_credentials[0].state", is("ACTIVE"))
                    .body("gateway_account_credentials[0].gateway_account_id", is(Math.toIntExact(defaultTestAccount.getAccountId())))
                    .body("gateway_account_credentials[0].credentials", hasEntry("stripe_account_id", "a-stripe-account-id"))
                    .body("gateway_account_credentials[0]", not(Matchers.hasKey("gateway_account_credential_id")));
        }

        @Test
        void shouldNotReturn3dsFlexCredentials_whenGatewayIsNotAWorldpayAccount() {
            defaultTestAccount = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withPaymentProvider(STRIPE.getName())
                    .insert();
            String url = ACCOUNTS_API_SERVICE_ID_URL.replace("{serviceId}", "valid-external-service-id").replace("{accountType}", GatewayAccountType.TEST.name());
            app.givenSetup()
                    .get(url)
                    .then()
                    .statusCode(200)
                    .body("worldpay_3ds_flex", nullValue());
        }
    }

    @Nested
    class GetByGatewayAccountId {

        @Test
        void shouldReturn404IfAccountIdIsUnknown() {
            String unknownAccountId = "92348739";
            app.givenSetup()
                    .get(ACCOUNTS_API_URL + unknownAccountId)
                    .then()
                    .statusCode(404);
        }

        @Test
        void shouldReturnDescriptionAndAnalyticsId() {
            String gatewayAccountId = testHelpers.createGatewayAccount(
                    aCreateGatewayAccountPayloadBuilder()
                            .withDescription("desc")
                            .withAnalyticsId("id")
                                    .build());
            app.givenSetup()
                    .get(ACCOUNTS_API_URL + gatewayAccountId)
                    .then()
                    .statusCode(200)
                    .body("analytics_id", is("id"))
                    .body("description", is("desc"));
        }

        @Test
        void shouldReturnAnalyticsId() {
            String gatewayAccountId = testHelpers.createGatewayAccount(
                    aCreateGatewayAccountPayloadBuilder()
                            .withAnalyticsId("id")
                            .build());
            app.givenSetup()
                    .get(ACCOUNTS_API_URL + gatewayAccountId)
                    .then()
                    .statusCode(200)
                    .body("analytics_id", is("id"))
                    .body("description", is(nullValue()));
        }

        @Test
        void shouldReturnDescription() {
            String gatewayAccountId = testHelpers.createGatewayAccount(
                    aCreateGatewayAccountPayloadBuilder()
                            .withDescription("desc")
                            .build());
            
            app.givenSetup()
                    .get(ACCOUNTS_API_URL + gatewayAccountId)
                    .then()
                    .statusCode(200)
                    .body("analytics_id", is(nullValue()))
                    .body("description", is("desc"));
        }


        @Test
        void shouldReturn3dsSetting() {
            String gatewayAccountId = testHelpers.createGatewayAccount(
                    aCreateGatewayAccountPayloadBuilder()
                            .withProvider("stripe")
                            .withRequires3ds(true)
                            .build());

            app.givenSetup()
                    .get(ACCOUNTS_API_URL + gatewayAccountId)
                    .then()
                    .statusCode(200)
                    .body("requires3ds", is(true));
        }

        @Test
        void shouldReturnCorporateCreditCardSurchargeAmountAndCorporateDebitCardSurchargeAmount() {
            int corporateCreditCardSurchargeAmount = 250;
            int corporateDebitCardSurchargeAmount = 50;
            defaultTestAccount = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withCorporateCreditCardSurchargeAmount(corporateCreditCardSurchargeAmount)
                    .withCorporateDebitCardSurchargeAmount(corporateDebitCardSurchargeAmount)
                    .insert();

            app.givenSetup()
                    .get(ACCOUNTS_API_URL + defaultTestAccount.getAccountId())
                    .then()
                    .statusCode(200)
                    .body("corporate_credit_card_surcharge_amount", is(corporateCreditCardSurchargeAmount))
                    .body("corporate_debit_card_surcharge_amount", is(corporateDebitCardSurchargeAmount));
        }

        @Test
        void shouldReturnCorporatePrepaidDebitCardSurchargeAmount() {
            int corporatePrepaidDebitCardSurchargeAmount = 50;
            defaultTestAccount = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withCorporatePrepaidDebitCardSurchargeAmount(corporatePrepaidDebitCardSurchargeAmount)
                    .insert();

            app.givenSetup()
                    .get(ACCOUNTS_API_URL + defaultTestAccount.getAccountId())
                    .then()
                    .statusCode(200)
                    .body("corporate_prepaid_debit_card_surcharge_amount", is(corporatePrepaidDebitCardSurchargeAmount));
        }

        @Test
        void shouldReturnAccountInformationForGetAccountById_withWorldpayCredentials() {
            long accountId = RandomUtils.nextInt();
            AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider(WORLDPAY.getName())
                    .withGatewayAccountId(accountId)
                    .withState(ACTIVE)
                    .withCredentials(Map.of(
                            CREDENTIALS_MERCHANT_ID, "legacy-merchant-code",
                            CREDENTIALS_USERNAME, "legacy-username",
                            CREDENTIALS_PASSWORD, "legacy-password",
                            FIELD_GATEWAY_MERCHANT_ID, "google-pay-merchant-id",
                            ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                    CREDENTIALS_MERCHANT_CODE, "one-off-merchant-code",
                                    CREDENTIALS_USERNAME, "one-off-username",
                                    CREDENTIALS_PASSWORD, "one-off-password"),
                            RECURRING_CUSTOMER_INITIATED, Map.of(
                                    CREDENTIALS_MERCHANT_CODE, "cit-merchant-code",
                                    CREDENTIALS_USERNAME, "cit-username",
                                    CREDENTIALS_PASSWORD, "cit-password"),
                            RECURRING_MERCHANT_INITIATED, Map.of(
                                    CREDENTIALS_MERCHANT_CODE, "mit-merchant-code",
                                    CREDENTIALS_USERNAME, "mit-username",
                                    CREDENTIALS_PASSWORD, "mit-password")))
                    .build();

            defaultTestAccount = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withAccountId(accountId)
                    .withAllowTelephonePaymentNotifications(true)
                    .withAllowMoto(true)
                    .withCorporateCreditCardSurchargeAmount(250)
                    .withCorporateDebitCardSurchargeAmount(50)
                    .withAllowAuthApi(true)
                    .withRecurringEnabled(true)
                    .withPaymentProvider(WORLDPAY.getName())
                    .withDefaultCredentials()
                    .withGatewayAccountCredentials(List.of(credentialsParams))
                    .insert();

            app.getDatabaseTestHelper().allowApplePay(accountId);
            app.getDatabaseTestHelper().allowZeroAmount(accountId);
            app.getDatabaseTestHelper().blockPrepaidCards(accountId);
            app.getDatabaseTestHelper().enableProviderSwitch(accountId);
            app.getDatabaseTestHelper().setDisabled(accountId);
            app.getDatabaseTestHelper().setDisabledReason(accountId, "Disabled because reasons");

            int accountIdAsInt = Math.toIntExact(accountId);
            app.givenSetup()
                    .get(ACCOUNTS_API_URL + accountId)
                    .then()
                    .statusCode(200)
                    .body("payment_provider", is("worldpay"))
                    .body("gateway_account_id", is(Math.toIntExact(defaultTestAccount.getAccountId())))
                    .body("external_id", is(defaultTestAccount.getExternalId()))
                    .body("type", is(TEST.toString()))
                    .body("description", is("a description"))
                    .body("analytics_id", is("an analytics id"))
                    .body("email_collection_mode", is("OPTIONAL"))
                    .body("email_notifications.PAYMENT_CONFIRMED.template_body", is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."))
                    .body("email_notifications.PAYMENT_CONFIRMED.enabled", is(true))
                    .body("email_notifications.REFUND_ISSUED.template_body", is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."))
                    .body("email_notifications.REFUND_ISSUED.enabled", is(true))
                    .body("service_name", is("service_name"))
                    .body("corporate_credit_card_surcharge_amount", is(250))
                    .body("corporate_debit_card_surcharge_amount", is(50))
                    .body("allow_google_pay", is(false))
                    .body("allow_apple_pay", is(true))
                    .body("send_payer_ip_address_to_gateway", is(false))
                    .body("send_payer_email_to_gateway", is(false))
                    .body("allow_zero_amount", is(true))
                    .body("integration_version_3ds", is(2))
                    .body("allow_telephone_payment_notifications", is(true))
                    .body("provider_switch_enabled", is(true))
                    .body("service_id", is("valid-external-service-id"))
                    .body("send_reference_to_gateway", is(false))
                    .body("allow_authorisation_api", is(true))
                    .body("recurring_enabled", is(true))
                    .body("block_prepaid_cards", is(true))
                    .body("disabled", is(true))
                    .body("disabled_reason", is("Disabled because reasons"))
                    .body("gateway_account_credentials.size()", is(1))
                    .body("gateway_account_credentials[0].payment_provider", is("worldpay"))
                    .body("gateway_account_credentials[0].state", is("ACTIVE"))
                    .body("gateway_account_credentials[0].gateway_account_id", is(accountIdAsInt))
                    .body("gateway_account_credentials[0].credentials", hasEntry("gateway_merchant_id", "google-pay-merchant-id"))
                    .body("gateway_account_credentials[0].credentials", hasKey("one_off_customer_initiated"))
                    .body("gateway_account_credentials[0].credentials.one_off_customer_initiated", hasEntry("merchant_code", "one-off-merchant-code"))
                    .body("gateway_account_credentials[0].credentials.one_off_customer_initiated", hasEntry("username", "one-off-username"))
                    .body("gateway_account_credentials[0].credentials.one_off_customer_initiated", not(hasKey("password")))
                    .body("gateway_account_credentials[0].credentials", hasKey("recurring_customer_initiated"))
                    .body("gateway_account_credentials[0].credentials.recurring_customer_initiated", hasEntry("merchant_code", "cit-merchant-code"))
                    .body("gateway_account_credentials[0].credentials.recurring_customer_initiated", hasEntry("username", "cit-username"))
                    .body("gateway_account_credentials[0].credentials.recurring_customer_initiated", not(hasKey("password")))
                    .body("gateway_account_credentials[0].credentials", hasKey("recurring_merchant_initiated"))
                    .body("gateway_account_credentials[0].credentials.recurring_merchant_initiated", hasEntry("merchant_code", "mit-merchant-code"))
                    .body("gateway_account_credentials[0].credentials.recurring_merchant_initiated", hasEntry("username", "mit-username"))
                    .body("gateway_account_credentials[0].credentials.recurring_merchant_initiated", not(hasKey("password")))
                    .body("gateway_account_credentials[0]", hasKey("gateway_account_credential_id"));
        }

        @Test
        void shouldReturnAccountInformationForGetAccountById_withStripeCredentials() {
            long accountId = RandomUtils.nextInt();
            AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider(STRIPE.getName())
                    .withGatewayAccountId(accountId)
                    .withState(ACTIVE)
                    .withCredentials(Map.of(CREDENTIALS_STRIPE_ACCOUNT_ID, "a-stripe-account-id"))
                    .build();

            defaultTestAccount = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withAccountId(accountId)
                    .withPaymentProvider(STRIPE.getName())
                    .withGatewayAccountCredentials(List.of(credentialsParams))
                    .insert();

            int accountIdAsInt = Math.toIntExact(accountId);
            app.givenSetup()
                    .get(ACCOUNTS_API_URL + accountId)
                    .then()
                    .statusCode(200)
                    .body("payment_provider", is("stripe"))
                    .body("gateway_account_credentials.size()", is(1))
                    .body("gateway_account_credentials[0].payment_provider", is("stripe"))
                    .body("gateway_account_credentials[0].state", is("ACTIVE"))
                    .body("gateway_account_credentials[0].gateway_account_id", is(accountIdAsInt))
                    .body("gateway_account_credentials[0].credentials", hasEntry("stripe_account_id", "a-stripe-account-id"))
                    .body("gateway_account_credentials[0]", hasKey("gateway_account_credential_id"));
        }

        @Test
        void shouldReturnAccountInformationForGetAccountById_withEpdqCredentials() {
            long accountId = RandomUtils.nextInt();
            AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider(EPDQ.getName())
                    .withGatewayAccountId(accountId)
                    .withState(ACTIVE)
                    .withCredentials(Map.of(CREDENTIALS_MERCHANT_ID, "merchant-id",
                            CREDENTIALS_USERNAME, "username",
                            CREDENTIALS_PASSWORD, "password",
                            CREDENTIALS_SHA_IN_PASSPHRASE, "a-sha-in-passphrase",
                            CREDENTIALS_SHA_OUT_PASSPHRASE, "a-sha-out-passphrase"))
                    .build();

            defaultTestAccount = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withAccountId(accountId)
                    .withPaymentProvider(EPDQ.getName())
                    .withGatewayAccountCredentials(List.of(credentialsParams))
                    .insert();

            int accountIdAsInt = Math.toIntExact(accountId);
            app.givenSetup()
                    .get(ACCOUNTS_API_URL + accountId)
                    .then()
                    .statusCode(200)
                    .body("payment_provider", is("epdq"))
                    .body("gateway_account_credentials.size()", is(1))
                    .body("gateway_account_credentials[0].payment_provider", is("epdq"))
                    .body("gateway_account_credentials[0].state", is("ACTIVE"))
                    .body("gateway_account_credentials[0].gateway_account_id", is(accountIdAsInt))
                    .body("gateway_account_credentials[0].credentials", hasEntry("merchant_id", "merchant-id"))
                    .body("gateway_account_credentials[0].credentials", hasEntry("username", "username"))
                    .body("gateway_account_credentials[0].credentials", not(hasKey("password")))
                    .body("gateway_account_credentials[0].credentials", not(hasKey("sha_in_passphrase")))
                    .body("gateway_account_credentials[0].credentials", not(hasKey("sha_out_passphrase")));
        }

        @Test
        void shouldNotReturn3dsFlexCredentials_whenGatewayAccountHasNoCreds() {
            String gatewayAccountId = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(200)
                    .body("worldpay_3ds_flex", nullValue());
        }
    }

    @Test
    void shouldReturnEmptyCollectionOfAccountsWhenNoneFound() {
        app.givenSetup()
                .get("/v1/api/accounts")
                .then()
                .statusCode(200)
                .body("accounts", hasSize(0));
    }

    @Test
    void shouldGetAllGatewayAccountsWhenSearchWithNoParams() {
        String gatewayAccountId1 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());
        testHelpers.updateGatewayAccount(gatewayAccountId1, "allow_moto", true);
        String gatewayAccountId2 = testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withProvider("worldpay")
                        .build());
        
        app.getDatabaseTestHelper().insertWorldpay3dsFlexCredential(
                Long.valueOf(gatewayAccountId2),
                "macKey",
                "issuer",
                "org_unit_id",
                2L,
                true);

        app.givenSetup()
                .get("/v1/api/accounts")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(2))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)))
                .body("accounts[0].worldpay_3ds_flex", nullValue())
                .body("accounts[1].gateway_account_id", is(Integer.valueOf(gatewayAccountId2)))
                .body("accounts[1].worldpay_3ds_flex.issuer", is("issuer"))
                .body("accounts[1].worldpay_3ds_flex.organisational_unit_id", is("org_unit_id"))
                .body("accounts[1].worldpay_3ds_flex.exemption_engine_enabled", is(true))
                .body("accounts[1].worldpay_3ds_flex", not(hasKey("jwt_mac_key")));
    }

    @Test
    public void shouldReturnAccountInformationWhenSearchingByWorldpayMerchantCodeInOneOffPaymentCredentials() {
        long accountId = RandomUtils.nextInt();
        AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(WORLDPAY.getName())
                .withGatewayAccountId(accountId)
                .withState(ACTIVE)
                .withCredentials(Map.of(
                        ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                CREDENTIALS_MERCHANT_CODE, "one-off-merchant-code",
                                CREDENTIALS_USERNAME, "one-off-username",
                                CREDENTIALS_PASSWORD, "one-off-password")))
                .build();

        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(accountId)
                .withPaymentProvider(WORLDPAY.getName())
                .withDefaultCredentials()
                .withGatewayAccountCredentials(List.of(credentialsParams))
                .insert();

        int accountIdAsInt = Math.toIntExact(accountId);
        app.givenSetup()
                .get("/v1/api/accounts?payment_provider_account_id=one-off-merchant-code")
                .then()
                .statusCode(200)
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(accountIdAsInt))
                .body("accounts[0].payment_provider", is("worldpay"));
    }

    @Test
    public void shouldReturnAccountInformationWhenSearchingByWorldpayMerchantCodeInRecurringCustomerInitiatedCredentials() {
        long accountId = RandomUtils.nextInt();
        AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(WORLDPAY.getName())
                .withGatewayAccountId(accountId)
                .withState(ACTIVE)
                .withCredentials(Map.of(
                        RECURRING_CUSTOMER_INITIATED, Map.of(
                                CREDENTIALS_MERCHANT_CODE, "recurring-merchant-code",
                                CREDENTIALS_USERNAME, "recurring-username",
                                CREDENTIALS_PASSWORD, "recurring-password")))
                .build();

        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(accountId)
                .withPaymentProvider(WORLDPAY.getName())
                .withDefaultCredentials()
                .withGatewayAccountCredentials(List.of(credentialsParams))
                .insert();

        int accountIdAsInt = Math.toIntExact(accountId);
        app.givenSetup()
                .get("/v1/api/accounts?payment_provider_account_id=recurring-merchant-code")
                .then()
                .statusCode(200)
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(accountIdAsInt))
                .body("accounts[0].payment_provider", is("worldpay"));
    }
    
    @Test
    public void shouldSetApplePayEnabledByDefaultForSandboxAccount() {
        String gatewayAccountId1 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().withProvider("sandbox").build());
        String gatewayAccountId2 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().withProvider("worldpay").build());

        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId1)
                .then()
                .body("allow_apple_pay", is(true));

        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId2)
                .then()
                .body("allow_apple_pay", is(false));
    }

    @Test
    void shouldGetGatewayAccountsByIds() {
        String gatewayAccountId1 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());
        String gatewayAccountId2 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());
        testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());

        app.givenSetup()
                .get("/v1/api/accounts?accountIds=" + gatewayAccountId1 + "," + gatewayAccountId2)
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(2))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)))
                .body("accounts[0].external_id", is(notNullValue()))
                .body("accounts[1].gateway_account_id", is(Integer.valueOf(gatewayAccountId2)))
                .body("accounts[1].external_id", is(notNullValue()));
    }

    @Test
    void shouldFilterGetGatewayAccountForExistingAccountByServiceId() {
        String serviceId = "aValidServiceId";
        String anotherServiceId = "anotherServiceId";
        String nonExistentServiceId = "nonExistentServiceId";
        
        String gatewayAccountId1 = testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withServiceId(serviceId)
                        .build());
        testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withServiceId(anotherServiceId)
                        .build());
        
        app.givenSetup().accept(JSON)
                .get(format("/v1/api/accounts?serviceIds=%s,%s", nonExistentServiceId, serviceId))
                .then()
                .statusCode(200)
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.parseInt(gatewayAccountId1)))
                .body("accounts[0].service_id", is(serviceId));

        app.givenSetup().accept(JSON)
                .get(format("/v1/api/accounts?serviceIds=%s", nonExistentServiceId))
                .then()
                .statusCode(200)
                .body("accounts", hasSize(0));
    }

    @Test
    void shouldGetGatewayAccountsByMotoEnabled() {
        String gatewayAccountId1 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());
        testHelpers.updateGatewayAccount(gatewayAccountId1, "allow_moto", true);
        testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());

        app.givenSetup()
                .get("/v1/api/accounts?moto_enabled=true")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    void shouldGetGatewayAccountsByMotoDisabled() {
        String gatewayAccountId1 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());
        testHelpers.updateGatewayAccount(gatewayAccountId1, "allow_moto", true);
        String gatewayAccountId2 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());

        app.givenSetup()
                .get("/v1/api/accounts?moto_enabled=false")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId2)));
    }

    @Test
    void shouldGetGatewayAccountsByApplePayEnabled() {
        String gatewayAccountId1 = testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withProvider("worldpay")
                        .withAllowApplePay(true)
                        .build());
        testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withProvider("stripe")
                        .build());

        app.givenSetup()
                .get("/v1/api/accounts?apple_pay_enabled=true")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    void shouldGetGatewayAccountsByGooglePayEnabled() {
        String gatewayAccountId1 = testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withProvider("worldpay")
                        .withAllowGooglePay(true)
                        .build());
        testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withProvider("stripe")
                        .build());

        app.givenSetup()
                .get("/v1/api/accounts?google_pay_enabled=true")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    void shouldGetGatewayAccountsByType() {
        String gatewayAccountId1 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder()
                .withProvider("stripe")
                .withType(LIVE)
                .build());
        testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());

        app.givenSetup()
                .get("/v1/api/accounts?type=live")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    void shouldGetGatewayAccountsByRecurringEnabled() {
        String gatewayAccountId1 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder()
                .withProvider("worldpay")
                .build());
        testHelpers.updateGatewayAccount(gatewayAccountId1, "recurring_enabled", true);
        testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());

        app.givenSetup()
                .get("/v1/api/accounts?recurring_enabled=true")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    void shouldGetGatewayAccountsByProvider() {
        String gatewayAccountId1 = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder()
                .withProvider("worldpay")
                .build());
        testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder()
                .withProvider("stripe")
                .build());

        app.givenSetup()
                .get("/v1/api/accounts?payment_provider=worldpay")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    void shouldReturn422ForMotoEnabledNotBooleanValue() {
        app.givenSetup()
                .get("/v1/api/accounts?moto_enabled=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [moto_enabled] must be true or false"));
    }

    @Test
    void shouldReturn422ForApplePayEnabledNotBooleanValue() {
        app.givenSetup()
                .get("/v1/api/accounts?apple_pay_enabled=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [apple_pay_enabled] must be true or false"));
    }

    @Test
    void shouldReturn422ForGooglePayEnabledNotBooleanValue() {
        app.givenSetup()
                .get("/v1/api/accounts?google_pay_enabled=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [google_pay_enabled] must be true or false"));
    }

    @Test
    void shouldReturn422ForRequires3dsdNotBooleanValue() {
        app.givenSetup()
                .get("/v1/api/accounts?requires_3ds=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [requires_3ds] must be true or false"));
    }

    @Test
    void shouldReturn422ForTypeNotAllowedValue() {
        app.givenSetup()
                .get("/v1/api/accounts?type=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [type] must be 'live' or 'test'"));
    }

    @Test
    void shouldReturn422ForPaymentProviderNotAllowedValue() {
        app.givenSetup()
                .get("/v1/api/accounts?payment_provider=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [payment_provider] must be one of 'sandbox', 'worldpay', 'smartpay', 'epdq' or 'stripe'"));
    }

    @Test
    void shouldReturn422WhenRecurringEnabledIsNotABooleanValue() {
        app.givenSetup()
                .get("/v1/api/accounts?recurring_enabled=somerandomvalue")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [recurring_enabled] must be true or false"));
    }

    @Test
    void getAccountShouldReturn404IfAccountIdIsNotNumeric() {
        String unknownAccountId = "92348739wsx673hdg";

        app.givenSetup()
                .get(ACCOUNTS_API_URL + unknownAccountId)
                .then()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", Is.is(404))
                .body("message", Is.is("HTTP 404 Not Found"));
    }
    
    @Nested
    class Update3dsToggleByServiceIdAndAccountType {

        private String serviceId;

        @BeforeEach
        void before() {
            serviceId = RandomIdGenerator.newId();
            Map<String, String> gatewayAccountRequest = Map.of(
                    "payment_provider", "worldpay",
                    "service_id", serviceId,
                    "service_name", "Service Name",
                    "type", "test");

            app.givenSetup().body(toJson(gatewayAccountRequest)).post(ACCOUNTS_API_URL);
        }
        
        @Test
        void update3dsToggleSuccessfully() {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("requires3ds", is(false));

            app.givenSetup()
                    .body(toJson(Map.of("toggle_3ds", true)))
                    .patch(format("/v1/frontend/service/%s/account/test/3ds-toggle", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("requires3ds", is(true));
            
            app.givenSetup()
                    .body(toJson(Map.of("toggle_3ds", false)))
                    .patch(format("/v1/frontend/service/%s/account/test/3ds-toggle", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("requires3ds", is(false));
        }
        
        @Test
        void setting3dsToggleToFalse_WhenA3dsCardTypeIsAccepted_returnsConflict() {
            app.givenSetup()
                    .body(toJson(Map.of("toggle_3ds", true)))
                    .patch(format("/v1/frontend/service/%s/account/test/3ds-toggle", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            String maestroCardTypeId = app.getDatabaseTestHelper().getCardTypeId("maestro", "DEBIT");

            app.givenSetup().accept(JSON)
                    .body("{\"card_types\": [\"" + maestroCardTypeId + "\"]}")
                    .post(format("/v1/frontend/service/%s/account/test/card-types", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .body(toJson(Map.of("toggle_3ds", false)))
                    .patch(format("/v1/frontend/service/%s/account/test/3ds-toggle", serviceId))
                    .then()
                    .statusCode(CONFLICT.getStatusCode());
        }
    }
    
    @Nested
    class Update3dsToggleByGatewayAccountId {
        String gatewayAccountId;
        @BeforeEach
        void createGatewayAccount() {
            gatewayAccountId = testHelpers.createGatewayAccount(
                    aCreateGatewayAccountPayloadBuilder()
                            .withProvider("worldpay")
                            .build());
        }
        
        @Test
        void shouldToggle3dsToTrue() {
            app.givenSetup()
                    .body(toJson(Map.of("toggle_3ds", true)))
                    .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("requires3ds", is(true));
        }

        @Test
        void shouldToggle3dsToFalse() {
            app.givenSetup()
                    .body(toJson(Map.of("toggle_3ds", false)))
                    .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("requires3ds", is(false));
        }

        @Test
        void shouldReturn409Conflict_Toggling3dsToFalse_WhenA3dsCardTypeIsAccepted() {
            String maestroCardTypeId = app.getDatabaseTestHelper().getCardTypeId("maestro", "DEBIT");

            app.givenSetup()
                    .body(toJson(Map.of("toggle_3ds", true)))
                    .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup().accept(JSON)
                    .body("{\"card_types\": [\"" + maestroCardTypeId + "\"]}")
                    .post(ACCOUNTS_FRONTEND_URL + gatewayAccountId + "/card-types")
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .body(toJson(Map.of("toggle_3ds", false)))
                    .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                    .then()
                    .statusCode(CONFLICT.getStatusCode());
        }
    }
    
    @Test
    void shouldReturn3dsFlexCredentials_whenGatewayAccountHasCreds() {
        String gatewayAccountId = testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withProvider("worldpay")
                        .build());
        app.getDatabaseTestHelper().insertWorldpay3dsFlexCredential(Long.valueOf(gatewayAccountId), "macKey", "issuer", "org_unit_id", 2L);
        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("$", hasKey("worldpay_3ds_flex"))
                .body("worldpay_3ds_flex.issuer", is("issuer"))
                .body("worldpay_3ds_flex.organisational_unit_id", is("org_unit_id"))
                .body("worldpay_3ds_flex", not(hasKey("jwt_mac_key")))
                .body("worldpay_3ds_flex", not(hasKey("version")))
                .body("worldpay_3ds_flex", not(hasKey("gateway_account_id")))
                .body("worldpay_3ds_flex.exemption_engine_enabled", is(false));
    }

    @Test
    void shouldReturn3dsFlexCredentials_whenGatewayAccountHasCreds_byServiceIdAndAccountType() {
        String gatewayAccountId = testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withProvider("worldpay")
                        .withServiceId("a-valid-service-id")
                        .build());
        app.getDatabaseTestHelper().insertWorldpay3dsFlexCredential(Long.valueOf(gatewayAccountId), "macKey", "issuer", "org_unit_id", 2L);
        String url = ACCOUNTS_API_SERVICE_ID_URL.replace("{serviceId}", "a-valid-service-id").replace("{accountType}", GatewayAccountType.TEST.name());
        app.givenSetup()
                .get(url)
                .then()
                .statusCode(200)
                .body("$", hasKey("worldpay_3ds_flex"))
                .body("worldpay_3ds_flex.issuer", is("issuer"))
                .body("worldpay_3ds_flex.organisational_unit_id", is("org_unit_id"))
                .body("worldpay_3ds_flex", not(hasKey("jwt_mac_key")))
                .body("worldpay_3ds_flex", not(hasKey("version")))
                .body("worldpay_3ds_flex", not(hasKey("gateway_account_id")))
                .body("worldpay_3ds_flex.exemption_engine_enabled", is(false));
    }

    @Test
    void shouldNotReturn3dsFlexCredentials_whenGatewayIsNotAWorldpayAccount() {
        String gatewayAccountId = testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withProvider("stripe")
                        .build());
        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("worldpay_3ds_flex", nullValue());
    }
}
