package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.hamcrest.Matchers;
import org.junit.Test;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountJpaResourceTest extends GatewayAccountResourceTestBase {

    @Test
    public void createGatewayAccountWithoutPaymentProviderDefaultsToSandbox() throws Exception {

        String payload = toJson(ImmutableMap.of("name", "test account"));

        ValidatableResponse response = givenSetup()
                .body(payload)
                .post(ACCOUNTS_RESOURCE_JPA)
                .then()
                .statusCode(201);

        assertCorrectAccountLocationIn(response);

        assertGettingAccountReturnsProviderName(response, "sandbox");
    }

    @Test
    public void getAccountShouldReturn404IfAccountIdIsUnknown() throws Exception {

        String unknownAcocuntId = "92348739";

        givenSetup()
                .get(ACCOUNTS_RESOURCE_JPA + unknownAcocuntId)
                .then()
                .statusCode(404)
                .body("message", is(String.format("Account with id %s not found.", unknownAcocuntId)));
    }

    @Test
    public void getAccountShouldReturn400IfAccountIdIsNotNumeric() throws Exception {

        String unknownAcocuntId = "92348739wsx673hdg";

        givenSetup()
                .get(ACCOUNTS_RESOURCE_JPA + unknownAcocuntId)
                .then()
                .statusCode(400)
                .body("message", is(String.format("Invalid account ID format. was [%s]. Should be a number", unknownAcocuntId)));
    }

    @Test
    public void getAccountShouldNotReturnCredentials() throws Exception {

        String gatewayAccountId = createAGatewayAccountFor("worldpay");

        givenSetup()
                .get(ACCOUNTS_API_URL + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("credentials", Matchers.is(nullValue()));
    }

    @Test
    public void createAccountShouldFailIfPaymentProviderIsNotSandboxOfWorldpay() throws Exception {
        String testProvider = "random";
        String payload = toJson(ImmutableMap.of("payment_provider", testProvider));

        givenSetup()
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", Matchers.is(format("Unsupported payment provider %s.", testProvider)));
    }

    @Test
    public void createAGatewayAccountForSandbox() throws Exception {

        createJpaGatewayAccountFor("sandbox");
    }

    @Test
    public void createAGatewayAccountForWorldpay() throws Exception {

        createJpaGatewayAccountFor("worldpay");
    }

    @Test
    public void createAGatewayAccountForSmartpay() throws Exception {

        createJpaGatewayAccountFor("smartpay");
    }

}
