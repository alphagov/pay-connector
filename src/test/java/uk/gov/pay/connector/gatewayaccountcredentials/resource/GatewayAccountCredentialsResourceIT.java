package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.postgresql.util.PGobject;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountCredentialsResourceIT {
    @RegisterExtension
    static ITestBaseExtension app = new ITestBaseExtension("sandbox");
    private DatabaseFixtures.TestAccount testAccount;
    private static final String PATCH_CREDENTIALS_URL = "/v1/api/accounts/%s/credentials/%s";
    private Long credentialsId;
    private Long accountId;

    @BeforeEach
    void setUp() {
        Map<String, Object> credentials = Map.of(
                "one_off_customer_initiated", Map.of(
                        "merchant_code", "a-merchant-code",
                        "username", "a-username",
                        "password", "a-password"));
        testAccount = addGatewayAccountAndCredential("worldpay", ACTIVE, TEST, credentials);
        accountId = testAccount.getAccountId();

        credentialsId = testAccount.getCredentials().get(0).getId();
    }
    
    @Test
    void createGatewayAccountsCredentialsWithCredentials_responseShouldBe200_Ok() {
        Map<String, String> credentials = Map.of("stripe_account_id", "some-account-id");
        app.givenSetup()
                .body(toJson(Map.of("payment_provider", "stripe", "credentials", credentials)))
                .post("/v1/api/accounts/" + accountId + "/credentials")
                .then()
                .statusCode(OK.getStatusCode());

        app.givenSetup()
                .get("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(OK.getStatusCode())
                .body("gateway_account_credentials.size()", is(2))
                .body("gateway_account_credentials[1].payment_provider", is("stripe"))
                .body("gateway_account_credentials[1].credentials.stripe_account_id", is("some-account-id"))
                .body("gateway_account_credentials[1].external_id", is(notNullValue(String.class)));
    }

    @Test
    void createGatewayAccountsCredentialsValidatesRequestBusinessLogic_responseShouldBe400() {
        app.givenSetup()
                .body(toJson(Map.of("payment_provider", "epdq")))
                .post("/v1/api/accounts/10000/credentials")
                .then()
                .statusCode(400);
    }

    @Test
    void patchGatewayAccountCredentialsValidRequest_responseShouldBe200() {
        DatabaseFixtures.TestAccount stripeTestAccount = addGatewayAccountAndCredential("stripe", ACTIVE, TEST,
                Map.of("stripe_account_id", "some-account-id"));
        long stripeCredentialsId = stripeTestAccount.getCredentials().get(0).getId();
        String stripeCredentialsExternalId = stripeTestAccount.getCredentials().get(0).getExternalId();

        Map<String, String> newCredentials = Map.of("stripe_account_id", "new-account-id");
        app.givenSetup()
                .body(toJson(List.of(
                        Map.of("op", "replace",
                                "path", "credentials",
                                "value", newCredentials),
                        Map.of("op", "replace",
                                "path", "last_updated_by_user_external_id",
                                "value", "a-new-user-external-id"),
                        Map.of("op", "replace",
                                "path", "state",
                                "value", "VERIFIED_WITH_LIVE_PAYMENT")
                )))
                .patch(format(PATCH_CREDENTIALS_URL, stripeTestAccount.getAccountId(), stripeCredentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials.stripe_account_id", is("new-account-id"))
                .body("last_updated_by_user_external_id", is("a-new-user-external-id"))
                .body("state", is("VERIFIED_WITH_LIVE_PAYMENT"))
                .body("created_date", is("2021-01-01T00:00:00.000Z"))
                .body("active_start_date", is("2021-02-01T00:00:00.000Z"))
                .body("active_end_date", is("2021-03-01T00:00:00.000Z"))
                .body("external_id", is(stripeCredentialsExternalId))
                .body("payment_provider", is("stripe"));

        Map<String, Object> updatedGatewayAccountCredentials = app.getDatabaseTestHelper().getGatewayAccountCredentialsById(stripeCredentialsId);
        assertThat(updatedGatewayAccountCredentials, hasEntry("last_updated_by_user_external_id", "a-new-user-external-id"));
    }

    @Test
    void patchGatewayAccountCredentialsInvalidRequestBody_shouldReturn400() {
        Long credentialsId = (Long) app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(accountId).get(0).get("id");

        app.givenSetup()
                .body(toJson(singletonList(
                        Map.of("op", "replace",
                                "path", "credentials"))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(400)
                .body("message[0]", is("Field [value] is required"));
    }

    @Test
    void patchGatewayAccountCredentialsGatewayAccountCredentialsNotFound_shouldReturn404() {
        Map<String, String> newCredentials = Map.of("username", "new-username",
                "password", "new-password",
                "merchant_id", "new-merchant-id");
        app.givenSetup()
                .body(toJson(singletonList(
                        Map.of("op", "replace",
                                "path", "credentials",
                                "value", newCredentials))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, 999999))
                .then()
                .statusCode(404)
                .body("message[0]", is("Gateway account credentials with id [999999] not found."));
    }

    @Test
    void patchGatewayAccountCredentialsGatewayMerchantId() {
        app.givenSetup()
                .body(toJson(List.of(
                        Map.of("op", "replace",
                                "path", "credentials/gateway_merchant_id",
                                "value", "abcdef123abcdef"))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("one_off_customer_initiated"))
                .body("credentials.one_off_customer_initiated", hasKey("username"))
                .body("credentials.one_off_customer_initiated", hasKey("merchant_code"))
                .body("credentials", hasKey("gateway_merchant_id"))
                .body("credentials.gateway_merchant_id", is("abcdef123abcdef"));

        Map<String, Object> updatedGatewayAccountCredentials = app.getDatabaseTestHelper().getGatewayAccountCredentialsById(credentialsId);

        Map<String, String> updatedCredentials = new Gson().fromJson(((PGobject) updatedGatewayAccountCredentials.get("credentials")).getValue(), Map.class);
        assertThat(updatedCredentials, hasEntry("gateway_merchant_id", "abcdef123abcdef"));
    }

    @Test
    void patchGatewayAccountCredentialsForGatewayMerchantIdShouldReturn400ForUnsupportedGateway() {
        DatabaseFixtures.TestAccount testAccount = addGatewayAccountAndCredential("stripe", ACTIVE, TEST, Map.of());
        AddGatewayAccountCredentialsParams params = testAccount.getCredentials().get(0);

        app.givenSetup()
                .body(toJson(singletonList(
                        Map.of("op", "replace",
                                "path", "credentials/gateway_merchant_id",
                                "value", "abcdef123abcdef"))))
                .patch(format(PATCH_CREDENTIALS_URL, params.getGatewayAccountId(), params.getId()))
                .then()
                .statusCode(400)
                .body("message[0]", is("Gateway 'stripe' does not support digital wallets."));
    }

    @Test
    void patchGatewayAccountCredentialsShouldNotOverWriteOtherExistingCredentials() {
        app.givenSetup()
                .body(toJson(List.of(
                        Map.of("op", "replace",
                                "path", "credentials/gateway_merchant_id",
                                "value", "abcdef123abcdef"))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("one_off_customer_initiated"))
                .body("credentials.one_off_customer_initiated", hasKey("username"))
                .body("credentials.one_off_customer_initiated", hasKey("merchant_code"))
                .body("credentials", hasKey("gateway_merchant_id"))
                .body("credentials.gateway_merchant_id", is("abcdef123abcdef"));

        Map<String, Object> newCredentials = Map.of("op", "replace",
                "path", "credentials/worldpay/one_off_customer_initiated",
                "value", Map.of("username", "new-username",
                        "password", "new-password",
                        "merchant_code", "new-merchant-code"));

        app.givenSetup()
                .body(toJson(List.of(newCredentials)))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("one_off_customer_initiated"))
                .body("credentials.one_off_customer_initiated", hasKey("username"))
                .body("credentials.one_off_customer_initiated.username", is("new-username"))
                .body("credentials.one_off_customer_initiated", hasKey("merchant_code"))
                .body("credentials.one_off_customer_initiated.merchant_code", is("new-merchant-code"))
                .body("credentials.one_off_customer_initiated", not(hasKey("password")))
                .body("credentials", hasKey("gateway_merchant_id"))
                .body("credentials.gateway_merchant_id", is("abcdef123abcdef"));

        Map<String, Object> updatedGatewayAccountCredentials = app.getDatabaseTestHelper().getGatewayAccountCredentialsById(credentialsId);

        Map<String, Object> updatedCredentials = new Gson().fromJson(((PGobject) updatedGatewayAccountCredentials.get("credentials")).getValue(), Map.class);
        assertThat(updatedCredentials, hasKey("one_off_customer_initiated"));
        Map<String, String> oneOffCustomerInitiated = (Map<String, String>) updatedCredentials.get("one_off_customer_initiated");
        assertThat(oneOffCustomerInitiated, hasEntry("username", "new-username"));
        assertThat(oneOffCustomerInitiated, hasEntry("password", "new-password"));
        assertThat(oneOffCustomerInitiated, hasEntry("merchant_code", "new-merchant-code"));
        assertThat(updatedCredentials, hasEntry("gateway_merchant_id", "abcdef123abcdef"));
    }

    private DatabaseFixtures.TestAccount addGatewayAccountAndCredential(
            String paymentProvider,
            GatewayAccountCredentialState state,
            GatewayAccountType gatewayAccountType,
            Map<String, Object> credentials) {

        long accountId = nextLong(2, 10000);
        LocalDateTime createdDate = LocalDate.parse("2021-01-01").atStartOfDay();
        LocalDateTime activeStartDate = LocalDate.parse("2021-02-01").atStartOfDay();
        LocalDateTime activeEndDate = LocalDate.parse("2021-03-01").atStartOfDay();

        AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                .withGatewayAccountId(accountId)
                .withPaymentProvider(paymentProvider)
                .withCreatedDate(createdDate.toInstant(ZoneOffset.UTC))
                .withActiveStartDate(activeStartDate.toInstant(ZoneOffset.UTC))
                .withActiveEndDate(activeEndDate.toInstant(ZoneOffset.UTC))
                .withState(state)
                .withCredentials(credentials)
                .build();

        return app.getDatabaseFixtures().aTestAccount().withPaymentProvider(paymentProvider)
                .withIntegrationVersion3ds(2)
                .withAccountId(accountId)
                .withType(gatewayAccountType)
                .withGatewayAccountCredentials(Collections.singletonList(credentialsParams))
                .insert();
    }
}
