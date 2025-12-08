package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonApplePayAuthorisationDetails;

public class CardResourceAuthoriseApplePayIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    @Test
    void shouldAuthoriseCharge_ForApplePay() {
        var chargeId = testBaseExtension.createNewChargeWithNoGatewayTransactionIdOrEmailAddress(ENTERING_CARD_DETAILS);
        var email = "mr@payment.test";
        shouldAuthoriseChargeForApplePay(chargeId, "mr payment", email)
                .statusCode(200);
        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(chargeId);
        assertThat(charge.get("email"), is(email));
    }

    @Test
    void shouldAuthoriseCharge_ForApplePay_withMinData() {
        var chargeId = testBaseExtension.createNewChargeWithNoGatewayTransactionIdOrEmailAddress(ENTERING_CARD_DETAILS);
        shouldAuthoriseChargeForApplePay(chargeId, null, null)
                .statusCode(200);
        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(chargeId);
        assertThat(charge.get("email"), is(nullValue()));
    }

    @Test
    void shouldAuthoriseChargeAppropriately_ForApplePay_withMagicValues() {
        provideMagicValues().forEach(arguments -> {
            var desc = arguments.getLeft();
            var expectedCode = arguments.getMiddle();
            var expectedStatus = arguments.getRight();
            var chargeId = testBaseExtension.createNewChargeWithDescriptionAndNoGatewayTransactionIdOrEmailAddress(ENTERING_CARD_DETAILS, desc);
            shouldAuthoriseChargeForApplePay(chargeId, null, null)
                    .statusCode(expectedCode);
            testBaseExtension.assertFrontendChargeStatusIs(chargeId, expectedStatus.getValue());
        });
    }

    @Test
    void shouldAuthoriseCharge_ForApplePay_andAddFakeExpiry_forSandboxProvider() {
        var chargeId = testBaseExtension.createNewChargeWithNoGatewayTransactionIdOrEmailAddress(ENTERING_CARD_DETAILS);
        shouldAuthoriseChargeForApplePay(chargeId, null, null)
                .statusCode(200);
        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(chargeId);
        assertThat(charge.get("expiry_date"), is("12/50"));
    }

    @Test
    void tooLongCardHolderName_shouldResultInBadRequest() {
        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String payload = buildJsonApplePayAuthorisationDetails(
                "tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars " +
                        "12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12",
                "mr@payment.test");

        app.givenSetup()
                .body(payload)
                .post(ITestBaseExtension.authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("message", contains("Card holder name must be a maximum of 255 chars"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void tooLongEmail_shouldResultInBadRequest() {
        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String payload = buildJsonApplePayAuthorisationDetails("Example Name",
                "tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars@" +
                        "12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12");

        app.givenSetup()
                .body(payload)
                .post(testBaseExtension.authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("message", contains("Email must be a maximum of 254 chars"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    private ValidatableResponse shouldAuthoriseChargeForApplePay(String chargeId, String cardHolderName, String email) {

        return app.givenSetup()
                .body(buildJsonApplePayAuthorisationDetails(cardHolderName, email))
                .post(testBaseExtension.authoriseChargeUrlForApplePay(chargeId))
                .then();
    }

    private static Stream<Triple<String, Integer, ChargeStatus>> provideMagicValues() {
        return Stream.of(
                Triple.of("whatever", 200, AUTHORISATION_SUCCESS),
                Triple.of("DECLINED", 400, AUTHORISATION_REJECTED),
                Triple.of("declined", 400, AUTHORISATION_REJECTED),
                Triple.of("REFUSED", 400, AUTHORISATION_REJECTED),
                Triple.of("refused", 400, AUTHORISATION_REJECTED),
                Triple.of("ERROR", 402, AUTHORISATION_ERROR),
                Triple.of("error", 402, AUTHORISATION_ERROR)
        );
    }
}
