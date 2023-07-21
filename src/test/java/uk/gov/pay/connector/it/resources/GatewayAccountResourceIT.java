package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.core.Is;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.contains;
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
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.resource.GatewayAccountCredentialsRequestValidator.FIELD_GATEWAY_MERCHANT_ID;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccountResourceIT extends GatewayAccountResourceTestBase {

    private static ObjectMapper objectMapper = new ObjectMapper();

    private DatabaseFixtures.TestAccount defaultTestAccount;

    @Test
    public void getAccountShouldReturn404IfAccountIdIsUnknown() {
        String unknownAccountId = "92348739";
        givenSetup()
                .get(ACCOUNTS_API_URL + unknownAccountId)
                .then()
                .statusCode(404);
    }

    @Test
    public void getAccountShouldNotReturnCredentials() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay");
        givenSetup()
                .get(ACCOUNTS_API_URL + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("credentials", is(nullValue()));
    }

    @Test
    public void getAccountShouldNotReturnCardTypes() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay");
        givenSetup()
                .get(ACCOUNTS_API_URL + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("card_types", is(nullValue()));
    }

    @Test
    public void getAccountShouldReturnDescriptionAndAnalyticsId() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "desc", "id");
        givenSetup()
                .get(ACCOUNTS_API_URL + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("analytics_id", is("id"))
                .body("description", is("desc"));
    }

    @Test
    public void getAccountShouldReturnAnalyticsId() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", null, "id");
        givenSetup()
                .get(ACCOUNTS_API_URL + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("analytics_id", is("id"))
                .body("description", is(nullValue()));
    }

    @Test
    public void getAccountShouldReturnDescription() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "desc", null);
        givenSetup()
                .get(ACCOUNTS_API_URL + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("analytics_id", is(nullValue()))
                .body("description", is("desc"));
    }

    @Test
    public void getAccountShouldReturn3dsSetting() {
        String gatewayAccountId = createAGatewayAccountFor("stripe", "desc", "id", "true", "test");
        givenSetup()
                .get(ACCOUNTS_API_URL + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("requires3ds", is(true));
    }

    @Test
    public void getAccountShouldReturnCorporateCreditCardSurchargeAmountAndCorporateDebitCardSurchargeAmount() {
        int corporateCreditCardSurchargeAmount = 250;
        int corporateDebitCardSurchargeAmount = 50;
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withCorporateCreditCardSurchargeAmount(corporateCreditCardSurchargeAmount)
                .withCorporateDebitCardSurchargeAmount(corporateDebitCardSurchargeAmount)
                .insert();

        givenSetup()
                .get(ACCOUNTS_API_URL + defaultTestAccount.getAccountId())
                .then()
                .statusCode(200)
                .body("corporate_credit_card_surcharge_amount", is(corporateCreditCardSurchargeAmount))
                .body("corporate_debit_card_surcharge_amount", is(corporateDebitCardSurchargeAmount));
    }

    @Test
    public void getAccountShouldReturnCorporatePrepaidDebitCardSurchargeAmount() {
        int corporatePrepaidDebitCardSurchargeAmount = 50;
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withCorporatePrepaidDebitCardSurchargeAmount(corporatePrepaidDebitCardSurchargeAmount)
                .insert();

        givenSetup()
                .get(ACCOUNTS_API_URL + defaultTestAccount.getAccountId())
                .then()
                .statusCode(200)
                .body("corporate_prepaid_debit_card_surcharge_amount", is(corporatePrepaidDebitCardSurchargeAmount));
    }

    @Test
    public void shouldReturnAccountInformationForGetAccountById_withWorldpayCredentials() {
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

        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
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

        databaseTestHelper.allowApplePay(accountId);
        databaseTestHelper.allowZeroAmount(accountId);
        databaseTestHelper.blockPrepaidCards(accountId);
        databaseTestHelper.enableProviderSwitch(accountId);
        databaseTestHelper.setDisabled(accountId);
        databaseTestHelper.setDisabledReason(accountId, "Disabled because reasons");

        int accountIdAsInt = Math.toIntExact(accountId);
        givenSetup()
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
                .body("gateway_account_credentials[0].credentials", hasEntry("merchant_id", "legacy-merchant-code"))
                .body("gateway_account_credentials[0].credentials", hasEntry("username", "legacy-username"))
                .body("gateway_account_credentials[0].credentials", not(hasKey("password")))
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
                .body("gateway_account_credentials[0].credentials.recurring_merchant_initiated", not(hasKey("password")));
    }

    @Test
    public void shouldReturnAccountInformationForGetAccountById_forLegacyCredentials() {
        long accountId = RandomUtils.nextInt();
        AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(WORLDPAY.getName())
                .withGatewayAccountId(accountId)
                .withState(ACTIVE)
                .withCredentials(Map.of(
                        CREDENTIALS_MERCHANT_ID, "legacy-merchant-code",
                        CREDENTIALS_USERNAME, "legacy-username",
                        CREDENTIALS_PASSWORD, "legacy-password"))
                .build();

        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(accountId)
                .withGatewayAccountCredentials(List.of(credentialsParams))
                .insert();

        givenSetup()
                .get(ACCOUNTS_API_URL + accountId)
                .then()
                .statusCode(200)
                .body("gateway_account_credentials[0].credentials", hasEntry("merchant_id", "legacy-merchant-code"))
                .body("gateway_account_credentials[0].credentials", hasEntry("username", "legacy-username"))
                .body("gateway_account_credentials[0].credentials", not(hasKey("password")))
                .body("gateway_account_credentials[0].credentials", hasKey("one_off_customer_initiated"))
                .body("gateway_account_credentials[0].credentials.one_off_customer_initiated", hasEntry("merchant_code", "legacy-merchant-code"))
                .body("gateway_account_credentials[0].credentials.one_off_customer_initiated", hasEntry("username", "legacy-username"))
                .body("gateway_account_credentials[0].credentials.one_off_customer_initiated", not(hasKey("password")));
    }

    @Test
    public void shouldReturnAccountInformationForGetAccountById_withStripeCredentials() {
        long accountId = RandomUtils.nextInt();
        AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(STRIPE.getName())
                .withGatewayAccountId(accountId)
                .withState(ACTIVE)
                .withCredentials(Map.of(CREDENTIALS_STRIPE_ACCOUNT_ID, "a-stripe-account-id"))
                .build();

        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(accountId)
                .withPaymentProvider(STRIPE.getName())
                .withGatewayAccountCredentials(List.of(credentialsParams))
                .insert();

        int accountIdAsInt = Math.toIntExact(accountId);
        givenSetup()
                .get(ACCOUNTS_API_URL + accountId)
                .then()
                .statusCode(200)
                .body("payment_provider", is("stripe"))
                .body("gateway_account_credentials.size()", is(1))
                .body("gateway_account_credentials[0].payment_provider", is("stripe"))
                .body("gateway_account_credentials[0].state", is("ACTIVE"))
                .body("gateway_account_credentials[0].gateway_account_id", is(accountIdAsInt))
                .body("gateway_account_credentials[0].credentials", hasEntry("stripe_account_id", "a-stripe-account-id"));
    }

    @Test
    public void shouldReturnAccountInformationForGetAccountById_withEpdqCredentials() {
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

        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(accountId)
                .withPaymentProvider(EPDQ.getName())
                .withGatewayAccountCredentials(List.of(credentialsParams))
                .insert();

        int accountIdAsInt = Math.toIntExact(accountId);
        givenSetup()
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
    public void shouldNotReturn3dsFlexCredentials_whenGatewayAccountHasNoCreds() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "a-description", "analytics-id");
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("worldpay_3ds_flex", nullValue());
    }

    @Test
    public void shouldReturnEmptyCollectionOfAccountsWhenNoneFound() {
        givenSetup()
                .get("/v1/api/accounts")
                .then()
                .statusCode(200)
                .body("accounts", hasSize(0));
    }

    @Test
    public void shouldGetAllGatewayAccountsWhenSearchWithNoParams() {
        String gatewayAccountId1 = createAGatewayAccountFor("sandbox");
        updateGatewayAccount(gatewayAccountId1, "allow_moto", true);
        String gatewayAccountId2 = createAGatewayAccountFor("worldpay", "a-description", "analytics-id");
        databaseTestHelper.insertWorldpay3dsFlexCredential(
                Long.valueOf(gatewayAccountId2),
                "macKey",
                "issuer",
                "org_unit_id",
                2L,
                true);

        givenSetup()
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
    public void shouldGetGatewayAccountsByIds() {
        String gatewayAccountId1 = createAGatewayAccountFor("sandbox");
        String gatewayAccountId2 = createAGatewayAccountFor("sandbox");
        createAGatewayAccountFor("sandbox");

        givenSetup()
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
    public void shouldFilterGetGatewayAccountForExistingAccountByServiceId() {
        String gatewayAccountId1 = createAGatewayAccountFor("sandbox");
        String gatewayAccountId2 = createAGatewayAccountFor("sandbox");
        String serviceId = "someexternalserviceid";
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");

        databaseTestHelper.updateServiceIdFor(Long.parseLong(gatewayAccountId1), serviceId);
        databaseTestHelper.updateServiceIdFor(Long.parseLong(gatewayAccountId2), "notsearchedforserviceid");

        givenSetup().accept(JSON)
                .get("/v1/api/accounts?serviceIds=somemissingserviceid,anotherserviceid," + serviceId)
                .then()
                .statusCode(200)
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.parseInt(gatewayAccountId1)))
                .body("accounts[0].service_id", is(serviceId));

        givenSetup().accept(JSON)
                .get("/v1/api/accounts?serviceIds=nonexistingserviceid")
                .then()
                .statusCode(200)
                .body("accounts", hasSize(0));
    }

    @Test
    public void shouldGetGatewayAccountsByMotoEnabled() {
        String gatewayAccountId1 = createAGatewayAccountFor("sandbox");
        updateGatewayAccount(gatewayAccountId1, "allow_moto", true);
        createAGatewayAccountFor("sandbox");

        givenSetup()
                .get("/v1/api/accounts?moto_enabled=true")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    public void shouldGetGatewayAccountsByMotoDisabled() {
        String gatewayAccountId1 = createAGatewayAccountFor("sandbox");
        updateGatewayAccount(gatewayAccountId1, "allow_moto", true);
        String gatewayAccountId2 = createAGatewayAccountFor("sandbox");

        givenSetup()
                .get("/v1/api/accounts?moto_enabled=false")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId2)));
    }

    @Test
    public void shouldGetGatewayAccountsByApplePayEnabled() {
        String gatewayAccountId1 = createAGatewayAccountFor("worldpay");
        updateGatewayAccount(gatewayAccountId1, "allow_apple_pay", true);
        createAGatewayAccountFor("sandbox");

        givenSetup()
                .get("/v1/api/accounts?apple_pay_enabled=true")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    public void shouldGetGatewayAccountsByGooglePayEnabled() {
        String gatewayAccountId1 = createAGatewayAccountFor("worldpay");
        updateGatewayAccount(gatewayAccountId1, "allow_google_pay", true);
        createAGatewayAccountFor("sandbox");

        givenSetup()
                .get("/v1/api/accounts?google_pay_enabled=true")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    public void shouldGetGatewayAccountsByType() {
        String gatewayAccountId1 = createAGatewayAccountFor("worldpay", "descr", "analytics", "true", "live");
        createAGatewayAccountFor("sandbox");

        givenSetup()
                .get("/v1/api/accounts?type=live")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    public void shouldGetGatewayAccountsByRecurringEnabled() {
        String gatewayAccountId1 = createAGatewayAccountFor("worldpay");
        updateGatewayAccount(gatewayAccountId1, "recurring_enabled", true);
        createAGatewayAccountFor("sandbox");

        givenSetup()
                .get("/v1/api/accounts?recurring_enabled=true")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    public void shouldGetGatewayAccountsByProvider() {
        String gatewayAccountId1 = createAGatewayAccountFor("worldpay");
        createAGatewayAccountFor("sandbox");

        givenSetup()
                .get("/v1/api/accounts?payment_provider=worldpay")
                .then()
                .statusCode(OK.getStatusCode())
                .body("accounts", hasSize(1))
                .body("accounts[0].gateway_account_id", is(Integer.valueOf(gatewayAccountId1)));
    }

    @Test
    public void shouldReturn422ForMotoEnabledNotBooleanValue() {
        givenSetup()
                .get("/v1/api/accounts?moto_enabled=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [moto_enabled] must be true or false"));
    }

    @Test
    public void shouldReturn422ForApplePayEnabledNotBooleanValue() {
        givenSetup()
                .get("/v1/api/accounts?apple_pay_enabled=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [apple_pay_enabled] must be true or false"));
    }

    @Test
    public void shouldReturn422ForGooglePayEnabledNotBooleanValue() {
        givenSetup()
                .get("/v1/api/accounts?google_pay_enabled=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [google_pay_enabled] must be true or false"));
    }

    @Test
    public void shouldReturn422ForRequires3dsdNotBooleanValue() {
        givenSetup()
                .get("/v1/api/accounts?requires_3ds=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [requires_3ds] must be true or false"));
    }

    @Test
    public void shouldReturn422ForTypeNotAllowedValue() {
        givenSetup()
                .get("/v1/api/accounts?type=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [type] must be 'live' or 'test'"));
    }

    @Test
    public void shouldReturn422ForPaymentProviderNotAllowedValue() {
        givenSetup()
                .get("/v1/api/accounts?payment_provider=blah")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [payment_provider] must be one of 'sandbox', 'worldpay', 'smartpay', 'epdq' or 'stripe'"));
    }

    @Test
    public void shouldReturn422WhenRecurringEnabledIsNotABooleanValue() {
        givenSetup()
                .get("/v1/api/accounts?recurring_enabled=somerandomvalue")
                .then()
                .statusCode(422)
                .body("message[0]", is("Parameter [recurring_enabled] must be true or false"));
    }

    @Test
    public void getAccountShouldReturn404IfAccountIdIsNotNumeric() {
        String unknownAccountId = "92348739wsx673hdg";

        givenSetup()
                .get(ACCOUNTS_API_URL + unknownAccountId)
                .then()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", Is.is(404))
                .body("message", Is.is("HTTP 404 Not Found"));
    }

    @Test
    public void createValidNotificationCredentials_responseShouldBe200_Ok() {
        String gatewayAccountId = createAGatewayAccountFor("stripe");
        givenSetup()
                .body(toJson(Map.of("username", "bob", "password", "bobsbigsecret")))
                .post("/v1/api/accounts/" + gatewayAccountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());
    }

    @Test
    public void patchGatewayAccountAnalyticsId_responseShouldBe200_Ok() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "old-desc", "old-id");
        givenSetup()
                .body(toJson(Map.of("analytics_id", "new-id")))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/description-analytics-id")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("description", is("old-desc"))
                .body("analytics_id", is("new-id"));
    }

    @Test
    public void patchGatewayAccountDescription_responseShouldBe200_Ok() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "old-desc", "old-id");
        givenSetup()
                .body(toJson(Map.of("description", "new-desc")))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/description-analytics-id")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("description", is("new-desc"))
                .body("analytics_id", is("old-id"));
    }

    @Test
    public void patchGatewayAccountDescriptionAndAnalyticsId_responseShouldBe200_Ok() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "old-desc", "old-id");
        givenSetup()
                .body(toJson(Map.of("analytics_id", "new-id", "description", "new-desc")))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/description-analytics-id")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("description", is("new-desc"))
                .body("analytics_id", is("new-id"));
    }

    @Test
    public void patchGatewayAccountDescriptionAndAnalyticsIdEmpty_responseShouldReturn400() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "old-desc", "old-id");
        givenSetup()
                .body(toJson(Map.of()))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/description-analytics-id")
                .then()
                .statusCode(BAD_REQUEST.getStatusCode());

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("description", is("old-desc"))
                .body("analytics_id", is("old-id"));
    }

    @Test
    public void shouldToggle3dsToTrue() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "old-desc", "old-id");
        givenSetup()
                .body(toJson(Map.of("toggle_3ds", true)))
                .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("requires3ds", is(true));
    }

    @Test
    public void shouldReturn200WhenWorldpayExemptionEngineEnabledIsUpdated() throws JsonProcessingException {
        String gatewayAccountId = createAGatewayAccountFor(WORLDPAY.getName(), "a-description", "analytics-id");
        databaseTestHelper.insertWorldpay3dsFlexCredential(
                Long.valueOf(gatewayAccountId),
                "macKey",
                "issuer",
                "org_unit_id",
                2L);
        String payload = objectMapper.writeValueAsString(Map.of(
                "op", "replace",
                "path", "worldpay_exemption_engine_enabled",
                "value", true));

        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode())
                .body("worldpay_3ds_flex.exemption_engine_enabled", is(true));
    }

    @Test
    public void shouldReturn3dsFlexCredentials_whenGatewayAccountHasCreds() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "a-description", "analytics-id");
        databaseTestHelper.insertWorldpay3dsFlexCredential(Long.valueOf(gatewayAccountId), "macKey", "issuer", "org_unit_id", 2L);
        givenSetup()
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
    public void shouldNotReturn3dsFlexCredentials_whenGatewayIsNotAWorldpayAccount() {
        String gatewayAccountId = createAGatewayAccountFor("stripe", "a-description", "analytics-id");
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("worldpay_3ds_flex", nullValue());
    }

    @Test
    public void shouldToggle3dsToFalse() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "old-desc", "old-id");
        givenSetup()
                .body(toJson(Map.of("toggle_3ds", false)))
                .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("requires3ds", is(false));
    }

    @Test
    public void shouldReturn409Conflict_Toggling3dsToFalse_WhenA3dsCardTypeIsAccepted() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "desc", "id");
        String maestroCardTypeId = databaseTestHelper.getCardTypeId("maestro", "DEBIT");

        givenSetup()
                .body(toJson(Map.of("toggle_3ds", true)))
                .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup().accept(JSON)
                .body("{\"card_types\": [\"" + maestroCardTypeId + "\"]}")
                .post(ACCOUNTS_FRONTEND_URL + gatewayAccountId + "/card-types")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .body(toJson(Map.of("toggle_3ds", false)))
                .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                .then()
                .statusCode(CONFLICT.getStatusCode());
    }

    @Test
    public void whenNotificationCredentialsInvalidKeys_shouldReturn400() {
        String gatewayAccountId = createAGatewayAccountFor("stripe");
        givenSetup()
                .body(toJson(Map.of("bob", "bob", "bobby", "bobsbigsecret")))
                .post("/v1/api/accounts/" + gatewayAccountId + "/notification-credentials")
                .then()
                .statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    public void whenNotificationCredentialsInvalidValues_shouldReturn400() {
        String gatewayAccountId = createAGatewayAccountFor("stripe");
        givenSetup()
                .body(toJson(Map.of("username", "bob", "password", "tooshort")))
                .post("/v1/api/accounts/" + gatewayAccountId + "/notification-credentials")
                .then()
                .contentType(ContentType.JSON)
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("message", contains("Credentials update failure: Invalid password length"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldReturn200_whenNotifySettingsIsUpdated() throws Exception {
        String gatewayAccountId = createAGatewayAccountFor("worldpay");
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "notify_settings",
                "value", Map.of("api_token", "anapitoken",
                        "template_id", "atemplateid",
                        "refund_issued_template_id", "anothertemplate")));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());
    }

    @Test
    public void shouldReturn400_whenNotifySettingsIsUpdated_withWrongOp() throws Exception {
        String gatewayAccountId = createAGatewayAccountFor("worldpay");
        String payload = objectMapper.writeValueAsString(Map.of("op", "insert",
                "path", "notify_settings",
                "value", Map.of("api_token", "anapitoken",
                        "template_id", "atemplateid")));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    public void shouldReturn200_whenBlockPrepaidCardsIsUpdated() throws Exception {
        String gatewayAccountId = createAGatewayAccountFor("worldpay");
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "block_prepaid_cards",
                "value", true));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode())
                .body("block_prepaid_cards", is(true));
    }

    @Test
    public void shouldReturn200_whenEmailCollectionModeIsUpdated() throws Exception {
        String gatewayAccountId = createAGatewayAccountFor("worldpay");
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "email_collection_mode",
                "value", "OFF"));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());
    }

    @Test
    public void shouldReturn400_whenEmailCollectionModeIsUpdated_withWrongValue() throws Exception {
        String gatewayAccountId = createAGatewayAccountFor("worldpay");
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "email_collection_mode",
                "value", "nope"));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    public void shouldReturn404ForNotifySettings_whenGatewayAccountIsNonExistent() throws Exception {
        String gatewayAccountId = "1000023";
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "notify_settings",
                "value", Map.of("api_token", "anapitoken",
                        "template_id", "atemplateid",
                        "refund_issued_template_id", "anothertemplate")));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    public void shouldReturn200_whenNotifySettingsIsRemoved() throws Exception {
        String gatewayAccountId = createAGatewayAccountFor("worldpay");
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "notify_settings",
                "value", Map.of("api_token", "anapitoken",
                        "template_id", "atemplateid",
                        "refund_issued_template_id", "anothertemplate")));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());

        payload = objectMapper.writeValueAsString(Map.of("op", "remove",
                "path", "notify_settings"));

        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());
    }

    @Test
    public void shouldReturn400_whenNotifySettingsIsRemoved_withWrongPath() throws Exception {
        String gatewayAccountId = createAGatewayAccountFor("worldpay");
        String payload = objectMapper.writeValueAsString(Map.of("op", "insert",
                "path", "notify_setting"));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    public void patchGatewayAccount_forCorporateCreditCardSurcharge() throws JsonProcessingException {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "a-description", "analytics-id");
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "corporate_credit_card_surcharge_amount",
                "value", 100));
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("corporate_debit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("corporate_credit_card_surcharge_amount", is(100))
                .body("corporate_debit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
    }

    @Test
    public void patchGatewayAccount_forCorporateDebitCardSurcharge() throws JsonProcessingException {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "a-description", "analytics-id");
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "corporate_debit_card_surcharge_amount",
                "value", 200));
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("corporate_debit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("corporate_debit_card_surcharge_amount", is(200))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
    }

    @Test
    public void patchGatewayAccount_forCorporatePrepaidDebitCardSurcharge() throws JsonProcessingException {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "a-description", "analytics-id");
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "corporate_prepaid_debit_card_surcharge_amount",
                "value", 400));
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("corporate_debit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("corporate_debit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(400));
    }

    @Test
    public void patchGatewayAccount_forAllowTelephonePaymentNotifications() throws JsonProcessingException {
        String gatewayAccountId = createAGatewayAccountFor("sandbox", "a-description", "analytics-id");
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "allow_telephone_payment_notifications",
                "value", true));
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("allow_telephone_payment_notifications", is(false));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("allow_telephone_payment_notifications", is(true));
    }

    @Test
    public void shouldReturn404ForCorporateSurcharge_whenGatewayAccountIsNonExistent() throws Exception {
        String gatewayAccountId = "1000023";
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "corporate_credit_card_surcharge_amount",
                "value", 100));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    public void patchGatewayAccount_setDisabledToFalse_shouldClearDisabledReason() throws JsonProcessingException {
        String gatewayAccountId = createAGatewayAccountFor("sandbox", "a-description", "analytics-id");
        long gatewayAccountIdAsLong = Long.parseLong(gatewayAccountId);
        databaseTestHelper.setDisabled(gatewayAccountIdAsLong);
        String disabledReason = "Because reasons";
        databaseTestHelper.setDisabledReason(gatewayAccountIdAsLong, disabledReason);

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("disabled", is(true))
                .body("disabled_reason", is(disabledReason));

        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "disabled",
                "value", false));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("disabled", is(false))
                .body("disabled_reason", is(nullValue()));
    }
}
