package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.gatewayaccount.model.SandboxCredentials;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.Map;
import java.util.Optional;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.ACCOUNTS_API_URL;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.CreateGatewayAccountPayloadBuilder.aCreateGatewayAccountPayloadBuilder;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.assertCorrectCreateResponse;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountResourceCreateIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    GatewayAccountDao gatewayAccountDao;

    @BeforeEach
    void setUp() {
        gatewayAccountDao = app.getInstanceFromGuiceContainer(GatewayAccountDao.class);
    }

    @Nested
    class Degateway {

        @Test
        void shouldNotAllowMultipleGatewayAccountsWhenQueryStringIsIncluded() {
            var requestForAccountPayload = aCreateGatewayAccountPayloadBuilder()
                    .withProvider("worldpay")
                    .withServiceId("my-service-id")
                    .withDescription("my test service")
                    .withAnalyticsId("analytics")
                    .withSendPayerIpAddressToGateway(true)
                    .withSendPayerEmailToGateway(true)
                    .build();

            var requestForSecondAccountPayload = aCreateGatewayAccountPayloadBuilder()
                    .withProvider("worldpay")
                    .withServiceId("my-service-id")
                    .withDescription("my extra test service")
                    .withAnalyticsId("more analytics")
                    .withSendPayerEmailToGateway(true)
                    .withSendPayerIpAddressToGateway(true)
                    .build();

            var response = app.givenSetup()
                    .body(requestForAccountPayload)
                    .post(ACCOUNTS_API_URL + "?degatewayification=true")
                    .then()
                    .statusCode(201)
                    .contentType(JSON);

            app.givenSetup()
                    .body(requestForSecondAccountPayload)
                    .post(ACCOUNTS_API_URL + "?degatewayification=true")
                    .then()
                    .statusCode(409)
                    .contentType(JSON)
                    .body("message",
                            contains("Gateway account with service id my-service-id and account type 'test' already exists."));

            assertCorrectCreateResponse(response,
                    TEST,
                    "my test service",
                    "analytics",
                    null,
                    true,
                    true);
        }
    }

    @Nested
    class PreDegateway {

        @Test
        void shouldAllowMultipleGatewayAccounts() {
            var requestForAccountPayload = aCreateGatewayAccountPayloadBuilder()
                    .withProvider("worldpay")
                    .withServiceId("my-service-id")
                    .withDescription("my test service")
                    .withAnalyticsId("analytics")
                    .withSendPayerEmailToGateway(true)
                    .withSendPayerIpAddressToGateway(true)
                    .build();

            var requestForSecondAccountPayload = aCreateGatewayAccountPayloadBuilder()
                    .withProvider("worldpay")
                    .withServiceId("my-service-id")
                    .withDescription("my extra test service")
                    .withAnalyticsId("more analytics")
                    .withSendPayerEmailToGateway(true)
                    .withSendPayerIpAddressToGateway(true)
                    .build();

            app.givenSetup()
                    .body(requestForAccountPayload)
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .contentType(JSON);

            app.givenSetup()
                    .body(requestForSecondAccountPayload)
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .contentType(JSON);
        }

        @Test
        void createASandboxGatewayAccount() {
            var payload = Map.of("payment_provider", "sandbox",
                    "service_id", "a-valid-service-id",
                    "description", "my test service",
                    "analytics_id", "analytics",
                    "send_payer_email_to_gateway", true,
                    "send_payer_ip_address_to_gateway", true);
            var response = app.givenSetup()
                    .body(payload)
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .contentType(JSON);

            assertCorrectCreateResponse(response,
                    TEST,
                    "my test service",
                    "analytics",
                    null,
                    true,
                    true);
        }

        @Test
        void createAWorldpaySandboxGatewayAccount() {
            var payload = Map.of("payment_provider", "worldpay",
                    "service_id", "a-valid-service-id",
                    "description", "my test service",
                    "analytics_id", "analytics",
                    "send_payer_email_to_gateway", true,
                    "send_payer_ip_address_to_gateway", true);
            var response = app.givenSetup()
                    .body(payload)
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .contentType(JSON);

            assertCorrectCreateResponse(response,
                    TEST,
                    "my test service",
                    "analytics",
                    null,
                    true,
                    true);
        }

        @Test
        void shouldCreateAccountWithServiceIdAndDefaultsForOtherProperties() {
            var payload = Map.of("service_id", "some-special-service-id");
            var response = app.givenSetup()
                    .body(toJson(payload))
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("gateway_account_id", not(emptyString()))
                    .body("type", is("test"))
                    .body("requires_3ds", is(false));

            app.givenSetup()
                    .get(response.extract()
                            .header("Location")
                            .replace("https", "http")) //Scheme on links back are forced to be https
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("payment_provider", is("sandbox"))
                    .body("gateway_account_id", is(notNullValue()))
                    .body("type", is("test"))
                    .body("requires3ds", is(false))
                    .body("allow_apple_pay", is(true))
                    .body("allow_google_pay", is(false))
                    .body("corporate_credit_card_surcharge_amount", is(0))
                    .body("corporate_debit_card_surcharge_amount", is(0))
                    .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
        }

        @Test
        void shouldCreateAccountWithAllConfigurationOptions() {
            var payload = Map.of("service_id", "some-special-service-id",
                    "service_name", "a-service-name",
                    "type", "live",
                    "payment_provider", "stripe",
                    "description", "a-description",
                    "analytics_id", "an-analytics-id",
                    "requires_3ds", true,
                    "allow_apple_pay", true,
                    "allow_google_pay", true);
            var response = app.givenSetup()
                    .body(toJson(payload))
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("gateway_account_id", not(emptyString()))
                    .body("service_name", is("a-service-name"))
                    .body("type", is("live"))
                    .body("requires_3ds", is(true))
                    .body("analytics_id", is("an-analytics-id"))
                    .body("description", is("a-description"));

            app.givenSetup()
                    .get(response.extract()
                            .header("Location")
                            .replace("https", "http")) //Scheme on links back are forced to be https
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("payment_provider", is("stripe"))
                    .body("gateway_account_id", is(notNullValue()))
                    .body("service_name", is("a-service-name"))
                    .body("service_id", is("some-special-service-id"))
                    .body("type", is("live"))
                    .body("analytics_id", is("an-analytics-id"))
                    .body("description", is("a-description"))
                    .body("requires3ds", is(true))
                    .body("allow_apple_pay", is(true))
                    .body("allow_google_pay", is(true));
        }

        @Test
        void createStripeGatewayAccountWithoutCredentials() {
            var payload = Map.of(
                    "type", "test",
                    "payment_provider", "stripe",
                    "service_name", "My shiny new stripe service",
                    "service_id", "a-valid-service-id",
                    "description", "my test service",
                    "analytics_id", "analytics",
                    "send_payer_email_to_gateway", false,
                    "send_payer_ip_address_to_gateway", false);
            app.givenSetup()
                    .body(payload)
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("description", is("my test service"))
                    .body("analytics_id", is("analytics"))
                    .body("send_payer_email_to_gateway", is(false))
                    .body("send_payer_ip_address_to_gateway", is(false))
                    .body("gateway_account_id", not(emptyString()))
                    .body("service_name", is("My shiny new stripe service"))
                    .body("type", is("test"))
                    .body("links.size()", is(1))
                    .body("links[0].href", matchesPattern("https://localhost:[0-9]*/v1/api/accounts/[0-9]*"))
                    .body("links[0].rel", is("self"))
                    .body("links[0].method", is("GET"));
        }

        @Test
        void createStripeGatewayAccountWithCredentials() {
            var stripeAccountId = "abc";
            var payload = Map.of(
                    "type", "test",
                    "payment_provider", "stripe",
                    "service_name", "My shiny new stripe service",
                    "service_id", "a-valid-service-id",
                    "credentials", Map.of("stripe_account_id", stripeAccountId));
            var gatewayAccountId = app.givenSetup()
                    .body(toJson(payload))
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("gateway_account_id", not(emptyString()))
                    .body("service_name", is("My shiny new stripe service"))
                    .body("type", is("test"))
                    .body("links.size()", is(1))
                    .body("links[0].href", matchesPattern("https://localhost:[0-9]*/v1/api/accounts/[0-9]*"))
                    .body("links[0].rel", is("self"))
                    .body("links[0].method", is("GET"))
                    .extract()
                    .body()
                    .jsonPath()
                    .getString("gateway_account_id");
            var gatewayAccount = gatewayAccountDao.findById(Long.valueOf(gatewayAccountId));
            assertThat(gatewayAccount.isPresent(), is(true));

            var gatewayAccountCredentialsList = gatewayAccount.get().getGatewayAccountCredentials();
            assertThat(gatewayAccountCredentialsList.size(), is(1));
            var credentialsObject = gatewayAccountCredentialsList.getFirst().getCredentialsObject();
            assertThat(credentialsObject, isA(StripeCredentials.class));
            assertThat(((StripeCredentials) credentialsObject).getStripeAccountId(), is(stripeAccountId));
            assertThat(gatewayAccountCredentialsList.getFirst().getState(), is(ACTIVE));
            assertThat(gatewayAccountCredentialsList.getFirst().getPaymentProvider(), is("stripe"));
            assertThat(gatewayAccountCredentialsList.getFirst().getActiveStartDate(), is(notNullValue()));
        }

        @Test
        void should_create_Adyen_gateway_account_without_credentials() {
            // language=JSON
            var requestBodyWithoutCredentials = """
                    {
                      "payment_provider": "adyen",
                      "type": "test",
                      "service_name": "new service",
                      "service_id": "service-external-id"
                    }""";

            var accountId = app.givenSetup()
                    .body(requestBodyWithoutCredentials)
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .body("gateway_account_id", not(emptyString()))
                    .body("external_id", not(emptyString()))
                    .body("type", is("test"))
                    .body("service_name", is("new service"))
                    .body("description", nullValue())
                    .body("analytics_id", nullValue())
                    .body("links[0].method", is("GET"))
                    .body("links[0].href", matchesPattern("https://localhost:[0-9]*/v1/api/accounts/[0-9]*"))
                    .body("links[0].rel", is("self"))
                    .body("requires_3ds", is(false))
                    .body("send_payer_email_to_gateway", is(false))
                    .body("send_payer_ip_address_to_gateway", is(false))
                    .extract()
                    .body()
                    .jsonPath()
                    .getString("gateway_account_id");

            var gatewayAccount = gatewayAccountDao.findById(Long.valueOf(accountId));
            assertTrue(gatewayAccount.isPresent());

            Optional<GatewayAccountCredentialsEntity> gatewayAccountCredentials =
                    gatewayAccount.get().getGatewayAccountCredentials().stream().findFirst();
            assertTrue(gatewayAccountCredentials.isPresent());
            assertThat(gatewayAccountCredentials.get().getPaymentProvider(), is("adyen"));
        }

        @Test
        void should_create_Adyen_gateway_account_with_credentials() {
            // language=JSON
            var requestBodyWithCredentials = """
                    {
                       "payment_provider": "adyen",
                       "type": "test",
                       "service_name": "My new service",
                       "service_id": "service-ext-id-2",
                       "credentials": {
                         "legal_entity_id": "LEM0000000000000001",
                         "store_id": "ST00000000000000000000001",
                         "account_holder_id": "AH3227C223222H5J4DCLW9VBV",
                         "balance_account_id": "BA0000000000000000000001"
                       }
                     }""";

            var accountId = app.givenSetup()
                    .body(requestBodyWithCredentials)
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201)
                    .body("gateway_account_id", not(emptyString()))
                    .body("external_id", not(emptyString()))
                    .body("type", is("test"))
                    .body("service_name", is("My new service"))
                    .body("description", nullValue())
                    .body("analytics_id", nullValue())
                    .body("links[0].method", is("GET"))
                    .body("links[0].href", matchesPattern("https://localhost:[0-9]*/v1/api/accounts/[0-9]*"))
                    .body("links[0].rel", is("self"))
                    .body("requires_3ds", is(false))
                    .body("send_payer_email_to_gateway", is(false))
                    .body("send_payer_ip_address_to_gateway", is(false))
                    .extract()
                    .body()
                    .jsonPath()
                    .getString("gateway_account_id");

            var gatewayAccount = gatewayAccountDao.findById(Long.valueOf(accountId));

            assertTrue(gatewayAccount.isPresent());

            Optional<GatewayAccountCredentialsEntity> gatewayAccountCredentials =
                    gatewayAccount.get().getGatewayAccountCredentials().stream().findFirst();
            assertTrue(gatewayAccountCredentials.isPresent());
            assertThat(gatewayAccountCredentials.get().getPaymentProvider(), is("adyen"));

            AdyenCredentials credentialsObject = (AdyenCredentials) gatewayAccountCredentials.get().getCredentialsObject();
            assertThat(credentialsObject.legalEntityId(), is("LEM0000000000000001"));
            assertThat(credentialsObject.storeId(), is("ST00000000000000000000001"));
            assertThat(credentialsObject.accountHolderId(), is("AH3227C223222H5J4DCLW9VBV"));
            assertThat(credentialsObject.balanceAccountId(), is("BA0000000000000000000001"));
        }

        @Test
        void createGatewayAccountWithoutPaymentProviderDefaultsToSandbox() {
            var payload = toJson(Map.of("name", "test account",
                    "type", "test",
                    "service_id", "a-valid-service-id",
                    "send_payer_email_to_gateway", true,
                    "send_payer_ip_address_to_gateway", true));

            var response = app.givenSetup()
                    .body(payload)
                    .post(ACCOUNTS_API_URL)
                    .then()
                    .statusCode(201);

            assertCorrectCreateResponse(response,
                    TEST,
                    null,
                    null,
                    null,
                    true,
                    true);

            app.givenSetup()
                    .contentType(JSON)
                    .get(response.extract()
                            .header("Location")
                            .replace("https", "http")) //Scheme on links back are forced to be https
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("payment_provider", is("sandbox"))
                    .body("gateway_account_id", is(notNullValue()))
                    .body("type", is("test"));

            var gatewayAccountId = response.extract().body().jsonPath().getString("gateway_account_id");
            var gatewayAccount = gatewayAccountDao.findById(Long.valueOf(gatewayAccountId));
            assertThat(gatewayAccount.isPresent(), is(true));

            var gatewayAccountCredentialsList = gatewayAccount.get().getGatewayAccountCredentials();
            assertThat(gatewayAccountCredentialsList.size(), is(1));
            assertThat(gatewayAccountCredentialsList.getFirst().getState(), is(ACTIVE));
            assertThat(gatewayAccountCredentialsList.getFirst().getPaymentProvider(), is("sandbox"));

            var credentialsObj = gatewayAccountCredentialsList.getFirst().getCredentialsObject();
            assertThat(credentialsObj, isA(SandboxCredentials.class));
            assertThat(credentialsObj.hasCredentials(), is(true));
        }
    }
}
