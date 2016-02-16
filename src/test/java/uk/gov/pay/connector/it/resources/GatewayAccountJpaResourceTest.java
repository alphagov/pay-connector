package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountJpaResourceTest extends GatewayAccountResourceTestBase {


    public static final String ACCOUNTS_RESOURCE_JPA = "/v1/api/jpa/accounts/";

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
}
