package uk.gov.pay.connector.it.resources;

import com.google.gson.Gson;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.lang.String.join;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.ACCOUNTS_API_URL;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.ACCOUNTS_FRONTEND_URL;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.ACCOUNT_FRONTEND_EXTERNAL_ID_URL;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.CreateGatewayAccountPayloadBuilder.aCreateGatewayAccountPayloadBuilder;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountFrontendResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    public static GatewayAccountResourceITHelpers testHelpers = new GatewayAccountResourceITHelpers(app.getLocalPort());
    
    private static final String ACCOUNTS_CARD_TYPE_FRONTEND_URL = "v1/frontend/accounts/{accountId}/card-types";
    private static final String ACCOUNTS_CARD_TYPE_BY_SERVICE_ID_AND_ACCOUNT_TYPE_FRONTEND_URL = "v1/frontend/service/{serviceId}/account/{accountType}/card-types";

    private final Gson gson = new Gson();

    @Test
    void shouldGetGatewayAccountByExternalId() {
        DatabaseFixtures.TestAccount gatewayAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of(
                        "username", "a-username",
                        "password", "a-password",
                        "merchant_id", "a-merchant-id"))
                .withServiceName("A Service Name")
                .withCorporateCreditCardSurchargeAmount(250L)
                .withCorporateDebitCardSurchargeAmount(50L)
                .withIntegrationVersion3ds(1)
                .insert();
        long accountId = gatewayAccount.getAccountId();

        app.givenSetup().accept(JSON)
                .get(ACCOUNT_FRONTEND_EXTERNAL_ID_URL + gatewayAccount.getExternalId())
                .then()
                .statusCode(200)
                .body("payment_provider", is("worldpay"))
                .body("gateway_account_id", is((int) accountId))
                .body("gateway_account_id", is(notNullValue()))
                .body("email_collection_mode", is("OPTIONAL"))
                .body("email_notifications.PAYMENT_CONFIRMED.template_body", not(nullValue()))
                .body("email_notifications.PAYMENT_CONFIRMED.enabled", is(true))
                .body("email_notifications.REFUND_ISSUED.template_body", not(nullValue()))
                .body("email_notifications.REFUND_ISSUED.enabled", is(true))
                .body("description", is(gatewayAccount.getDescription()))
                .body("analytics_id", is(gatewayAccount.getAnalyticsId()))
                .body("service_name", is("A Service Name"))
                .body("corporate_credit_card_surcharge_amount", is(250))
                .body("corporate_debit_card_surcharge_amount", is(50))
                .body("allow_apple_pay", is(false))
                .body("allow_google_pay", is(false))
                .body("allow_zero_amount", is(false))
                .body("integration_version_3ds", is(1))
                .body("block_prepaid_cards", is(false))
                .body("allow_moto", is(false))
                .body("allow_telephone_payment_notifications", is(false))
                .body("worldpay_3ds_flex", nullValue())
                .body("gateway_account_credentials[0]", hasKey("gateway_account_credential_id"));
    }
    
    @Test
    void shouldReturnGatewayAccountByExternalIdWith3dsFlexCredentials_whenGatewayAccountHasCreds() {
        DatabaseFixtures.TestAccount gatewayAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withPaymentProvider("worldpay")
                .insert();
        app.getDatabaseTestHelper().insertWorldpay3dsFlexCredential(gatewayAccount.getAccountId(), "macKey", "issuer", "org_unit_id", 2L);
        app.givenSetup()
                .get(ACCOUNT_FRONTEND_EXTERNAL_ID_URL + gatewayAccount.getExternalId())
                .then()
                .statusCode(200)
                .body("$", hasKey("worldpay_3ds_flex"))
                .body("worldpay_3ds_flex.issuer", is("issuer"))
                .body("worldpay_3ds_flex.organisational_unit_id", is("org_unit_id"))
                .body("worldpay_3ds_flex", not(hasKey("jwt_mac_key")))
                .body("worldpay_3ds_flex", not(hasKey("version")))
                .body("worldpay_3ds_flex", not(hasKey("gateway_account_id")))
                .body("worldpay_3ds_flex.exemption_engine_enabled", is(false))
                .body("gateway_account_credentials[0]", hasKey("gateway_account_credential_id"));
    }

    private void validateNon3dsCardType(ValidatableResponse response, String brand, String label, String... type) {
        response
                .body(format("card_types.find { it.brand == '%s' }.id", brand), is(notNullValue()))
                .body(format("card_types.find { it.brand == '%s' }.label", brand), is(label))
                .body(format("card_types.find { it.brand == '%s' }.requires3ds", brand), is(false))
                .body(format("card_types.findAll { it.brand == '%s' }.type", brand), hasItems(type));
    }

    @Test
    void shouldAcceptAllCardTypesNotRequiring3DSForNewlyCreatedAccountAs3dsIsDisabledByDefault() {
        String accountId = testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder().withProvider("worldpay").build());
        String frontendCardTypeUrl = ACCOUNTS_CARD_TYPE_FRONTEND_URL.replace("{accountId}", accountId);
        ValidatableResponse response = app.givenSetup().accept(JSON)
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
    void shouldGetCardTypesByServiceIdAndAccountType() {
        testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().withServiceId("a-service-id").build());
        String frontendCardTypeByServiceIdAndAccountTypeUrl = ACCOUNTS_CARD_TYPE_BY_SERVICE_ID_AND_ACCOUNT_TYPE_FRONTEND_URL
                .replace("{serviceId}", "a-service-id")
                .replace("{accountType}", GatewayAccountType.TEST.name());
        ValidatableResponse response = app.givenSetup().accept(JSON)
                .get(frontendCardTypeByServiceIdAndAccountTypeUrl)
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
    void shouldReturn404IfGatewayAccountIsNotNumeric() {
        String nonNumericGatewayAccount = "ABC";
        app.givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_URL + nonNumericGatewayAccount)
                .then()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Nested
    class UpdateServiceNameByServiceId {
        private Map<String, String> gatewayAccountRequest = Map.of();
        private Map<String, String> testGatewayAccountRequest;
        private Map<String, String> liveGatewayAccountRequest;
        
        private String serviceId;
        
        @BeforeEach
        void before() {
            serviceId = RandomIdGenerator.newId();
            testGatewayAccountRequest = Map.of(
                    "payment_provider", "stripe",
                    "service_id", serviceId,
                    "service_name", "Service Name",
                    "type", "test");
            liveGatewayAccountRequest = Map.of(
                    "payment_provider", "stripe",
                    "service_id", serviceId,
                    "service_name", "Service Name",
                    "type", "live");
        }
        
        @Test
        void shouldUpdateServiceNameInTestAndLive_andReturn200() {
            // Create test account
            app.givenSetup()
                    .body(toJson(testGatewayAccountRequest))        
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .body("service_name", is("Service Name"));

            // Create live account
            app.givenSetup()
                    .body(toJson(liveGatewayAccountRequest))
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .body("service_name", is("Service Name"));

            // attempt to update service name
            app.givenSetup().accept(JSON)
                    .body(Map.of("service_name", "New Service Name"))
                    .patch(format("/v1/frontend/service/%s/servicename", serviceId))
                    .then()
                    .statusCode(200);

            // verify service name updated in test account 
            given().port(app.getLocalPort())
                    .contentType(JSON)
                    .accept(JSON)
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(200)
                    .body("service_name", is("New Service Name"));

            // verify service name updated in live account 
            given().port(app.getLocalPort())
                    .contentType(JSON)
                    .accept(JSON)
                    .get(format("/v1/api/service/%s/account/live", serviceId))
                    .then()
                    .statusCode(200)
                    .body("service_name", is("New Service Name"));
        }

        @Test
        void shouldUpdateServiceNameInTest_whenNoLiveAccountExists() {
            // Create test account
            app.givenSetup()
                    .body(toJson(testGatewayAccountRequest))
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .body("service_name", is("Service Name"));

            // attempt to update service name
            app.givenSetup().accept(JSON)
                    .body(Map.of("service_name", "New Service Name"))
                    .patch(format("/v1/frontend/service/%s/servicename", serviceId))
                    .then()
                    .statusCode(200);

            // verify service name updated in test account 
            given().port(app.getLocalPort())
                    .contentType(JSON)
                    .accept(JSON)
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(200)
                    .body("service_name", is("New Service Name"));
        }
        
        @Test
        void shouldReturn404_whenNoTestOrLiveAccountsExist()
        {
            // attempt to update service name
            app.givenSetup().accept(JSON)
                    .body(Map.of("service_name", "New Service Name"))
                    .patch(format("/v1/frontend/service/%s/servicename", serviceId))
                    .then()
                    .statusCode(404);
        }

        @Test
        void shouldReturn404_whenNoTestAccountExists()
        {
            // Create live account
            app.givenSetup()
                    .body(toJson(liveGatewayAccountRequest))
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .body("service_name", is("Service Name"));

            // attempt to update service name
            app.givenSetup().accept(JSON)
                    .body(Map.of("service_name", "New Service Name"))
                    .patch(format("/v1/frontend/service/%s/servicename", serviceId))
                    .then()
                    .statusCode(404);
        }

        @Test
        void assertBadRequestForMissingServiceNameField() {
            app.givenSetup().body(toJson(gatewayAccountRequest)).post(ACCOUNTS_API_URL);

            app.givenSetup().accept(JSON)
                    .body(Map.of())
                    .patch(format("/v1/frontend/service/%s/servicename", serviceId))
                    .then()
                    .statusCode(422)
                    .body("message", contains("Field [service_name] cannot be null"))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }
        
        @Test
        void assertBadRequestForInvalidServiceNameLength() {
            app.givenSetup().body(toJson(gatewayAccountRequest)).post(ACCOUNTS_API_URL);

            app.givenSetup().accept(JSON)
                    .body(Map.of("service_name", "service-name-more-than-fifty-chars-service-name-more-than-fifty-chars"))
                    .patch(format("/v1/frontend/service/%s/servicename", serviceId))
                    .then()
                    .statusCode(422)
                    .body("message", contains("Field [service_name] can have a size between 1 and 50"))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }
        
        @Test
        void assertNotFoundForNonExistentServiceId() {
            app.givenSetup().accept(JSON)
                    .body(Map.of("service_name", "New Service Name"))
                    .patch("/v1/frontend/service/nexiste-pas/servicename")
                    .then()
                    .statusCode(404)
                    .body("message", contains("Gateway account not found for service ID [nexiste-pas] and account type [test]"))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }
    }
    
    @Nested
    class UpdateServiceNameByAccountId {
        @Test
        void updateServiceName_shouldUpdateGatewayAccountServiceNameSuccessfully() {
            String accountId = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());

            String newServiceName = "A Brand New Service Name";

            app.givenSetup().accept(JSON)
                    .body(Map.of("service_name", newServiceName))
                    .patch(ACCOUNTS_FRONTEND_URL + accountId + "/servicename")
                    .then()
                    .statusCode(200);

            String currentServiceName = app.getDatabaseTestHelper().getAccountServiceName(Long.valueOf(accountId));
            assertThat(currentServiceName, is(newServiceName));
        }

        @Test
        void updateServiceName_shouldNotUpdateGatewayAccountServiceNameIfMissingServiceName() {
            String accountId = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());

            app.givenSetup().accept(JSON)
                    .body(Map.of())
                    .patch(ACCOUNTS_FRONTEND_URL + accountId + "/servicename")
                    .then()
                    .statusCode(400)
                    .body("message", contains("Field(s) missing: [service_name]"))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        void updateServiceName_shouldFailUpdatingIfInvalidServiceNameLength() {
            String accountId = testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());
            String tooLongServiceName = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

            app.givenSetup().accept(JSON)
                    .body(Map.of("service_name", tooLongServiceName))
                    .patch(ACCOUNTS_FRONTEND_URL + accountId + "/servicename")
                    .then()
                    .statusCode(400)
                    .body("message", contains("Field(s) are too big: [service_name]"))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        void updateServiceName_shouldNotUpdateGatewayAccountServiceNameIfAccountIdIsNotNumeric() {
           app.givenSetup().accept(JSON)
                    .body(Map.of("service_name", "A Brand New Service Name"))
                    .patch(ACCOUNTS_FRONTEND_URL + "NON_NUMERIC_ACCOUNT_ID" + "/servicename")
                    .then()
                    .contentType(JSON)
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("code", is(404))
                    .body("message", is("HTTP 404 Not Found"));
        }
        
        @Test
        void updateServiceName_shouldNotUpdateGatewayAccountServiceNameIfAccountIdDoesNotExist() {
            testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());
            String nonExistentAccountId = "111111111";

            app.givenSetup().accept(JSON)
                    .body(Map.of("service_name", "A Brand New Service Name"))
                    .patch(ACCOUNTS_FRONTEND_URL + nonExistentAccountId + "/servicename")
                    .then()
                    .contentType(JSON)
                    .statusCode(404)
                    .body("message", contains("The gateway account id '111111111' does not exist"))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }
    }

    @Test
    void updateAcceptedCardTypes_shouldNotUpdateGatewayAccountIfCardTypesFieldIsMissing() {
        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards();

        String body = "{}";
        updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), body)
                .then()
                .statusCode(400)
                .body("message", contains("Field(s) missing: [card_types]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void updateAcceptedCardTypes_shouldNotUpdateGatewayAccountIfCardTypeIdIsNotExisting() {
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
    
    @Nested
    class UpdateCardTypesByServiceIdAndAccountType {

        private final Map<String, List<CardTypeEntity>> cardTypes = app.givenSetup()
                .contentType(JSON)
                .accept(JSON)
                .get("/v1/api/card-types")
                .then()
                .extract()
                .as(new TypeRef<Map<String, List<CardTypeEntity>>>() {
                });

        @Test
        void updateAcceptedCardTypes_shouldUpdateGatewayAccountToAcceptCardTypes() {

            CardTypeEntity visaCredit = getCardTypes(CardType.CREDIT, "visa");

            String serviceId = "my-service-id";
            testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder()
                    .withProvider("worldpay")
                    .withServiceId(serviceId)
                    .build());
            
            String bodyWithJustVisaCredit = buildAcceptedCardTypesBody(visaCredit);
            updateGatewayAccountCardTypesWith(serviceId, GatewayAccountType.TEST, bodyWithJustVisaCredit)
                    .then()
                    .statusCode(200);
            
            given().port(app.getLocalPort())
                    .get(String.format("/v1/frontend/service/%s/account/%s/card-types", serviceId, "test"))
                    .then()
                    .body("card_types", hasSize(1))
                    .body("card_types[0].brand", is("visa"))
                    .body("card_types[0].label", is("Visa"))
                    .body("card_types[0].type", is("CREDIT"))
                    .body("card_types[0].requires3ds", is(false));

            CardTypeEntity visaDebit = getCardTypes(CardType.DEBIT, "visa");
            CardTypeEntity mastercardCredit = getCardTypes(CardType.CREDIT, "master-card");

            String bodyWithAllThreeCardTypes = buildAcceptedCardTypesBody(visaCredit, visaDebit, mastercardCredit);
            updateGatewayAccountCardTypesWith(serviceId, GatewayAccountType.TEST, bodyWithAllThreeCardTypes)
                    .then()
                    .statusCode(200);
            
            given().port(app.getLocalPort())
                    .get(String.format("/v1/frontend/service/%s/account/%s/card-types", serviceId, "test"))
                    .then()
                    .body("card_types", hasSize(3));
        }

        private Response updateGatewayAccountCardTypesWith(String serviceId, GatewayAccountType accountType, String body) {
            String url = format("/v1/frontend/service/%s/account/%s/card-types", serviceId, accountType.toString());
            return app.givenSetup()
                    .contentType(ContentType.JSON)
                    .accept(JSON)
                    .body(body)
                    .post(url);
        }

        @NotNull
        private CardTypeEntity getCardTypes(CardType cardType, String brand) {
            return cardTypes.get("card_types").stream().filter(x -> x.getType() == cardType && x.getBrand().equals(brand)).findFirst().get();
        }

        @Test
        void updateAcceptedCardTypes_shouldUpdateGatewayAccountToAcceptNoCardTypes() {
            String serviceId = "another-service-id";
            testHelpers.createGatewayAccount(aCreateGatewayAccountPayloadBuilder()
                    .withProvider("worldpay")
                    .withServiceId(serviceId)
                    .build());

            String bodyWithJustVisaCredit = buildAcceptedCardTypesBody(getCardTypes(CardType.CREDIT, "visa"));
            updateGatewayAccountCardTypesWith(serviceId, GatewayAccountType.TEST, bodyWithJustVisaCredit)
                    .then()
                    .statusCode(200);

            given().port(app.getLocalPort())
                    .get(String.format("/v1/frontend/service/%s/account/%s/card-types", serviceId, "test"))
                    .then()
                    .body("card_types", hasSize(1));

            updateGatewayAccountCardTypesWith(serviceId, GatewayAccountType.TEST, buildAcceptedCardTypesBody())
                    .then()
                    .statusCode(200);

            given().port(app.getLocalPort())
                    .get(String.format("/v1/frontend/service/%s/account/%s/card-types", serviceId, "test"))
                    .then()
                    .body("card_types", hasSize(0));
        }
    }
    
    @Nested
    class UpdateCardTypesByAccountId {

        @Test
        void updateAcceptedCardTypes_shouldUpdateGatewayAccountToAcceptCardTypes() {
            CardTypeEntity mastercardCreditCard = app.getDatabaseTestHelper().getMastercardCreditCard();

            CardTypeEntity visaCreditCard = app.getDatabaseTestHelper().getVisaCreditCard();

            CardTypeEntity visaDebitCard = app.getDatabaseTestHelper().getVisaDebitCard();

            DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards(mastercardCreditCard, visaCreditCard);
            String body = buildAcceptedCardTypesBody(mastercardCreditCard, visaDebitCard);
            updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), body)
                    .then()
                    .statusCode(200);

            List<Map<String, Object>> acceptedCardTypes =
                    app.getDatabaseTestHelper().getAcceptedCardTypesByAccountId(accountRecord.getAccountId());

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
        void updateAcceptedCardTypes_shouldUpdateGatewayAccountToAcceptNoCardTypes() {
            CardTypeEntity mastercardCreditCardTypeRecord = app.getDatabaseTestHelper().getMastercardCreditCard();
            CardTypeEntity visaCreditCardTypeRecord = app.getDatabaseTestHelper().getVisaCreditCard();

            DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards(
                    mastercardCreditCardTypeRecord, visaCreditCardTypeRecord);

            updateGatewayAccountCardTypesWith(accountRecord.getAccountId(), buildAcceptedCardTypesBody())
                    .then()
                    .statusCode(200);

            List<Map<String, Object>> acceptedCardTypes =
                    app.getDatabaseTestHelper().getAcceptedCardTypesByAccountId(accountRecord.getAccountId());

            assertEquals(0, acceptedCardTypes.size());
        }
    }

    @Test
    void updateAcceptedCardTypes_shouldFailWhenCardTypeRequires3ds_whenGatewayAccountDoesNotRequire3ds() {

        CardTypeEntity maestroCard = app.getDatabaseTestHelper().getMaestroCard();

        DatabaseFixtures.TestAccount gatewayAccount = createAccountRecordWithCards();

        updateGatewayAccountCardTypesWith(gatewayAccount.getAccountId(), buildAcceptedCardTypesBody(maestroCard))
                .then()
                .statusCode(409);

        List<Map<String, Object>> acceptedCardTypes =
                app.getDatabaseTestHelper().getAcceptedCardTypesByAccountId(gatewayAccount.getAccountId());

        assertEquals(0, acceptedCardTypes.size());
    }

    private Response updateGatewayAccountCardTypesWith(long accountId, String body) {
        return app.givenSetup()
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

    private DatabaseFixtures.TestAccount createAccountRecordWithCards(CardTypeEntity... cardTypes) {
        return app.getDatabaseFixtures()
                .aTestAccount()
                .withCardTypeEntities(Arrays.asList(cardTypes))
                .insert();
    }
}
