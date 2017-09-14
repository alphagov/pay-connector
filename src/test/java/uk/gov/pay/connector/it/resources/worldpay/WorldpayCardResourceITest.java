package uk.gov.pay.connector.it.resources.worldpay;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matchers;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.jayway.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class WorldpayCardResourceITest extends ChargingITestBase {

    private String validAuthorisationDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "visa");

    public WorldpayCardResourceITest() {
        super("worldpay");
    }

    @Test
    public void shouldAuthoriseCharge_ForValidAuthorisationDetails() throws Exception {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpay.mockAuthorisationSuccess();

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldReturnStatusAsRequires3ds() {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpay.mockAuthorisationRequires3ds();

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.toString());
    }

    @Test
    public void shouldNotAuthorise_AWorldpayErrorCard() throws Exception {
        String cardDetailsRejectedByWorldpay = buildJsonAuthorisationDetailsFor("REFUSED", "4444333322221111", "visa");

        worldpay.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(cardDetailsRejectedByWorldpay, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldDeferCaptureCardPayment_IfAsynchronousFeatureFlagIsOn() {
        String chargeId = authoriseNewCharge();

        worldpay.mockCaptureSuccess();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }

    @Test
    public void shouldAuthoriseCharge_For3dsRequiredCharge() {
        String chargeId = createNewCharge(AUTHORISATION_3DS_REQUIRED);
        worldpay.mockAuthorisationSuccess();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldReturnStatus400_WhenAuthorisationFails() {
        String chargeId = createNewCharge(AUTHORISATION_3DS_REQUIRED);

        String expectedErrorMessage = "This transaction was declined.";
        worldpay.mockAuthorisationFailure();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(expectedErrorMessage));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldAuthoriseChargesConcurrentlyGettingExpectedChargeStatus() throws Exception {

        List<String> charges = newArrayList();
        for (int i = 0; i < 500; i++) {
            String externalId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
            System.out.println("created charge with externalId = " + externalId);
            charges.add(externalId);
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);

        List<Future<Pair<String, ValidatableResponse>>> futuresResponsesForAuth = new ArrayList<>();
        //List<Future<Pair<String, ValidatableResponse>>> futuresResponsesForGet = new ArrayList<>();

        worldpay.mockAuthorisationSuccess();

        for (String charge : charges) {
            futuresResponsesForAuth.add(executor.submit(anPatchAndAuthorisationCallableFor(charge)));
            //futuresResponsesForGet.add(executor.submit(getChargeCallableFor(charge)));
        }

        Map<String, Integer> results = newHashMap();

        for (Future<Pair<String, ValidatableResponse>> futureAuth : futuresResponsesForAuth) {
            Pair<String, ValidatableResponse> pair = futureAuth.get();
            int statusCode = pair.getRight().extract().statusCode();

            results.put(pair.getLeft(), statusCode);

            if(statusCode != 200) {
                System.out.println("Madarfakaaaa = " + statusCode);
            }
            /*System.out.println("-------------------------------------------\n");
            System.out.println("Charge     = " + pair.getLeft());
            System.out.println("statusCode = " + statusCode);
            System.out.println("-------------------------------------------\n");
*/
            //assertThat(statusCode, is(200));
            //assertFrontendChargeStatusIs(pair.getLeft(), AUTHORISATION_SUCCESS.toString());
        }

       /* for (Future<Pair<String, ValidatableResponse>> futureGet : futuresResponsesForGet) {
            int statusCode = futureGet.get().getRight().extract().statusCode();
            assertThat(statusCode, is(200));
        }*/


        print(results);
    }

    private void print(Map<String, Integer> results) {
        System.out.println("-----------------------------------------------------------------------");
        System.out.println("results.size() = " + results.size());
        for (Map.Entry<String, Integer> stringIntegerEntry : results.entrySet()) {
            System.out.println(stringIntegerEntry.getKey() +" : "+ stringIntegerEntry.getValue());
        }
    }

    private Callable<Pair<String, ValidatableResponse>> anPatchAndAuthorisationCallableFor(String externalChargeId) {
        return () -> {

            ValidatableResponse responseToPatch = connectorRestApi
                    .withChargeId(externalChargeId)
                    .patchCharge(createPatch("replace", "email", randomAlphabetic(10) + "@example.com"));
            System.out.println("responseToPatch.extract().statusCode() = " + responseToPatch.extract().statusCode());

            ValidatableResponse response = givenSetup()
                    .body(validAuthorisationDetails)
                    .post(authoriseChargeUrlFor(externalChargeId))
                    .then();

            return Pair.of(externalChargeId, response);
        };
    }

    private Callable<Pair<String, ValidatableResponse>> pathChargeCallableFor(String externalChargeId) {
        return () -> {
            String email = randomAlphabetic(10) + "@example.com";
            ValidatableResponse response = connectorRestApi
                    .withChargeId(externalChargeId)
                    .patchCharge(createPatch("replace", "email", email));
            return Pair.of(externalChargeId, response);
        };
    }

    private static String createPatch(String op, String path, String value) {
        return toJson(ImmutableMap.of("op", op, "path", path, "value", value));
    }

    private Callable<Pair<String, ValidatableResponse>> getChargeCallableFor(String externalChargeId) {
        return () -> Pair.of(externalChargeId, getCharge(externalChargeId));
    }
}
