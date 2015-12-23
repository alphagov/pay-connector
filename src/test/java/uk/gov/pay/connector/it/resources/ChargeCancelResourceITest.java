package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.util.Arrays;
import java.util.List;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.*;
import static org.apache.commons.lang3.BooleanUtils.negate;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_SYSTEM_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;

public class ChargeCancelResourceITest {
    private static final List<ChargeStatus> CANCELLABLE_STATES = ImmutableList.of(
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS, AUTHORISATION_SUBMITTED, READY_FOR_CAPTURE
    );

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
        CANCELLABLE_STATES.forEach(status -> {
            String chargeId = createNewChargeWithStatus(status);
            restApiCall
                    .withChargeId(chargeId)
                    .postChargeCancellation()
                    .statusCode(NO_CONTENT.getStatusCode());
            restApiCall
                    .withChargeId(chargeId)
                    .getCharge()
                    .body("status", is(EXT_SYSTEM_CANCELLED.getValue()));
            restFrontendCall
                    .withChargeId(chargeId)
                    .getCharge()
                    .body("status", is(SYSTEM_CANCELLED.getValue()));
        });
    }

    @Test
    public void respondWith400_whenNotCancellableState() {
        Arrays.stream(ChargeStatus.values())
                .filter(status -> negate(CANCELLABLE_STATES.contains(status)))
                .forEach(notCancellableState -> {
                    String chargeId = createNewChargeWithStatus(notCancellableState);
                    String expectedMessage = "Cannot cancel a charge id [" + chargeId
                            + "]: status is [" + notCancellableState.getValue() + "].";
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
        String expectedMessage = "Invalid account Id";

        restApiCall
                .withAccountId("---garbage---")
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(BAD_REQUEST.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    @Test
    public void respondWith400__IfAccountIdIsNonNumeric() {
        String chargeId = createNewChargeWithStatus(CREATED);
        String expectedMessage = "Invalid account Id";

        restApiCall
                .withAccountId("ABSDCEFG")
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(BAD_REQUEST.getStatusCode())
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

    private String createNewChargeWithStatus(ChargeStatus status) {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, status, "http://not.relevant", null);
        return chargeId;
    }
}
