package uk.gov.pay.connector.it.resources;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.util.Arrays;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.*;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.service.CardCancelService.SYSTEM_CANCELLABLE_STATUSES;

public class ChargeCancelResourceITest {
    private String accountId = "66757943593456";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private RestAssuredClient restFrontendCall;
    private RestAssuredClient restApiCall;

    @Before
    public void setupGatewayAccount() {
        restFrontendCall = new RestAssuredClient(app, accountId);
        restApiCall = new RestAssuredClient(app, accountId);
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "sandbox");
    }

    @Test
    public void respondWith204_whenCancellationSuccessful() {
        asList(SYSTEM_CANCELLABLE_STATUSES)
                .forEach(status -> {
                    String chargeId = createNewChargeWithStatus(status);
                    restApiCall
                            .withChargeId(chargeId)
                            .postChargeCancellation()
                            .statusCode(NO_CONTENT.getStatusCode()); //assertion

                    restApiCall
                            .withChargeId(chargeId)
                            .getCharge()
                            .body("state.status", is("cancelled"))
                            .body("state.message", is("Payment was cancelled by the service"))
                            .body("state.code", is("P0040"));

                    restFrontendCall
                            .withChargeId(chargeId)
                            .getFrontendCharge()
                            .body("status", is(SYSTEM_CANCELLED.getValue()));
                });
    }

    @Test
    public void respondWith400_whenNotCancellableState() {
        ChargeStatus[] statuses = {
                AUTHORISATION_REJECTED,
                AUTHORISATION_ERROR,
                CAPTURED,
                CAPTURE_SUBMITTED,
                CAPTURE_ERROR,
                EXPIRE_CANCEL_FAILED,
                CANCEL_ERROR,
                SYSTEM_CANCELLED
        };

        Arrays.asList(statuses).stream()
                .forEach(notCancellableState -> {
                    String chargeId = createNewChargeWithStatus(notCancellableState);
                    String expectedMessage = "Charge not in correct state to be processed, " + chargeId;
                    restApiCall
                            .withChargeId(chargeId)
                            .postChargeCancellation()
                            .statusCode(BAD_REQUEST.getStatusCode())
                            .and()
                            .contentType(JSON)
                            .body("message", is(expectedMessage));
                });
    }

    @Test
    public void respondWith202_whenCancelAlreadyInProgress() {
        String chargeId = createNewChargeWithStatus(CANCEL_READY);
        String expectedMessage = "Cancellation for charge already in progress, " + chargeId;
        restApiCall
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(ACCEPTED.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    @Test
    public void respondWith404_whenPaymentNotFound() {
        String unknownChargeId = "2344363244";
        restApiCall
                .withChargeId(unknownChargeId)
                .postChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is("Charge with id [" + unknownChargeId + "] not found."));
    }

    @Test
    public void respondWith400__IfAccountIdIsMissing() {
        String chargeId = createNewChargeWithStatus(CREATED);
        String expectedMessage = "HTTP 404 Not Found";

        restApiCall
                .withAccountId("")
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    @Test
    public void respondWith404__IfAccountIdIsNonNumeric() {
        String chargeId = createNewChargeWithStatus(CREATED);
        String expectedMessage = "HTTP 404 Not Found";

        restApiCall
                .withAccountId("ABSDCEFG")
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    @Test
    public void respondWith404__IfChargeIdDoNotBelongToAccount() {
        String chargeId = createNewChargeWithStatus(CREATED);
        String expectedMessage = format("Charge with id [%s] not found.", chargeId);

        restApiCall
                .withAccountId("12345")
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    @Test
    public void respondWith400_IfChargeIsExpired() {
        String chargeId = createNewChargeWithStatus(EXPIRED);
        String expectedMessage = format("Cancellation for charge failed as already expired, %s", chargeId);

        restApiCall
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(BAD_REQUEST.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    private String createNewChargeWithStatus(ChargeStatus status) {
        String chargeId = RandomIdGenerator.newId();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, status, "http://not.relevant", null);
        return chargeId;
    }
}
