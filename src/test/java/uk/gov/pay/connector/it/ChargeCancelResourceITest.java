package uk.gov.pay.connector.it;

import com.google.common.collect.ImmutableList;
import com.jayway.restassured.response.ValidatableResponse;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.ChargeStatus;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import java.util.Arrays;
import java.util.List;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.apache.commons.lang3.BooleanUtils.negate;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.ChargeStatus.READY_FOR_CAPTURE;
import static uk.gov.pay.connector.model.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_SYSTEM_CANCELLED;

public class ChargeCancelResourceITest {

    private static final List<ChargeStatus> CANCELLABLE_STATES = ImmutableList.of(
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS, AUTHORISATION_SUBMITTED, READY_FOR_CAPTURE
    );

    private String accountId = "66757943593456";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String cancelChargePath(String chargeId) {
        return "/v1/api/charges/" + chargeId + "/cancel";
    }

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway for cancellation");
    }

    @Test
    public void respondWith204_whenCancellationSuccessful() {
        CANCELLABLE_STATES.forEach(status -> {
            String chargeId = createNewChargeWithStatus(status);

            assertPostCancelHasStatus(chargeId, 204);
            assertFrontendChargeStatusIs(chargeId, SYSTEM_CANCELLED.getValue());
            assertApiStatusIs(chargeId, EXT_SYSTEM_CANCELLED.getValue());
        });
    }

    @Test
    public void respondWith400_whenNotCancellableState() {
        Arrays.stream(ChargeStatus.values())
                .filter(status -> negate(CANCELLABLE_STATES.contains(status)))
                .forEach(notCancellableState -> {
                    String chargeId = createNewChargeWithStatus(notCancellableState);
                    assertPostCancelHasStatus(chargeId, 400)
                            .and()
                            .contentType(JSON)
                            .body("message", is("Cannot cancel a charge with status " + notCancellableState.getValue() + "."));
                });
    }

    @Test
    public void respondWith404_whenPaymentNotFound() {
        String unknownChargeId = "2344363244";
        assertPostCancelHasStatus(unknownChargeId, 404)
                .and()
                .contentType(JSON)
                .body("message", is("Charge with id [" + unknownChargeId + "] not found."));
    }

    private ValidatableResponse assertPostCancelHasStatus(String chargeId, int expectedStatusCode) {
        return given().port(app.getLocalPort())
                .post(cancelChargePath(chargeId))
                .then()
                .statusCode(expectedStatusCode);
    }

    private String createNewChargeWithStatus(ChargeStatus status) {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, status, "http://not.relevant");
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

}
