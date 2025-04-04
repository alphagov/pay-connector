package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.AddGatewayAccountParams;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargesApiResourceCreateProviderCredentialsIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());
    private static final String VALID_SERVICE_ID = "a-valid-service-id";
    
    @Nested
    class ByGatewayAccountId {
        @Test
        void shouldCreateChargeForCredentialIdProvided() {
            //set up a Worldpay to Worldpay PSP switch scenario in the database (as described in PP-10958)
            String accountId = String.valueOf(RandomUtils.nextInt());
            AddGatewayAccountCredentialsParams credentialsToUse = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.ENTERED)
                    .withGatewayAccountId(Long.parseLong(accountId))
                    .build();
            AddGatewayAccountCredentialsParams activeCredentials = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.ACTIVE)
                    .withGatewayAccountId(Long.parseLong(accountId))
                    .build();
            AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                    .withGatewayAccountCredentials(List.of(credentialsToUse, activeCredentials))
                    .withAccountId(accountId)
                    .build();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

            //create the charge specifying the credential id to use
            String postBody = toJson(Map.of(
                    "amount", 6234L,
                    "reference", "Test reference",
                    "description", "Test description",
                    "return_url", "http://service.local/success-page/",
                    "credential_id", credentialsToUse.getExternalId()
            ));
            String chargeId = app.givenSetup()
                    .body(postBody)
                    .post(format("/v1/api/accounts/%s/charges", accountId))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .body("payment_provider", is("worldpay"))
                    .extract().body().jsonPath().get("charge_id");

            //assert that the correct credential is recorded against the charge 
            Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(chargeId);
            assertThat(charge.get("gateway_account_credential_id"), is(credentialsToUse.getId()));
        }

        @Test
        void shouldReturn400WhenCredentialsNotFoundForCredentialIdProvided() {
            //set up an account with valid credentials in the database
            String accountId = String.valueOf(RandomUtils.nextInt());
            AddGatewayAccountCredentialsParams credentials = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.ACTIVE)
                    .withGatewayAccountId(Long.parseLong(accountId))
                    .build();
            AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                    .withGatewayAccountCredentials(List.of(credentials))
                    .withAccountId(accountId)
                    .build();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

            String postBody = toJson(Map.of(
                    "amount", 6234L,
                    "reference", "Test reference",
                    "description", "Test description",
                    "return_url", "http://service.local/success-page/",
                    "credential_id", "random-credential-id"
            ));

            app.givenSetup()
                    .body(postBody)
                    .post(format("/v1/api/accounts/%s/charges", accountId))
                    .then()
                    .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                    .contentType(JSON)
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()))
                    .body("message", contains(format("Credentials not found for gateway account [%s] and credential_external_id [random-credential-id]", accountId)));
        }

        @Test
        void shouldReturn400WhenNoCredentialsAreInUsableState() {
            String accountId = String.valueOf(RandomUtils.nextInt());
            AddGatewayAccountCredentialsParams credentials = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.RETIRED)
                    .withGatewayAccountId(Long.parseLong(accountId))
                    .build();
            AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                    .withPaymentGateway("worldpay")
                    .withGatewayAccountCredentials(List.of(credentials))
                    .withAccountId(accountId)
                    .build();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

            String postBody = toJson(Map.of(
                    "amount", 6234L,
                    "reference", "Test reference",
                    "description", "Test description",
                    "return_url", "http://service.local/success-page/",
                    "credential_id", credentials.getExternalId()
            ));

            app.givenSetup()
                    .body(postBody)
                    .post(format("/v1/api/accounts/%s/charges", accountId))
                    .then()
                    .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                    .contentType(JSON)
                    .body("message", contains("Payment provider details are not configured on this account"))
                    .body("error_identifier", is(ErrorIdentifier.ACCOUNT_NOT_LINKED_WITH_PSP.toString()));
        }

        @Test
        void shouldReturn400WhenCredentialsInCreatedState() {
            String accountId = String.valueOf(RandomUtils.nextInt());
            AddGatewayAccountCredentialsParams credentials = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.CREATED)
                    .withGatewayAccountId(Long.parseLong(accountId))
                    .build();
            AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                    .withPaymentGateway("worldpay")
                    .withGatewayAccountCredentials(List.of(credentials))
                    .withAccountId(accountId)
                    .build();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

            String postBody = toJson(Map.of(
                    "amount", 6234L,
                    "reference", "Test reference",
                    "description", "Test description",
                    "return_url", "http://service.local/success-page/",
                    "credential_id", credentials.getExternalId()
            ));

            app.givenSetup()
                    .body(postBody)
                    .post(format("/v1/api/accounts/%s/charges", accountId))
                    .then()
                    .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                    .contentType(JSON)
                    .body("message", contains("Payment provider details are not configured on this account"))
                    .body("error_identifier", is(ErrorIdentifier.ACCOUNT_NOT_LINKED_WITH_PSP.toString()));
        }
    }

    @Nested
    class ByServiceIdAndAccountType {
        @Test
        void shouldCreateChargeForCredentialIdProvided() {
            //set up a Worldpay to Worldpay PSP switch scenario in the database (as described in PP-10958)
            String accountId = String.valueOf(RandomUtils.nextInt());
            AddGatewayAccountCredentialsParams credentialsToUse = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.ENTERED)
                    .withGatewayAccountId(Long.parseLong(accountId))
                    .build();
            AddGatewayAccountCredentialsParams activeCredentials = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.ACTIVE)
                    .withGatewayAccountId(Long.parseLong(accountId))
                    .build();
            AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                    .withGatewayAccountCredentials(List.of(credentialsToUse, activeCredentials))
                    .withAccountId(accountId)
                    .withServiceId(VALID_SERVICE_ID)
                    .build();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

            //create the charge specifying the credential id to use
            String postBody = toJson(Map.of(
                    "amount", 6234L,
                    "reference", "Test reference",
                    "description", "Test description",
                    "return_url", "http://service.local/success-page/",
                    "credential_id", credentialsToUse.getExternalId()
            ));
            String chargeId = app.givenSetup()
                    .body(postBody)
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .body("payment_provider", is("worldpay"))
                    .extract().body().jsonPath().get("charge_id");

            //assert that the correct credential is recorded against the charge 
            Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(chargeId);
            assertThat(charge.get("gateway_account_credential_id"), is(credentialsToUse.getId()));
        }

        @Test
        void shouldReturn400WhenCredentialsNotFoundForCredentialIdProvided() {
            //set up an account with valid credentials in the database
            String accountId = String.valueOf(RandomUtils.nextInt());
            AddGatewayAccountCredentialsParams credentials = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.ACTIVE)
                    .withGatewayAccountId(Long.parseLong(accountId))
                    .build();
            AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                    .withGatewayAccountCredentials(List.of(credentials))
                    .withAccountId(accountId)
                    .withServiceId(VALID_SERVICE_ID)
                    .build();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

            String postBody = toJson(Map.of(
                    "amount", 6234L,
                    "reference", "Test reference",
                    "description", "Test description",
                    "return_url", "http://service.local/success-page/",
                    "credential_id", "random-credential-id"
            ));

            app.givenSetup()
                    .body(postBody)
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                    .contentType(JSON)
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()))
                    .body("message", contains(format("Credentials not found for gateway account [%s] and credential_external_id [random-credential-id]", accountId)));
        }

        @Test
        void shouldReturn400WhenNoCredentialsAreInUsableState() {
            String accountId = String.valueOf(RandomUtils.nextInt());
            AddGatewayAccountCredentialsParams credentials = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.RETIRED)
                    .withGatewayAccountId(Long.parseLong(accountId))
                    .build();
            AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                    .withPaymentGateway("worldpay")
                    .withGatewayAccountCredentials(List.of(credentials))
                    .withAccountId(accountId)
                    .withServiceId(VALID_SERVICE_ID)
                    .build();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

            String postBody = toJson(Map.of(
                    "amount", 6234L,
                    "reference", "Test reference",
                    "description", "Test description",
                    "return_url", "http://service.local/success-page/",
                    "credential_id", credentials.getExternalId()
            ));

            app.givenSetup()
                    .body(postBody)
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                    .contentType(JSON)
                    .body("message", contains("Payment provider details are not configured on this account"))
                    .body("error_identifier", is(ErrorIdentifier.ACCOUNT_NOT_LINKED_WITH_PSP.toString()));
        }

        @Test
        void shouldReturn400WhenCredentialsInCreatedState() {
            String accountId = String.valueOf(RandomUtils.nextInt());
            AddGatewayAccountCredentialsParams credentials = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider("worldpay")
                    .withState(GatewayAccountCredentialState.CREATED)
                    .withGatewayAccountId(Long.parseLong(accountId))
                    .build();
            AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                    .withPaymentGateway("worldpay")
                    .withGatewayAccountCredentials(List.of(credentials))
                    .withAccountId(accountId)
                    .withServiceId(VALID_SERVICE_ID)
                    .build();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

            String postBody = toJson(Map.of(
                    "amount", 6234L,
                    "reference", "Test reference",
                    "description", "Test description",
                    "return_url", "http://service.local/success-page/",
                    "credential_id", credentials.getExternalId()
            ));

            app.givenSetup()
                    .body(postBody)
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                    .contentType(JSON)
                    .body("message", contains("Payment provider details are not configured on this account"))
                    .body("error_identifier", is(ErrorIdentifier.ACCOUNT_NOT_LINKED_WITH_PSP.toString()));
        }
    }
}
