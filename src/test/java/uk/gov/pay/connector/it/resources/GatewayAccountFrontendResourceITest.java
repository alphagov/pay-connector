package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.response.ValidatableResponse;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.lang.String.join;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class GatewayAccountFrontendResourceITest extends GatewayAccountResourceTestBase {
    private static final String ACCOUNTS_CARD_TYPE_FRONTEND_URL = "v1/frontend/accounts/{accountId}/card-types";

    static class GatewayAccountPayload {
        String userName;
        String password;
        String merchantId;
        String serviceName;

        static GatewayAccountPayload createDefault() {
            return new GatewayAccountPayload()
                    .withUsername("a-username")
                    .withPassword("a-password")
                    .withMerchantId("a-merchant-id")
                    .withServiceName("a-service-name");
        }

        public GatewayAccountPayload withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public GatewayAccountPayload withUsername(String userName) {
            this.userName = userName;
            return this;
        }

        public GatewayAccountPayload withPassword(String password) {
            this.password = password;
            return this;
        }

        public GatewayAccountPayload withMerchantId(String merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public Map<String, String> getCredentials() {
            HashMap<String, String> credentials = new HashMap<>();

            if (this.userName != null) {
                credentials.put("username", userName);
            }

            if (this.password != null) {
                credentials.put("password", password);
            }

            if (this.merchantId != null) {
                credentials.put("merchant_id", merchantId);
            }

            return credentials;
        }

        Map<String, Object> buildCredentialsPayload() {
            return ImmutableMap.of("credentials", getCredentials());
        }

        Map buildServiceNamePayload() {
            return ImmutableMap.of("service_name", serviceName);
        }

        String getServiceName() {
            return serviceName;
        }

        String getPassword() {
            return password;
        }

        String getUserName() {
            return userName;
        }

        String getMerchantId() {
            return merchantId;
        }

    }

    private Gson gson = new Gson();

    @Test
    public void shouldGetGatewayAccountForExistingAccount() {
        String accountId = createAGatewayAccountFor("worldpay");
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        app.getDatabaseTestHelper().updateCredentialsFor(Long.valueOf(accountId), gson.toJson(gatewayAccountPayload.getCredentials()));
        app.getDatabaseTestHelper().updateServiceNameFor(Long.valueOf(accountId), gatewayAccountPayload.getServiceName());
        app.getDatabaseTestHelper().updateCorporateCreditCardSurchargeAmountFor(Long.valueOf(accountId), 250);
        app.getDatabaseTestHelper().updateCorporateDebitCardSurchargeAmountFor(Long.valueOf(accountId), 50);

        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_URL + accountId)
                .then()
                .statusCode(200)
                .body("payment_provider", is("worldpay"))
                .body("gateway_account_id", is(Integer.parseInt(accountId)))
                .body("credentials.username", is(gatewayAccountPayload.getUserName()))
                .body("credentials.password", is(nullValue()))
                .body("credentials.merchant_id", is(gatewayAccountPayload.getMerchantId()))
                .body("description", is(nullValue()))
                .body("analytics_id", is(nullValue()))
                .body("service_name", is(gatewayAccountPayload.getServiceName()))
                .body("corporate_credit_card_surcharge_amount", is(250))
                .body("corporate_debit_card_surcharge_amount", is(50));
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
        app.getDatabaseTestHelper().updateCredentialsFor(Long.valueOf(accountId), gson.toJson(gatewayAccountPayload.getCredentials()));
        app.getDatabaseTestHelper().addNotificationCredentialsFor(Long.valueOf(accountId), "bob", "bobssecret");

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
        app.getDatabaseTestHelper().updateCredentialsFor(Long.valueOf(accountId), gson.toJson(gatewayAccountPayload.getCredentials()));
        app.getDatabaseTestHelper().updateServiceNameFor(Long.valueOf(accountId), gatewayAccountPayload.getServiceName());
        app.getDatabaseTestHelper().updateCorporateCreditCardSurchargeAmountFor(Long.valueOf(accountId), 250);
        app.getDatabaseTestHelper().updateCorporateDebitCardSurchargeAmountFor(Long.valueOf(accountId), 50);

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
                .body("accounts[0].corporate_debit_card_surcharge_amount", is(50));
    }

    private void validateNon3dsCardType(ValidatableResponse response, String brand, String label, String... type) {
        response
                .body(format("card_types.find { it.brand == '%s' }.id", brand), is(notNullValue()))
                .body(format("card_types.find { it.brand == '%s' }.label", brand), is(label))
                .body(format("card_types.find { it.brand == '%s' }.requires3ds", brand), is(false))
                .body(format("card_types.findAll { it.brand == '%s' }.type", brand), Matchers.hasItems(type));
    }

    @Test
    public void shouldAcceptAllCardTypesNotRequiring3DSForNewlyCreatedAccountAs3dsIsDisabledByDefault() {
        String accountId = createAGatewayAccountFor("worldpay");
        String frontendCardTypeUrl = ACCOUNTS_CARD_TYPE_FRONTEND_URL.replace("{accountId}", accountId);
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        app.getDatabaseTestHelper().updateCredentialsFor(Long.valueOf(accountId), gson.toJson(gatewayAccountPayload.getCredentials()));
        app.getDatabaseTestHelper().updateServiceNameFor(Long.valueOf(accountId), gatewayAccountPayload.getServiceName());
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
                .body("message", is("Account with id '12345' not found"));

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

        Map<String, String> currentCredentials = app.getDatabaseTestHelper().getAccountCredentials(Long.valueOf(accountId));
        assertThat(currentCredentials, is(gatewayAccountPayload.getCredentials()));
    }

    @Test
    public void updateCredentials_shouldUpdateGatewayAccountCredentialsForASmartpayAccountSuccessfully() {
        String accountId = createAGatewayAccountFor("smartpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault();

        updateGatewayAccountCredentialsWith(accountId, gatewayAccountPayload.buildCredentialsPayload())
                .then()
                .statusCode(200);

        Map<String, String> currentCredentials = app.getDatabaseTestHelper().getAccountCredentials(Long.valueOf(accountId));
        assertThat(currentCredentials, is(gatewayAccountPayload.getCredentials()));
    }

    @Test
    public void updateCredentials_shouldUpdateGatewayAccountCredentialsWithSpecialCharactersInUserNamesAndPassword() throws Exception {
        String accountId = createAGatewayAccountFor("smartpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault()
                .withUsername("someone@some{[]where&^%>?\\/")
                .withPassword("56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w");

        updateGatewayAccountCredentialsWith(accountId, gatewayAccountPayload.buildCredentialsPayload())
                .then()
                .statusCode(200);

        Map<String, String> currentCredentials = app.getDatabaseTestHelper().getAccountCredentials(Long.valueOf(accountId));
        assertThat(currentCredentials, is(gatewayAccountPayload.getCredentials()));
    }

    @Test
    public void updateCredentials_shouldNotUpdateGatewayAccountCredentialsIfMissingCredentials() {
        String accountId = createAGatewayAccountFor("worldpay");

        updateGatewayAccountCredentialsWith(accountId, new HashMap<>())
                .then()
                .statusCode(400)
                .body("message", is("Field(s) missing: [credentials]"));
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
                .body("message", is("Field(s) missing: [password]"));
        //TODO: For backward compatibility. Enable once the selfservice/e2e/acceptest changes are done
//                .body("message", is("Field(s) missing: [password, merchant_id]"));
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
                .body("message", is("Field(s) missing: [merchant_id]"));
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
                .body("message", is("Field(s) missing: [password, merchant_id]"));
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
                .body("message", is("The gateway account id '111111111' does not exist"));
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

        String currentServiceName = app.getDatabaseTestHelper().getAccountServiceName(Long.valueOf(accountId));
        assertThat(currentServiceName, is(gatewayAccountPayload.getServiceName()));
    }

    @Test
    public void updateServiceName_shouldNotUpdateGatewayAccountServiceNameIfMissingServiceName() {
        String accountId = createAGatewayAccountFor("worldpay");

        updateGatewayAccountServiceNameWith(accountId, new HashMap<>())
                .then()
                .statusCode(400)
                .body("message", is("Field(s) missing: [service_name]"));
    }

    @Test
    public void updateServiceName_shouldFailUpdatingIfInvalidServiceNameLength() {
        String accountId = createAGatewayAccountFor("worldpay");

        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault()
                .withServiceName("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

        updateGatewayAccountServiceNameWith(accountId, gatewayAccountPayload.buildServiceNamePayload())
                .then()
                .statusCode(400)
                .body("message", is("Field(s) are too big: [service_name]"));
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
                .body("message", is("The gateway account id '111111111' does not exist"));
    }

    @Test
    public void updateAcceptedCardTypes_shouldNotUpdateGatewayAccountIfCardTypesFieldIsMissing() {
        DatabaseFixtures.TestAccount accountRecord = createAccountRecord();

        String body = "{}";
        updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), body)
                .then()
                .statusCode(400)
                .body("message", is("Field(s) missing: [card_types]"));
    }

    @Test
    public void updateAcceptedCardTypes_shouldNotUpdateGatewayAccountIfCardTypeIdIsNotExisting() {
        DatabaseFixtures.TestAccount accountRecord = createAccountRecord();

        String nonExistingCardTypeId = "9f10a66c-122b-4d08-bcd2-13cb83d1a284";

        DatabaseFixtures.TestCardType aVisaDebitCardTypeWithNonExistingId =
                databaseFixtures.aVisaDebitCardType().withCardTypeId(UUID.fromString(nonExistingCardTypeId));

        updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), buildAcceptedCardTypesBody(aVisaDebitCardTypeWithNonExistingId))
                .then()
                .statusCode(400)
                .body("message", is(format("Accepted Card Type(s) referenced by id(s) '%s' not found", aVisaDebitCardTypeWithNonExistingId.getId())));
    }

    @Test
    public void updateAcceptedCardTypes_shouldUpdateGatewayAccountToAcceptCardTypes() {
        DatabaseFixtures.TestCardType mastercardCreditCardTypeRecord = createMastercardCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaCreditCardTypeRecord = createVisaCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaDebitCardTypeRecord = createVisaDebitCardTypeRecord();
        DatabaseFixtures.TestAccount accountRecord = createAccountRecord(mastercardCreditCardTypeRecord, visaCreditCardTypeRecord);

        String body = buildAcceptedCardTypesBody(mastercardCreditCardTypeRecord, visaDebitCardTypeRecord);
        updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), body)
                .then()
                .statusCode(200);

        List<Map<String, Object>> acceptedCardTypes =
                app.getDatabaseTestHelper().getAcceptedCardTypesByAccountId(accountRecord.getAccountId());

        MatcherAssert.assertThat(acceptedCardTypes, containsInAnyOrder(
                allOf(
                        hasEntry("label", mastercardCreditCardTypeRecord.getLabel()),
                        hasEntry("type", mastercardCreditCardTypeRecord.getType().toString()),
                        hasEntry("brand", mastercardCreditCardTypeRecord.getBrand())
                ), allOf(
                        hasEntry("label", visaDebitCardTypeRecord.getLabel()),
                        hasEntry("type", visaDebitCardTypeRecord.getType().toString()),
                        hasEntry("brand", visaDebitCardTypeRecord.getBrand())
                )));
    }

    @Test
    public void updateAcceptedCardTypes_shouldUpdateGatewayAccountToAcceptNoCardTypes() {
        DatabaseFixtures.TestCardType mastercardCreditCardTypeRecord = createMastercardCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaCreditCardTypeRecord = createVisaCreditCardTypeRecord();
        DatabaseFixtures.TestAccount accountRecord = createAccountRecord(mastercardCreditCardTypeRecord, visaCreditCardTypeRecord);

        updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), buildAcceptedCardTypesBody())
                .then()
                .statusCode(200);

        List<Map<String, Object>> acceptedCardTypes =
                app.getDatabaseTestHelper().getAcceptedCardTypesByAccountId(accountRecord.getAccountId());

        assertTrue(acceptedCardTypes.size() == 0);
    }

    @Test
    public void updateAcceptedCardTypes_shouldFailWhenCardTypeRequires3ds_whenGatewayAccountDoesNotRequire3ds() {

        DatabaseFixtures.TestCardType maestroCardType = databaseFixtures.aMaestroDebitCardType().insert();

        DatabaseFixtures.TestAccount gatewayAccount = databaseFixtures
                .aTestAccount()
                .insert();

        updateGatewayAccountCardTypesWith(gatewayAccount.getAccountId(), buildAcceptedCardTypesBody(maestroCardType))
                .then()
                .statusCode(409);

        List<Map<String, Object>> acceptedCardTypes =
                app.getDatabaseTestHelper().getAcceptedCardTypesByAccountId(gatewayAccount.getAccountId());

        assertTrue(acceptedCardTypes.size() == 0);
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
        return givenSetup().accept(JSON)
                .body(body)
                .post(ACCOUNTS_FRONTEND_URL + accountId + "/card-types");
    }

    private String buildAcceptedCardTypesBody(DatabaseFixtures.TestCardType... cardTypes) {
        List<String> cardTypeIds = Arrays.asList(cardTypes).stream()
                .map(cardType -> "\"" + cardType.getId().toString() + "\"")
                .collect(Collectors.toList());

        return format("{\"card_types\": [%s]}", join(",", cardTypeIds));
    }

}
