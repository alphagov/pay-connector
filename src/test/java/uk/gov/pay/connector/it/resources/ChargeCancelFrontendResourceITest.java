package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.util.List;
import java.util.Random;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

/**
 * There are currently no integration tests for case when payment gateway fails. However, this case is unit tested
 */
public class ChargeCancelFrontendResourceITest {
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "66757943593456";
    private RestAssuredClient connectorRestApi;
    private RestAssuredClient restFrontendCall;


    public static final ChargeStatus[] USER_CANCELLABLE_STATUSES =
            new ChargeStatus[]{
                    ENTERING_CARD_DETAILS,
                    AUTHORISATION_SUCCESS,
            };


    private static final List<ChargeStatus> NON_CANCELLABLE_STATUSES = ImmutableList.of(
            AUTHORISATION_REJECTED,
            AUTHORISATION_ERROR,
            CAPTURED,
            CAPTURE_SUBMITTED,
            CAPTURE_ERROR,
            EXPIRE_CANCEL_FAILED,
            SYSTEM_CANCEL_ERROR,
            SYSTEM_CANCELLED
    );

    @Before
    public void setupGatewayAccount() {
        connectorRestApi = new RestAssuredClient(app, accountId);
        restFrontendCall = new RestAssuredClient(app, accountId);
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "sandbox");
    }

    @Test
    public void respondWith204_whenCancellationSuccessful() {
        asList(USER_CANCELLABLE_STATUSES)
                .forEach(status -> {
                    String chargeId = createNewChargeWithStatus(status);
                    connectorRestApi
                            .withChargeId(chargeId)
                            .withAccountId(accountId)
                            .postFrontendChargeCancellation()
                            .statusCode(NO_CONTENT.getStatusCode());
                    connectorRestApi
                            .withChargeId(chargeId)
                            .getCharge()
                            .body("state.status", is("failed"))
                            .body("state.message", is("Payment was cancelled by the user"))
                            .body("state.code", is("P0030"));

                    restFrontendCall
                            .withChargeId(chargeId)
                            .getFrontendCharge()
                            .body("status", is(USER_CANCELLED.getValue()));
                    List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
                    assertThat(events.size(), isOneOf(2, 3));
                    assertThat(events, hasItems(status.getValue(), USER_CANCELLED.getValue()));

                    if (status.equals(AUTHORISATION_SUCCESS)) {
                        assertThat(events, hasItem(USER_CANCEL_READY.getValue()));
                    }
                });
    }

    @Test
    public void respondWith400_whenNotCancellableState() {
        NON_CANCELLABLE_STATUSES
                .forEach(nonCancellableStatus -> {
                    String chargeId = createNewChargeWithStatus(nonCancellableStatus);
                    String incorrectStateMessage = "Charge not in correct state to be processed, " + chargeId;

                    connectorRestApi
                            .withChargeId(chargeId)
                            .postFrontendChargeCancellation()
                            .statusCode(BAD_REQUEST.getStatusCode())
                            .and()
                            .contentType(JSON)
                            .body("message", is(incorrectStateMessage));

                });
    }

    private String createNewChargeWithStatus(ChargeStatus status) {
        String externalChargeId = RandomIdGenerator.newId();
        long chargeId = new Random().nextInt(100000);
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, 500, status, "http://not.relevant", null);
        app.getDatabaseTestHelper().addEvent(chargeId, status.getValue());
        return externalChargeId;
    }
}
