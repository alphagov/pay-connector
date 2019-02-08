package uk.gov.pay.connector.it.util.gatewayaccount;

import io.restassured.response.ValidatableResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class GatewayAccountAssertions {
    
    public static final fj.F5<ValidatableResponse, GatewayAccountEntity.Type, String, String, String, Void> assertGatewayAccountCreation =
            (response, expectedType, expectedDescription, expectedAnalyticsId, expectedName) -> {
                String accountId = response.extract().path("gateway_account_id");
                String urlSlug = "api/accounts/" + accountId;
                response.header("Location", containsString(urlSlug))
                        .body("gateway_account_id", containsString(accountId))
                        .body("type", is(expectedType.toString()))
                        .body("description", is(expectedDescription))
                        .body("service_name", is(expectedName))
                        .body("analytics_id", is(expectedAnalyticsId))
                        .body("corporate_credit_card_surcharge_amount", is(nullValue()))
                        .body("corporate_debit_card_surcharge_amount", is(nullValue()))
                        .body("links[0].href", containsString(urlSlug))
                        .body("links[0].rel", is("self"))
                        .body("links[0].method", is("GET"));
                return null;
            }; 
    
    public static final fj.F4<Integer, URI, String, GatewayAccountEntity.Type, Void> assertGettingAccountReturnsProviderName = 
            (connectorPort, uri, expectedProviderName, expectedType) -> {
                given().port(connectorPort).contentType(JSON)
                        .get(uri) //Scheme on links back are forced to be https
                        .then()
                        .statusCode(200)
                        .contentType(JSON)
                        .body("payment_provider", is(expectedProviderName))
                        .body("gateway_account_id", is(notNullValue()))
                        .body("type", is(expectedType.toString()));
                return null;
            };
}
