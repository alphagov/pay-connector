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

import static com.jayway.restassured.http.ContentType.JSON;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_USER_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.CardCancelService.CANCELLABLE_STATUSES;

/**
 * There are currently no integration tests for case when payment gateway fails. However, this case is unit tested
 */
public class ChargeCancelFrontendResourceITest {
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "66757943593456";
    private RestAssuredClient connectorRestApi;

    private static final List<ChargeStatus> NON_CANCELLABLE_STATUSES = ImmutableList.of(
            AUTHORISATION_REJECTED,
            AUTHORISATION_ERROR,
            CAPTURED,
            CAPTURE_SUBMITTED,
            CAPTURE_ERROR,
            EXPIRE_CANCEL_FAILED,
            CANCEL_ERROR,
            SYSTEM_CANCELLED
    );

    @Before
    public void setupGatewayAccount() {
        connectorRestApi = new RestAssuredClient(app, accountId);
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "sandbox");
    }

    @Test
    public void respondWith204_whenCancellationSuccessful() {
        asList(CANCELLABLE_STATUSES)
                .forEach(cancellableStatus -> {
                    String chargeId = createNewChargeWithStatus(cancellableStatus);
                    connectorRestApi
                            .withChargeId(chargeId)
                            .withAccountId(accountId)
                            .postFrontendChargeCancellation()
                            .statusCode(NO_CONTENT.getStatusCode());
                    connectorRestApi
                            .withChargeId(chargeId)
                            .getCharge()
                            .body("status", is(EXT_USER_CANCELLED.getValue()));
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
        String chargeId = RandomIdGenerator.newId();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, status, "http://not.relevant", null);
        return chargeId;
    }
}
