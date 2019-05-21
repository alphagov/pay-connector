package uk.gov.pay.connector.it.resources;


import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.ZonedDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_MAINTENANCE_ORDER;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_QUERY_ORDER;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargeExpiryResourceEpdqITest  extends ChargingITestBase {

    public ChargeExpiryResourceEpdqITest() {
        super("epdq");
    }

    @Test
    public void shouldExpireChargeInAuthorisationErrorState() {
        String extChargeId1 = addCharge(AUTHORISATION_ERROR, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());

        epdqMockClient.mockCancelSuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId1)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(extChargeId1))
                .body("state.status", is(EXPIRED.toExternal().getStatus()));

        wireMockRule.verify(1, postRequestedFor(urlEqualTo("/epdq/maintenancedirect.asp")));
    }
    
    @Test
    public void shouldExpireWithGatewayIfExistsInCancellableStateWithGatewayEvenIfChargeIsPreAuthorisation() {
        String chargeId = addCharge(ChargeStatus.AUTHORISATION_3DS_REQUIRED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());

        epdqMockClient.mockAuthorisationQuerySuccess();
        epdqMockClient.mockCancelSuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("state.status", is(EXPIRED.toExternal().getStatus()));

        wireMockRule.verify(postRequestedFor(urlEqualTo(String.format("/epdq/%s", ROUTE_FOR_MAINTENANCE_ORDER))));
        wireMockRule.verify(postRequestedFor(urlEqualTo(String.format("/epdq/%s", ROUTE_FOR_QUERY_ORDER))));
    }

    @Test
    public void shouldHandleCaseWhereEpdqRespondsWithUnknownStatus() {
        String chargeId = addCharge(ChargeStatus.AUTHORISATION_3DS_REQUIRED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        epdqMockClient.mockUnknown();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("state.status", is(EXPIRED.toExternal().getStatus()));

        wireMockRule.verify(postRequestedFor(urlEqualTo(String.format("/epdq/%s", ROUTE_FOR_QUERY_ORDER))));
    }
}
