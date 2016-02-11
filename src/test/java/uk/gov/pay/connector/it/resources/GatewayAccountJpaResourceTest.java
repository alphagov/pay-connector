package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;

import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountJpaResourceTest extends GatewayAccountResourceTestBase {


    @Test
    public void createGatewayAccountWithoutPaymentProviderDefaultsToSandbox() throws Exception {

        String payload = toJson(ImmutableMap.of("name", "test account"));

        ValidatableResponse response = givenSetup()
                .body(payload)
                .post(ACCOUNTS_API_URL + "jpa")
                .then()
                .statusCode(201);

        assertCorrectAccountLocationIn(response);

        assertGettingAccountReturnsProviderName(response, "sandbox");
    }
}
