package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingXPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.restassured.http.ContentType.JSON;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.rules.WorldpayMockClient.WORLDPAY_URL;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CANCEL_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class DiscrepancyResourceIT extends ChargingITestBase {
    public DiscrepancyResourceIT() {
        super("worldpay");
    }

    @Test
    public void shouldReturnAllCharges_whenRequestDiscrepancyReport() {
        String chargeId = addCharge(ChargeStatus.EXPIRED, "ref", Instant.now().minus(1, HOURS), "irrelevant");
        String chargeId2 = addCharge(ChargeStatus.AUTHORISATION_SUCCESS, "ref", Instant.now().minus(1, HOURS), "irrelevant");
        worldpayMockClient.mockAuthorisationQuerySuccess();

        List<JsonNode> results = connectorRestApiClient
                .getDiscrepancyReport(toJson(Arrays.asList(chargeId, chargeId2)))
                .statusCode(200)
                .contentType(JSON)
                .extract().body().jsonPath().getList(".", JsonNode.class);

        assertEquals(2, results.size());

        assertEquals("AUTHORISATION SUCCESS", results.get(0).get("gatewayStatus").asText());
        assertEquals("EXPIRED", results.get(0).get("payStatus").asText());
        assertEquals(chargeId, results.get(0).get("chargeId").asText());
        assertEquals("Worldpay query response (orderCode: transaction-id, lastEvent: AUTHORISED)", results.get(0).get("rawGatewayResponse").asText());
        assertEquals("EXTERNAL_SUBMITTED", results.get(0).get("gatewayExternalStatus").asText());
        assertEquals("EXTERNAL_FAILED_EXPIRED", results.get(0).get("payExternalStatus").asText());

        assertEquals("AUTHORISATION SUCCESS", results.get(1).get("gatewayStatus").asText());
        assertEquals("AUTHORISATION SUCCESS", results.get(1).get("payStatus").asText());
        assertEquals(chargeId2, results.get(1).get("chargeId").asText());
        assertEquals("Worldpay query response (orderCode: transaction-id, lastEvent: AUTHORISED)", results.get(1).get("rawGatewayResponse").asText());
        assertEquals("EXTERNAL_SUBMITTED", results.get(1).get("gatewayExternalStatus").asText());
        assertEquals("EXTERNAL_SUBMITTED", results.get(1).get("payExternalStatus").asText());
    }

    @Test
    public void shouldReturnDiscrepencyReportForExpungedCharges() throws JsonProcessingException {
        DatabaseFixtures.TestCharge charge = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(getTestAccount())
                .withExternalChargeId("external_charge_id_10")
                .withTransactionId("gateway_transaction_id_")
                .withChargeStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withAuthorisationMode(AuthorisationMode.WEB);

        ledgerStub.returnLedgerTransaction(charge.getExternalChargeId(), charge, null);
        worldpayMockClient.mockAuthorisationQuerySuccess();

        List<JsonNode> results = connectorRestApiClient
                .getDiscrepancyReport(toJson(singletonList(charge.getExternalChargeId())))
                .statusCode(200)
                .contentType(JSON)
                .extract().body().jsonPath().getList(".", JsonNode.class);

        assertEquals(1, results.size());

        assertEquals("AUTHORISATION SUCCESS", results.get(0).get("gatewayStatus").asText());
        assertNull(results.get(0).get("payStatus"));
        assertEquals(charge.getExternalChargeId(), results.get(0).get("chargeId").asText());
        assertEquals("Worldpay query response (orderCode: transaction-id, lastEvent: AUTHORISED)", results.get(0).get("rawGatewayResponse").asText());
        assertEquals("EXTERNAL_SUBMITTED", results.get(0).get("gatewayExternalStatus").asText());
        assertEquals("submitted", results.get(0).get("payExternalStatus").asText());
    }

    @Test
    public void shouldReportOnChargesThatAreInErrorStatesInGatewayAccount() {
        String chargeId = addCharge(ChargeStatus.EXPIRED, "ref", Instant.now().minus(8, DAYS), "irrelevant");
        mockAuthorisationQueryAndCancel(load(WORLDPAY_AUTHORISATION_FAILED_RESPONSE));

        List<JsonNode> results = connectorRestApiClient
                .getDiscrepancyReport(toJson(Arrays.asList(chargeId)))
                .statusCode(200)
                .contentType(JSON)
                .extract().body().jsonPath().getList(".", JsonNode.class);

        assertEquals(1, results.size());

        JsonNode statusComparisonForChargeId = results.get(0);
        assertEquals("AUTHORISATION REJECTED", statusComparisonForChargeId.get("gatewayStatus").asText());
        assertEquals("EXPIRED", statusComparisonForChargeId.get("payStatus").asText());
        assertEquals(chargeId, statusComparisonForChargeId.get("chargeId").asText());
        assertEquals("Worldpay query response (orderCode: MyUniqueTransactionId!12, lastEvent: REFUSED, ISO8583ReturnCode code: 5, ISO8583ReturnCode description: REFUSED)", 
                statusComparisonForChargeId.get("rawGatewayResponse").asText());
        assertEquals("EXTERNAL_FAILED_REJECTED", statusComparisonForChargeId.get("gatewayExternalStatus").asText());
        assertEquals("EXTERNAL_FAILED_EXPIRED", statusComparisonForChargeId.get("payExternalStatus").asText());
    }
    
    @Test
    public void should404_whenAChargeIdDoesntExist() {
        worldpayMockClient.mockAuthorisationQuerySuccess();

        ledgerStub.returnTransactionNotFound("nonExistentId");
        connectorRestApiClient
                .getDiscrepancyReport(toJson(singletonList("nonExistentId")))
                .statusCode(404);
    }

    @Test
    public void shouldProcessDiscrepanciesWherePayStateIsExpiredAndGatewayStateIsAuthorised() {
        String chargeId = addCharge(ChargeStatus.EXPIRED, "ref", Instant.now().minus(8, DAYS), "irrelevant");
        String chargeId2 = addCharge(ChargeStatus.EXPIRED, "ref", Instant.now().minus(8, DAYS), "irrelevant");
        mockAuthorisationQueryAndCancel(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        List<JsonNode> results = connectorRestApiClient
                .resolveDiscrepancies(toJson(Arrays.asList(chargeId, chargeId2)))
                .statusCode(200)
                .contentType(JSON)
                .extract().body().jsonPath().getList(".", JsonNode.class);

        assertEquals(2, results.size());

        JsonNode statusComparisonForChargeId = results.get(0);
        assertEquals("AUTHORISATION SUCCESS", statusComparisonForChargeId.get("gatewayStatus").asText());
        assertEquals("EXPIRED", statusComparisonForChargeId.get("payStatus").asText());
        assertEquals(chargeId, statusComparisonForChargeId.get("chargeId").asText());
        assertEquals("Worldpay query response (orderCode: transaction-id, lastEvent: AUTHORISED)", 
                statusComparisonForChargeId.get("rawGatewayResponse").asText());
        assertEquals("EXTERNAL_SUBMITTED", statusComparisonForChargeId.get("gatewayExternalStatus").asText());
        assertEquals("EXTERNAL_FAILED_EXPIRED", statusComparisonForChargeId.get("payExternalStatus").asText());
        assertTrue(statusComparisonForChargeId.get("processed").asBoolean());
    }
    
    private void mockAuthorisationQueryAndCancel(String authQueryResponse) {
        wireMockServer.resetAll();
        wireMockServer.stubFor(post(urlPathEqualTo(WORLDPAY_URL)).inScenario("process discrepancies")
                .whenScenarioStateIs(STARTED)
                .withRequestBody(matchingXPath("//orderInquiry[@orderCode]"))
                .willReturn(aResponse().withHeader(CONTENT_TYPE, TEXT_XML).withStatus(200).withBody(authQueryResponse))
                .willSetStateTo("payment status queried"));

        wireMockServer.stubFor(post(urlPathEqualTo(WORLDPAY_URL)).inScenario("process discrepancies")
                .whenScenarioStateIs("payment status queried")
                .willReturn(aResponse().withHeader(CONTENT_TYPE, TEXT_XML).withStatus(200).withBody(load(WORLDPAY_CANCEL_SUCCESS_RESPONSE)))
                .willSetStateTo(STARTED));
    }
}
