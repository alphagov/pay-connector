package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.it.dao.DatabaseFixtures.withDatabaseTestHelper;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

public class GatewayCleanupResourceIT {
    @RegisterExtension
    public static ITestBaseExtension app = new ITestBaseExtension("worldpay");

    @Test
    public void shouldCleanUpChargesInAuthorisationErrorStates() {
        String chargeId1 = app.addCharge(AUTHORISATION_REJECTED);
        String chargeId2 = app.addCharge(AUTHORISATION_ERROR);
        String chargeId3 = app.addCharge(AUTHORISATION_UNEXPECTED_ERROR);
        String chargeId4 = app.addCharge(AUTHORISATION_TIMEOUT);

        // add a non-Worldpay charge that shouldn't be picked up
        var sandboxAccount = withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(RandomUtils.nextLong())
                .withPaymentProvider(SANDBOX.getName())
                .insert();

        app.getDatabaseTestHelper().addCharge(anAddChargeParams()
                .withGatewayAccountId(String.valueOf(sandboxAccount.getAccountId()))
                .withPaymentProvider("sandbox")
                .withStatus(AUTHORISATION_ERROR)
                .withGatewayCredentialId(app.getCredentialParams().getId())
                .build());

        app.getWorldpayMockClient().mockCancelSuccess();
        app.getWorldpayMockClient().mockAuthorisationQuerySuccess();

        given().port(app.getLocalPort())
                .post("/v1/tasks/gateway-cleanup-sweep?limit=10")
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("cleanup-success", is(3))
                .body("cleanup-failed", is(0));

        String chargeStatus1 = app.getDatabaseTestHelper().getChargeStatusByExternalId(chargeId1);
        String chargeStatus2 = app.getDatabaseTestHelper().getChargeStatusByExternalId(chargeId2);
        String chargeStatus3 = app.getDatabaseTestHelper().getChargeStatusByExternalId(chargeId3);
        String chargeStatus4 = app.getDatabaseTestHelper().getChargeStatusByExternalId(chargeId4);
        
        assertThat(chargeStatus1, is(AUTHORISATION_REJECTED.getValue()));
        assertThat(chargeStatus2, is(AUTHORISATION_ERROR_CANCELLED.getValue()));
        assertThat(chargeStatus3, is(AUTHORISATION_ERROR_CANCELLED.getValue()));
        assertThat(chargeStatus4, is(AUTHORISATION_ERROR_CANCELLED.getValue()));

        List<String> events1 = app.getDatabaseTestHelper().getInternalEvents(chargeId1);
        List<String> events2 = app.getDatabaseTestHelper().getInternalEvents(chargeId2);
        List<String> events3 = app.getDatabaseTestHelper().getInternalEvents(chargeId3);
        List<String> events4 = app.getDatabaseTestHelper().getInternalEvents(chargeId4);

        assertThat(events1, contains(AUTHORISATION_REJECTED.getValue()));
        assertThat(events2, contains(AUTHORISATION_ERROR.getValue(), AUTHORISATION_ERROR_CANCELLED.getValue()));
        assertThat(events3, contains(AUTHORISATION_UNEXPECTED_ERROR.getValue(), AUTHORISATION_ERROR_CANCELLED.getValue()));
        assertThat(events4, contains(AUTHORISATION_TIMEOUT.getValue(), AUTHORISATION_ERROR_CANCELLED.getValue()));
    }

    @Test
    public void shouldLimitChargesCleanedUp() {
        app.addCharge(AUTHORISATION_ERROR);
        app.addCharge(AUTHORISATION_ERROR);
        app.addCharge(AUTHORISATION_ERROR);

        app.getWorldpayMockClient().mockCancelSuccess();
        app.getWorldpayMockClient().mockAuthorisationQuerySuccess();

        given().port(app.getLocalPort())
                .post("/v1/tasks/gateway-cleanup-sweep?limit=2")
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("cleanup-success", is(2))
                .body("cleanup-failed", is(0));
    }

    @Test
    public void shouldReturn422WhenLimitQueryParamMissing() {
        given().port(app.getLocalPort())
                .post("/v1/tasks/gateway-cleanup-sweep")
                .then()
                .statusCode(422)
                .contentType(JSON)
                .body("message", containsInAnyOrder("Parameter [limit] is required"));
    }
}
