package uk.gov.pay.connector.it.resources;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.lang.String.join;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccountFrontendResourceITest extends GatewayAccountResourceTestBase {

    private static final String ACCOUNTS_CARD_TYPE_FRONTEND_URL = "v1/frontend/accounts/{accountId}/card-types";

    private Gson gson = new Gson();

    @Test
    public void shouldGetGatewayAccountForExistingAccount() {
        String accountId = createAGatewayAccountFor("worldpay");
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        databaseTestHelper.updateCredentialsFor(Long.valueOf(accountId), gson.toJson(gatewayAccountPayload.getCredentials()));
        databaseTestHelper.updateServiceNameFor(Long.valueOf(accountId), gatewayAccountPayload.getServiceName());
        databaseTestHelper.updateCorporateCreditCardSurchargeAmountFor(Long.valueOf(accountId), 250);
        databaseTestHelper.updateCorporateDebitCardSurchargeAmountFor(Long.valueOf(accountId), 50);
        databaseTestHelper.allowApplePay(Long.valueOf(accountId));
        databaseTestHelper.allowZeroAmount(Long.valueOf(accountId));

        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_URL + accountId)
                .then()
                .statusCode(200)
                .body("payment_provider", is("worldpay"))
                .body("gateway_account_id", is(Integer.parseInt(accountId)))
                .body("credentials.username", is(gatewayAccountPayload.getUserName()))
                .body("credentials.password", is(nullValue()))
                .body("credentials.merchant_id", is(gatewayAccountPayload.getMerchantId()))
                .body("email_collection_mode", is("MANDATORY"))
                .body("email_notifications.PAYMENT_CONFIRMED.template_body", is(nullValue()))
                .body("email_notifications.PAYMENT_CONFIRMED.enabled", is(true))
                .body("email_notifications.REFUND_ISSUED.template_body", is(nullValue()))
                .body("email_notifications.REFUND_ISSUED.enabled", is(true))
                .body("description", is(nullValue()))
                .body("analytics_id", is(nullValue()))
                .body("service_name", is(gatewayAccountPayload.getServiceName()))
                .body("corporate_credit_card_surcharge_amount", is(250))
                .body("corporate_debit_card_surcharge_amount", is(50))
                .body("allow_apple_pay", is(true))
                .body("allow_google_pay", is(false))
                .body("allow_zero_amount", is(true));
    }

    @Test
    public void shouldGetGatewayAccountWithDescriptionAndAnalyticsId() {
        String accountId = createAGatewayAccountFor("worldpay", "a-description", "an-analytics-id");
        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_URL + accountId)
                .then()
                .statusCode(200)
                .body("payment_provider", is("worldpay"))
                .body("gateway_account_id", is(Integer.parseInt(accountId)))
                .body("analytics_id", is("an-analytics-id"))
                .body("description", is("a-description"));
    }

    @Test
    public void shouldGetNotificationCredentialsWhenTheyExistForGatewayAccount() {
        String accountId = createAGatewayAccountFor("smartpay");
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault();
        databaseTestHelper.updateCredentialsFor(Long.valueOf(accountId), gson.toJson(gatewayAccountPayload.getCredentials()));
        databaseTestHelper.addNotificationCredentialsFor(Long.valueOf(accountId), "bob", "bobssecret");

        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_URL + accountId)
                .then()
                .statusCode(200)
                .body("payment_provider", is("smartpay"))
                .body("gateway_account_id", is(Integer.parseInt(accountId)))
                .body("notificationCredentials.userName", is("bob"))
                .body("notificationCredentials.password", is(nullValue()));
    }

    @Test
    public void shouldFilterGetGatewayAccountForExistingAccount() {
        String accountId = createAGatewayAccountFor("worldpay");
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        databaseTestHelper.updateCredentialsFor(Long.valueOf(accountId), gson.toJson(gatewayAccountPayload.getCredentials()));
        databaseTestHelper.updateServiceNameFor(Long.valueOf(accountId), gatewayAccountPayload.getServiceName());
        databaseTestHelper.updateCorporateCreditCardSurchargeAmountFor(Long.valueOf(accountId), 250);
        databaseTestHelper.updateCorporateDebitCardSurchargeAmountFor(Long.valueOf(accountId), 50);

        givenSetup().accept(JSON)
                .get("/v1/frontend/accounts?accountIds=" + accountId)
                .then()
                .statusCode(200)
                .body("accounts", hasSize(1))
                .body("accounts[0].payment_provider", is("worldpay"))
                .body("accounts[0].gateway_account_id", is(Integer.parseInt(accountId)))
                .body("accounts[0].description", is(nullValue()))
                .body("accounts[0].analytics_id", is(nullValue()))
                .body("accounts[0].service_name", is(gatewayAccountPayload.getServiceName()))
                .body("accounts[0].corporate_credit_card_surcharge_amount", is(250))
                .body("accounts[0].corporate_debit_card_surcharge_amount", is(50))
                .body("accounts[0].allow_apple_pay", is(false))
                .body("accounts[0].allow_google_pay", is(false));
    }

    private void validateNon3dsCardType(ValidatableResponse response, String brand, String label, String... type) {
        response
                .body(format("card_types.find { it.brand == '%s' }.id", brand), is(notNullValue()))
                .body(format("card_types.find { it.brand == '%s' }.label", brand), is(label))
                .body(format("card_types.find { it.brand == '%s' }.requires3ds", brand), is(false))
                .body(format("card_types.findAll { it.brand == '%s' }.type", brand), hasItems(type));
    }

    @Test
    public void shouldAcceptAllCardTypesNotRequiring3DSForNewlyCreatedAccountAs3dsIsDisabledByDefault() {
        String accountId = createAGatewayAccountFor("worldpay");
        String frontendCardTypeUrl = ACCOUNTS_CARD_TYPE_FRONTEND_URL.replace("{accountId}", accountId);
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        databaseTestHelper.updateCredentialsFor(Long.valueOf(accountId), gson.toJson(gatewayAccountPayload.getCredentials()));
        databaseTestHelper.updateServiceNameFor(Long.valueOf(accountId), gatewayAccountPayload.getServiceName());
        ValidatableResponse response = givenSetup().accept(JSON)
                .get(frontendCardTypeUrl)
                .then()
                .statusCode(200)
                .body("containsKey('card_types')", is(true))
                .body("card_types", hasSize(9));

        validateNon3dsCardType(response, "visa", "Visa", "DEBIT", "CREDIT");
        validateNon3dsCardType(response, "master-card", "Mastercard", "DEBIT", "CREDIT");
        validateNon3dsCardType(response, "american-express", "American Express", "CREDIT");
        validateNon3dsCardType(response, "diners-club", "Diners Club", "CREDIT");
        validateNon3dsCardType(response, "discover", "Discover", "CREDIT");
        validateNon3dsCardType(response, "jcb", "Jcb", "CREDIT");
        validateNon3dsCardType(response, "unionpay", "Union Pay", "CREDIT");
    }

    @Test
    public void shouldReturn404IfGatewayAccountDoesNotExist() {
        String nonExistingGatewayAccount = "12345";
        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_URL + nonExistingGatewayAccount)
                .then()
                .statusCode(404)
                .body("message", contains("Account with id '12345' not found"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

    }

    @Test
    public void shouldReturn404IfGatewayAccountIsNotNumeric() {
        String nonNumericGatewayAccount = "ABC";
        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_URL + nonNumericGatewayAccount)
                .then()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void updateCredentials_shouldUpdateGatewayAccountCredentialsForAWorldpayAccountSuccessfully() {
        String accountId = createAGatewayAccountFor("worldpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault()
                .withMerchantId("a-merchant-id");

        updateGatewayAccountCredentialsWith(accountId, gatewayAccountPayload.buildCredentialsPayload())
                .then()
                .statusCode(200);

        Map<String, String> currentCredentials = databaseTestHelper.getAccountCredentials(Long.valueOf(accountId));
        assertThat(currentCredentials, is(gatewayAccountPayload.getCredentials()));
    }

    @Test
    public void updateCredentials_shouldUpdateGatewayAccountCredentialsForASmartpayAccountSuccessfully() {
        String accountId = createAGatewayAccountFor("smartpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault();

        updateGatewayAccountCredentialsWith(accountId, gatewayAccountPayload.buildCredentialsPayload())
                .then()
                .statusCode(200);

        Map<String, String> currentCredentials = databaseTestHelper.getAccountCredentials(Long.valueOf(accountId));
        assertThat(currentCredentials, is(gatewayAccountPayload.getCredentials()));
    }

    @Test
    public void updateCredentials_shouldUpdateGatewayAccountCredentialsWithSpecialCharactersInUserNamesAndPassword() {
        String accountId = createAGatewayAccountFor("smartpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault()
                .withUsername("someone@some{[]where&^%>?\\/")
                .withPassword("56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w");

        updateGatewayAccountCredentialsWith(accountId, gatewayAccountPayload.buildCredentialsPayload())
                .then()
                .statusCode(200);

        Map<String, String> currentCredentials = databaseTestHelper.getAccountCredentials(Long.valueOf(accountId));
        assertThat(currentCredentials, is(gatewayAccountPayload.getCredentials()));
    }

    @Test
    public void updateCredentials_shouldNotUpdateGatewayAccountCredentialsIfMissingCredentials() {
        String accountId = createAGatewayAccountFor("worldpay");

        updateGatewayAccountCredentialsWith(accountId, new HashMap<>())
                .then()
                .statusCode(400)
                .body("message", contains("Field(s) missing: [credentials]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void updateCredentials_shouldNotUpdateGatewayAccountCredentialsIfAccountWith2RequiredCredentialsMisses1Credential() {
        String accountId = createAGatewayAccountFor("smartpay");

        Map<String, Object> credentials = new GatewayAccountPayload()
                .withUsername("a-username")
                .withServiceName("a-service-name")
                .buildCredentialsPayload();

        updateGatewayAccountCredentialsWith(accountId, credentials)
                .then()
                .statusCode(400)
                .body("message", contains("Field(s) missing: [password]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void updateCredentials_shouldNotUpdateGatewayAccountCredentialsIfAccountWith3RequiredCredentialsMisses1Credential() {
        String accountId = createAGatewayAccountFor("worldpay");

        Map<String, Object> credentials = new GatewayAccountPayload()
                .withUsername("a-username")
                .withServiceName("a-service-name")
                .withPassword("a-password")
                .buildCredentialsPayload();

        updateGatewayAccountCredentialsWith(accountId, credentials)
                .then()
                .statusCode(400)
                .body("message", contains("Field(s) missing: [merchant_id]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void updateCredentials_shouldNotUpdateGatewayAccountCredentialsIfAccountWith3RequiredCredentialsMisses2Credentials() {
        String accountId = createAGatewayAccountFor("worldpay");

        Map<String, Object> credentials = new GatewayAccountPayload()
                .withUsername("a-username")
                .withServiceName("a-service-name")
                .buildCredentialsPayload();

        updateGatewayAccountCredentialsWith(accountId, credentials)
                .then()
                .statusCode(400)
                .body("message", contains("Field(s) missing: [password, merchant_id]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void updateCredentials_shouldNotUpdateGatewayAccountCredentialsIfAccountIdIsNotNumeric() {
        Map<String, Object> credentials = GatewayAccountPayload.createDefault().buildCredentialsPayload();
        updateGatewayAccountCredentialsWith("NO_NUMERIC_ACCOUNT_ID", credentials)
                .then()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void updateCredentials_shouldNotUpdateGatewayAccountCredentialsIfAccountIdDoesNotExist() {
        String nonExistingAccountId = "111111111";
        createAGatewayAccountFor("smartpay");

        Map<String, Object> credentials = GatewayAccountPayload.createDefault().buildCredentialsPayload();
        updateGatewayAccountCredentialsWith(nonExistingAccountId, credentials)
                .then()
                .statusCode(404)
                .body("message", contains("The gateway account id '111111111' does not exist"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void updateServiceName_shouldUpdateGatewayAccountServiceNameSuccessfully() {
        String accountId = createAGatewayAccountFor("smartpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault();

        givenSetup().accept(JSON)
                .body(gatewayAccountPayload.buildServiceNamePayload())
                .patch(ACCOUNTS_FRONTEND_URL + accountId + "/servicename")
                .then()
                .statusCode(200);

        String currentServiceName = databaseTestHelper.getAccountServiceName(Long.valueOf(accountId));
        assertThat(currentServiceName, is(gatewayAccountPayload.getServiceName()));
    }

    @Test
    public void updateServiceName_shouldNotUpdateGatewayAccountServiceNameIfMissingServiceName() {
        String accountId = createAGatewayAccountFor("worldpay");

        updateGatewayAccountServiceNameWith(accountId, new HashMap<>())
                .then()
                .statusCode(400)
                .body("message", contains("Field(s) missing: [service_name]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void updateServiceName_shouldFailUpdatingIfInvalidServiceNameLength() {
        String accountId = createAGatewayAccountFor("worldpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault()
                .withServiceName("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

        updateGatewayAccountServiceNameWith(accountId, gatewayAccountPayload.buildServiceNamePayload())
                .then()
                .statusCode(400)
                .body("message", contains("Field(s) are too big: [service_name]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void updateServiceName_shouldNotUpdateGatewayAccountServiceNameIfAccountIdIsNotNumeric() {
        Map<String, String> serviceNamePayload = GatewayAccountPayload.createDefault().buildServiceNamePayload();
        updateGatewayAccountServiceNameWith("NO_NUMERIC_ACCOUNT_ID", serviceNamePayload)
                .then()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void updateServiceName_shouldNotUpdateGatewayAccountServiceNameIfAccountIdDoesNotExist() {
        String nonExistingAccountId = "111111111";
        createAGatewayAccountFor("smartpay");

        Map<String, String> serviceNamePayload = GatewayAccountPayload.createDefault().buildServiceNamePayload();
        updateGatewayAccountServiceNameWith(nonExistingAccountId, serviceNamePayload)
                .then()
                .statusCode(404)
                .body("message", contains("The gateway account id '111111111' does not exist"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void updateAcceptedCardTypes_shouldNotUpdateGatewayAccountIfCardTypesFieldIsMissing() {
        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards();

        String body = "{}";
        updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), body)
                .then()
                .statusCode(400)
                .body("message", contains("Field(s) missing: [card_types]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void updateAcceptedCardTypes_shouldNotUpdateGatewayAccountIfCardTypeIdIsNotExisting() {
        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards();

        String nonExistingCardTypeId = "9f10a66c-122b-4d08-bcd2-13cb83d1a284";
        UUID[] cardTypes = {UUID.fromString(nonExistingCardTypeId)};
        Map<String, UUID[]> payload = new HashMap<>();
        payload.put("card_types", cardTypes);

        final String body = gson.toJson(payload);
        final String expectedMessage = format("Accepted Card Type(s) referenced by id(s) '%s' not found", nonExistingCardTypeId);
        updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), body)
                .then()
                .statusCode(400)
                .body("message", contains(expectedMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void updateAcceptedCardTypes_shouldUpdateGatewayAccountToAcceptCardTypes() {
        CardTypeEntity mastercardCreditCard = databaseTestHelper.getMastercardCreditCard();

        CardTypeEntity visaCreditCard = databaseTestHelper.getVisaCreditCard();

        CardTypeEntity visaDebitCard = databaseTestHelper.getVisaDebitCard();

        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards(mastercardCreditCard, visaCreditCard);
        String body = buildAcceptedCardTypesBody(mastercardCreditCard, visaDebitCard);
        updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), body)
                .then()
                .statusCode(200);

        List<Map<String, Object>> acceptedCardTypes =
                databaseTestHelper.getAcceptedCardTypesByAccountId(accountRecord.getAccountId());

        assertThat(acceptedCardTypes, containsInAnyOrder(
                allOf(
                        hasEntry("label", mastercardCreditCard.getLabel()),
                        hasEntry("type", mastercardCreditCard.getType().toString()),
                        hasEntry("brand", mastercardCreditCard.getBrand())
                ), allOf(
                        hasEntry("label", visaDebitCard.getLabel()),
                        hasEntry("type", visaDebitCard.getType().toString()),
                        hasEntry("brand", visaDebitCard.getBrand())
                )));
    }

    @Test
    public void updateAcceptedCardTypes_shouldUpdateGatewayAccountToAcceptNoCardTypes() {
        CardTypeEntity mastercardCreditCardTypeRecord = databaseTestHelper.getMastercardCreditCard();
        CardTypeEntity visaCreditCardTypeRecord = databaseTestHelper.getVisaCreditCard();

        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards(
                mastercardCreditCardTypeRecord, visaCreditCardTypeRecord);

        updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), buildAcceptedCardTypesBody())
                .then()
                .statusCode(200);

        List<Map<String, Object>> acceptedCardTypes =
                databaseTestHelper.getAcceptedCardTypesByAccountId(accountRecord.getAccountId());

        assertEquals(0, acceptedCardTypes.size());
    }

    @Test
    public void updateAcceptedCardTypes_shouldFailWhenCardTypeRequires3ds_whenGatewayAccountDoesNotRequire3ds() {

        CardTypeEntity maestroCard = databaseTestHelper.getMaestroCard();

        DatabaseFixtures.TestAccount gatewayAccount = createAccountRecordWithCards();

        updateGatewayAccountCardTypesWith(gatewayAccount.getAccountId(), buildAcceptedCardTypesBody(maestroCard))
                .then()
                .statusCode(409);

        List<Map<String, Object>> acceptedCardTypes =
                databaseTestHelper.getAcceptedCardTypesByAccountId(gatewayAccount.getAccountId());

        assertEquals(0, acceptedCardTypes.size());
    }

    private Response updateGatewayAccountCredentialsWith(String accountId, Map<String, Object> credentials) {
        return givenSetup().accept(JSON)
                .body(credentials)
                .patch(ACCOUNTS_FRONTEND_URL + accountId + "/credentials");
    }

    private Response updateGatewayAccountServiceNameWith(String accountId, Map<String, String> serviceName) {
        return givenSetup().accept(JSON)
                .body(serviceName)
                .patch(ACCOUNTS_FRONTEND_URL + accountId + "/servicename");
    }

    private Response updateGatewayAccountCardTypesWith(long accountId, String body) {
        return givenSetup()
                .contentType(ContentType.JSON)
                .accept(JSON)
                .body(body)
                .post(ACCOUNTS_FRONTEND_URL + accountId + "/card-types");
    }

    private String buildAcceptedCardTypesBody(CardTypeEntity... cardTypes) {
        List<String> cardTypeIds = Arrays.stream(cardTypes)
                .map(cardType -> "\"" + cardType.getId().toString() + "\"")
                .collect(Collectors.toList());

        return format("{\"card_types\": [%s]}", join(",", cardTypeIds));
    }

    private String createAGatewayAccountFor(String provider, String desc, String id) {
        return extractGatewayAccountId(createAGatewayAccountFor(testContext.getPort(), provider, desc, id));
    }

    private DatabaseFixtures.TestAccount createAccountRecordWithCards(CardTypeEntity... cardTypes) {
        return databaseFixtures
                .aTestAccount()
                .withCardTypeEntities(Arrays.asList(cardTypes))
                .insert();
    }
}
