package uk.gov.pay.connector.it;

import com.jayway.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.ChargeStatus;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUBMITTED;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUCCESS;

public class ChargeCaptureResourceITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "49857943875938";

    private String chargeCaptureUrlFor(String unknownChargeId) {
        return "/v1/frontend/charges/" + unknownChargeId + "/capture";
    }

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway for capture");
    }

    @Test
    public void shouldConfirmCardPaymentIfChargeWasAuthorised() {
        String chargeId = authoriseNewCharge();

        givenSetup()
                .post(chargeCaptureUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, "CAPTURED");
        assertApiStatusIs(chargeId, "SUCCEEDED");
    }

    @Test
    public void shouldReturnErrorWithoutChangingChargeState_IfOriginalStateIsNotAuthSuccess() {
        String chargeIdNotAuthorized = createNewChargeWithStatus(AUTHORIZATION_SUBMITTED);

        givenSetup()
                .post(chargeCaptureUrlFor(chargeIdNotAuthorized))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is("Cannot capture a charge with status " + AUTHORIZATION_SUBMITTED.getValue() + "."));

        assertFrontendChargeStatusIs(chargeIdNotAuthorized, "AUTHORIZATION SUBMITTED");
    }

    @Test
    public void shouldReturn404iftheChargeCannotBeFound() {
        String unknownChargeId = "398579438759438";

        givenSetup()
                .post(chargeCaptureUrlFor(unknownChargeId))
                .then()
                .statusCode(404);
    }

    private String authoriseNewCharge() {
        return createNewChargeWithStatus(AUTHORIZATION_SUCCESS);
    }

    private String createNewChargeWithStatus(ChargeStatus status) {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();

        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, status);
        return chargeId;
    }

    private void assertFrontendChargeStatusIs(String chargeId, String status) {
        assertStatusIs("/v1/frontend/charges/" + chargeId, status);
    }

    private void assertApiStatusIs(String chargeId, String status) {
        assertStatusIs("/v1/api/charges/" + chargeId, status);
    }

    private void assertStatusIs(String url, String status) {
        given().port(app.getLocalPort())
                .get(url)
                .then()
                .body("status", is(status));
    }

    private RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }
}
