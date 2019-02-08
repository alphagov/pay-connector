package uk.gov.pay.connector.it.util.gatewayaccount;

import com.google.common.collect.Maps;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountOperations {

    public static Response updateGatewayAccountCredentialsWith(int port, String accountId, Map<String, Object> credentials) {
        return given().port(port).contentType(JSON).accept(JSON)
                .body(credentials)
                .patch("/v1/frontend/accounts/" + accountId + "/credentials");
    }

    public static String createAGatewayAccountFor(int port, String testProvider, DatabaseTestHelper databaseTestHelper) {
        return createAGatewayAccountFor(port, testProvider, null, null, databaseTestHelper);
    }

    public static String createAGatewayAccountFor(int port, String testProvider, String description, String analyticsId, DatabaseTestHelper databaseTestHelper) {
        return createAGatewayAccountFor(port, testProvider, description, analyticsId, null, databaseTestHelper);
    }

    public static String createAGatewayAccountFor(int port,
                                                  String testProvider,
                                                  String description,
                                                  String analyticsId,
                                                  String requires_3ds,
                                                  DatabaseTestHelper databaseTestHelper) {
        Map<String, String> payload = Maps.newHashMap();
        payload.put("payment_provider", testProvider);
        if (description != null) {
            payload.put("description", description);
        }
        if (analyticsId != null) {
            payload.put("analytics_id", analyticsId);
        }
        if (requires_3ds != null) {
            payload.put("requires_3ds", requires_3ds);
        }
        ValidatableResponse response = given().port(port).contentType(JSON)
                .body(toJson(payload))
                .post("/v1/api/accounts/")
                .then()
                .statusCode(201)
                .contentType(JSON);

        GatewayAccountAssertions.assertGatewayAccountCreation.f(response, GatewayAccountEntity.Type.TEST, description, analyticsId, null);
        GatewayAccountAssertions.assertGettingAccountReturnsProviderName.f(port,
                URI.create(response.extract().header("Location").replace("https", "http")), //Scheme on links back are forced to be https
                testProvider, GatewayAccountEntity.Type.TEST);
        
        assertGatewayAccountCredentialsAreEmptyInDB(response, databaseTestHelper);
        return response.extract().path("gateway_account_id");
    }

    private static void assertGatewayAccountCredentialsAreEmptyInDB(ValidatableResponse response, DatabaseTestHelper databaseTestHelper) {
        String gateway_account_id = response.extract().path("gateway_account_id");
        Map<String, String> accountCredentials = databaseTestHelper.getAccountCredentials(Long.valueOf(gateway_account_id));
        assertThat(accountCredentials, is(new HashMap<>()));
    }
}
