package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.restassured.http.ContentType.JSON;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargeGatewayStatusComparisonReportITest extends ChargingITestBase {
    public ChargeGatewayStatusComparisonReportITest() {
        super("epdq");
    }

    @Test
    public void shouldReturnAllChargesInDiscrepantState_whenRequestDiscrepancyReport() {
        String chargeId = addCharge(ChargeStatus.EXPIRED, "ref", ZonedDateTime.now().minusHours(1), "irrelevant");
        String chargeId2 = addCharge(ChargeStatus.CREATED, "ref", ZonedDateTime.now().minusHours(1), "irrelevant");
        epdqMockClient.mockAuthorisationQuerySuccess();

        List<JsonNode> results = connectorRestApiClient
                .getDiscrepancyReport(toJson(Arrays.asList(chargeId, chargeId2)))
                .statusCode(200)
                .contentType(JSON)
                .extract().body().jsonPath().getList(".", JsonNode.class);

        assertEquals(2, results.size());


        assertEquals( "AUTHORISATION_SUCCESS", results.get(0).get("gatewayStatus").asText());
        assertEquals( "EXPIRED", results.get(0).get("payStatus").asText());
        assertEquals( chargeId, results.get(0).get("chargeId").asText());
        assertEquals( "ePDQ query response (PAYID: 3014644340, STATUS: 5)", results.get(0).get("rawGatewayResponse").asText());
        assertEquals( "EXTERNAL_SUBMITTED", results.get(0).get("gatewayExternalStatus").asText());
        assertEquals( "EXTERNAL_FAILED_EXPIRED", results.get(0).get("payExternalStatus").asText());

        assertEquals( "AUTHORISATION_SUCCESS", results.get(1).get("gatewayStatus").asText());
        assertEquals( "CREATED", results.get(1).get("payStatus").asText());
        assertEquals( chargeId2, results.get(1).get("chargeId").asText());
        assertEquals( "ePDQ query response (PAYID: 3014644340, STATUS: 5)", results.get(1).get("rawGatewayResponse").asText());
        assertEquals( "EXTERNAL_SUBMITTED", results.get(1).get("gatewayExternalStatus").asText());
        assertEquals( "EXTERNAL_CREATED", results.get(1).get("payExternalStatus").asText());
    }
    

    @Test
    public void should404_whenAChargeIdDoesntExist() {
        String chargeId = addCharge(ChargeStatus.EXPIRED, "ref", ZonedDateTime.now().minusHours(1), "irrelevant");
        epdqMockClient.mockAuthorisationQuerySuccess();

        connectorRestApiClient
                .getDiscrepancyReport(toJson(Collections.singletonList("chargeIds")))
                .statusCode(404);
    }
}
