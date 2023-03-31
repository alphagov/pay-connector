package uk.gov.pay.connector.it.resources;

import com.github.tomakehurst.wiremock.matching.UrlPattern;
import io.restassured.response.ValidatableResponse;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCancelOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForQueryOrder;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static io.restassured.http.ContentType.JSON;
import static java.time.temporal.ChronoUnit.MINUTES;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_MAINTENANCE_ORDER;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_QUERY_ORDER;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
@Ignore
public class ChargeExpiryResourceEpdqIT extends ChargingITestBase {

    public ChargeExpiryResourceEpdqIT() {
        super("epdq");
    }

    @Test
    public void shouldExpireWithGatewayIfExistsInCancellableStateWithGatewayEvenIfChargeIsPreAuthorisation() {
        String chargeId = addCharge(ChargeStatus.AUTHORISATION_3DS_REQUIRED, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());

        epdqMockClient.mockAuthorisationQuerySuccess();
        epdqMockClient.mockCancelSuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        ValidatableResponse response = assertChargeStatusAndGetResponse(chargeId, EXPIRED);

        String gatewayTransactionId = response.extract().path("gateway_transaction_id").toString();

        verifyPostToPath(String.format("/epdq/%s", ROUTE_FOR_QUERY_ORDER),
                getExpectedRequestBodyToPspForQueryOrder(chargeId));
        verifyPostToPath(String.format("/epdq/%s", ROUTE_FOR_MAINTENANCE_ORDER),
                getExpectedRequestBodyToPspForMaintenanceOrder(gatewayTransactionId));
    }

    @Test
    public void shouldHandleCaseWhereEpdqRespondsWithUnknownStatus() {
        String chargeId = addCharge(ChargeStatus.AUTHORISATION_3DS_REQUIRED, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        epdqMockClient.mockUnknown();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        assertChargeStatusAndGetResponse(chargeId, EXPIRED);

        verifyPostToPath(String.format("/epdq/%s", ROUTE_FOR_QUERY_ORDER),
                getExpectedRequestBodyToPspForQueryOrder(chargeId));
    }

    @Test
    public void shouldUpdateChargeStatusToMatchTerminalStateOnGateway() {
        String chargeId = addCharge(AUTHORISATION_3DS_READY, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());

        epdqMockClient.mockAuthorisationQuerySuccessCaptured();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        assertChargeStatusAndGetResponse(chargeId, CAPTURED);

        verifyPostToPath(String.format("/epdq/%s", ROUTE_FOR_QUERY_ORDER),
                getExpectedRequestBodyToPspForQueryOrder(chargeId));
    }

    @Test
    public void shouldUpdateChargeStatusToMatchTerminalStateOnGatewayWhenNotAForceTransition() {
        String chargeId = addCharge(AUTHORISATION_3DS_READY, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());

        epdqMockClient.mockAuthorisationQuerySuccessAuthFailed();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        assertChargeStatusAndGetResponse(chargeId, EXPIRED);

        verifyPostToPath(String.format("/epdq/%s", ROUTE_FOR_QUERY_ORDER),
                getExpectedRequestBodyToPspForQueryOrder(chargeId));
    }

    private void verifyPostToPath(String path, String body) {
        wireMockServer.verify(
                postRequestedFor(
                        UrlPattern.fromOneOf(
                                null,
                                null,
                                path,
                                null
                        )
                )
                .withRequestBody(matching(body))
                .withHeader("Authorization", equalTo("Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ="))
        );
    }

    private ValidatableResponse assertChargeStatusAndGetResponse(String chargeId, ChargeStatus chargeStatus) {
        ValidatableResponse response = connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .getCharge();

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("state.status", is(chargeStatus.toExternal().getStatus()));

        return response;
    }

    private String getExpectedRequestBodyToPspForMaintenanceOrder(String gatewayTransactionId) {
        EpdqPayloadDefinitionForCancelOrder order = new EpdqPayloadDefinitionForCancelOrder();
        order.setPayId(gatewayTransactionId);
        order.setPspId(credentials.get(CREDENTIALS_MERCHANT_ID).toString());
        order.setUserId(credentials.get(CREDENTIALS_USERNAME).toString());
        order.setPassword(credentials.get(CREDENTIALS_PASSWORD).toString());
        order.setShaInPassphrase(credentials.get(CREDENTIALS_SHA_IN_PASSPHRASE).toString());
        return order.createGatewayOrder().getPayload();
    }

    private String getExpectedRequestBodyToPspForQueryOrder(String chargeId) {
        EpdqPayloadDefinitionForQueryOrder order = new EpdqPayloadDefinitionForQueryOrder();
        order.setOrderId(chargeId);
        order.setPspId(credentials.get(CREDENTIALS_MERCHANT_ID).toString());
        order.setUserId(credentials.get(CREDENTIALS_USERNAME).toString());
        order.setPassword(credentials.get(CREDENTIALS_PASSWORD).toString());
        order.setShaInPassphrase(credentials.get(CREDENTIALS_SHA_IN_PASSPHRASE).toString());
        return order.createGatewayOrder().getPayload();
    }

}
