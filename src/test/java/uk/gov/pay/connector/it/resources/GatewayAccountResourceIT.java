package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.restassured.http.ContentType;
import org.hamcrest.core.Is;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccountResourceIT extends GatewayAccountResourceTestBase {
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
        String gatewayAccountId = extractGatewayAccountId(createAGatewayAccountFor(testContext.getPort(), "stripe", "desc", null, "true"));
        givenSetup()
                .get(ACCOUNTS_API_URL + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("toggle_3ds", is(true));
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
    public void getAccountShouldReturnCorporatePrepaidCreditCardSurchargeAmountAndCorporatePrepaidDebitCardSurchargeAmount() {
        int corporatePrepaidCreditCardSurchargeAmount = 250;
        int corporatePrepaidDebitCardSurchargeAmount = 50;
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withCorporatePrepaidCreditCardSurchargeAmount(corporatePrepaidCreditCardSurchargeAmount)
                .withCorporatePrepaidDebitCardSurchargeAmount(corporatePrepaidDebitCardSurchargeAmount)
                .insert();

        givenSetup()
                .get(ACCOUNTS_API_URL + defaultTestAccount.getAccountId())
                .then()
                .statusCode(200)
                .body("corporate_prepaid_credit_card_surcharge_amount", is(corporatePrepaidCreditCardSurchargeAmount))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(corporatePrepaidDebitCardSurchargeAmount));
    }

    @Test
    public void shouldReturnAccountInformationForGetAccountById() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();

        givenSetup()
                .get(ACCOUNTS_API_URL + defaultTestAccount.getAccountId())
                .then()
                .statusCode(200)
                .body("payment_provider", is("sandbox"))
                .body("gateway_account_id", is(Math.toIntExact(defaultTestAccount.getAccountId())))
                .body("type", is(TEST.toString()))
                .body("description", is("a description"))
                .body("analytics_id", is("an analytics id"))
                .body("email_collection_mode", is("OPTIONAL"))
                .body("email_notifications.PAYMENT_CONFIRMED.template_body", is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."))
                .body("email_notifications.PAYMENT_CONFIRMED.enabled", is(true))
                .body("email_notifications.REFUND_ISSUED.template_body", is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."))
                .body("email_notifications.REFUND_ISSUED.enabled", is(true))
                .body("service_name", is("service_name"))
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("allow_google_pay", is(false))
                .body("allow_apple_pay", is(false))
                .body("allow_zero_amount", is(false));
    }

    @Test
    public void shouldReturnCollectionOfAccounts() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(100)
                .withPaymentProvider("sandbox")
                .withType(TEST)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(200)
                .withPaymentProvider("sandbox")
                .withType(LIVE)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(300)
                .withPaymentProvider("smartpay")
                .withType(LIVE)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(400)
                .withPaymentProvider("worldpay")
                .withType(TEST)
                .withServiceName(null)
                .insert();

        // assert properties are there
        givenSetup()
                .get("/v1/api/accounts")
                .then()
                .statusCode(200)
                .body("accounts", hasSize(4))
                .body("accounts[0].gateway_account_id", is(100))
                .body("accounts[0].payment_provider", is("sandbox"))
                .body("accounts[0].type", is(TEST.toString()))
                .body("accounts[0].description", is("a description"))
                .body("accounts[0].service_name", is("service_name"))
                .body("accounts[0].analytics_id", is("an analytics id"))
                .body("accounts[0].corporate_credit_card_surcharge_amount", is(0))
                .body("accounts[0].corporate_debit_card_surcharge_amount", is(0))
                .body("accounts[0]._links.self.href", is("https://localhost:" + testContext.getPort() + ACCOUNTS_API_URL + 100))
                // and credentials should be missing
                .body("accounts[0].credentials", nullValue())

                .body("accounts[2].gateway_account_id", is(300))
                .body("accounts[2].payment_provider", is("smartpay"))
                .body("accounts[2].type", is(LIVE.toString()))
                .body("accounts[2].description", is("a description"))
                .body("accounts[2].service_name", is("service_name"))
                .body("accounts[2].analytics_id", is("an analytics id"))
                .body("accounts[2].corporate_credit_card_surcharge_amount", is(0))
                .body("accounts[2].corporate_debit_card_surcharge_amount", is(0))
                .body("accounts[2]._links.self.href", is("https://localhost:" + testContext.getPort() + ACCOUNTS_API_URL + 300))
                // and credentials should be missing
                .body("accounts[2].credentials", nullValue())

                // service name for the last one should be absent in response as its null
                .body("accounts[3].service_name", nullValue());
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
        String gatewayAccountId = createAGatewayAccountFor("smartpay");
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsbigsecret")))
                .post("/v1/api/accounts/" + gatewayAccountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());
    }

    @Test
    public void patchGatewayAccountAnalyticsId_responseShouldBe200_Ok() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "old-desc", "old-id");
        givenSetup()
                .body(toJson(ImmutableMap.of("analytics_id", "new-id")))
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
                .body(toJson(ImmutableMap.of("description", "new-desc")))
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
                .body(toJson(ImmutableMap.of("analytics_id", "new-id", "description", "new-desc")))
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
                .body(toJson(ImmutableMap.of()))
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
                .body(toJson(ImmutableMap.of("toggle_3ds", true)))
                .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("toggle_3ds", is(true));
    }

    @Test
    public void shouldToggle3dsToFalse() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "old-desc", "old-id");
        givenSetup()
                .body(toJson(ImmutableMap.of("toggle_3ds", false)))
                .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("toggle_3ds", is(false));
    }

    @Test
    public void shouldReturn409Conflict_Toggling3dsToFalse_WhenA3dsCardTypeIsAccepted() {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "desc", "id");
        String maestroCardTypeId = databaseTestHelper.getCardTypeId("maestro", "DEBIT");

        givenSetup()
                .body(toJson(ImmutableMap.of("toggle_3ds", true)))
                .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup().accept(JSON)
                .body("{\"card_types\": [\"" + maestroCardTypeId + "\"]}")
                .post(ACCOUNTS_FRONTEND_URL + gatewayAccountId + "/card-types")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .body(toJson(ImmutableMap.of("toggle_3ds", false)))
                .patch("/v1/frontend/accounts/" + gatewayAccountId + "/3ds-toggle")
                .then()
                .statusCode(CONFLICT.getStatusCode());
    }

    @Test
    public void whenNotificationCredentialsInvalidKeys_shouldReturn400() {
        String gatewayAccountId = createAGatewayAccountFor("smartpay");
        givenSetup()
                .body(toJson(ImmutableMap.of("bob", "bob", "bobby", "bobsbigsecret")))
                .post("/v1/api/accounts/" + gatewayAccountId + "/notification-credentials")
                .then()
                .statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    public void whenNotificationCredentialsInvalidValues_shouldReturn400() {
        String gatewayAccountId = createAGatewayAccountFor("smartpay");
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "tooshort")))
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
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", "notify_settings",
                "value", ImmutableMap.of("api_token", "anapitoken",
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
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "insert",
                "path", "notify_settings",
                "value", ImmutableMap.of("api_token", "anapitoken",
                        "template_id", "atemplateid")));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    public void shouldReturn200_whenEmailCollectionModeIsUpdated() throws Exception {
        String gatewayAccountId = createAGatewayAccountFor("worldpay");
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
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
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
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
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", "notify_settings",
                "value", ImmutableMap.of("api_token", "anapitoken",
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
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", "notify_settings",
                "value", ImmutableMap.of("api_token", "anapitoken",
                        "template_id", "atemplateid",
                        "refund_issued_template_id", "anothertemplate")));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(OK.getStatusCode());

        payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "remove",
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
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "insert",
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
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", "corporate_credit_card_surcharge_amount",
                "value", 100));
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("corporate_debit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_credit_card_surcharge_amount", is(0))
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
                .body("corporate_prepaid_credit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
    }

    @Test
    public void patchGatewayAccount_forCorporateDebitCardSurcharge() throws JsonProcessingException {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "a-description", "analytics-id");
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", "corporate_debit_card_surcharge_amount",
                "value", 200));
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("corporate_debit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_credit_card_surcharge_amount", is(0))
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
                .body("corporate_prepaid_credit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
    }

    @Test
    public void patchGatewayAccount_forCorporatePrepaidCreditCardSurcharge() throws JsonProcessingException {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "a-description", "analytics-id");
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", "corporate_prepaid_credit_card_surcharge_amount",
                "value", 300));
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("corporate_debit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_credit_card_surcharge_amount", is(0))
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
                .body("corporate_prepaid_credit_card_surcharge_amount", is(300))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
    }

    @Test
    public void patchGatewayAccount_forCorporatePrepaidDebitCardSurcharge() throws JsonProcessingException {
        String gatewayAccountId = createAGatewayAccountFor("worldpay", "a-description", "analytics-id");
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", "corporate_prepaid_debit_card_surcharge_amount",
                "value", 400));
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("corporate_debit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_credit_card_surcharge_amount", is(0))
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
                .body("corporate_prepaid_credit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(400));
    }

    @Test
    public void shouldReturn404ForCorporateSurcharge_whenGatewayAccountIsNonExistent() throws Exception {
        String gatewayAccountId = "1000023";
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", "corporate_credit_card_surcharge_amount",
                "value", 100));
        givenSetup()
                .body(payload)
                .patch("/v1/api/accounts/" + gatewayAccountId)
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    private String createAGatewayAccountFor(String provider, String desc, String id) {
        return extractGatewayAccountId(createAGatewayAccountFor(testContext.getPort(), provider, desc, id));
    }
}
