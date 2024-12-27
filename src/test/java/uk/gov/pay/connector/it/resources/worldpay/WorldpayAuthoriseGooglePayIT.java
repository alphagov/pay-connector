package uk.gov.pay.connector.it.resources.worldpay;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.paymentprocessor.resource.CardResource;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

public class WorldpayAuthoriseGooglePayIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app.getLocalPort(), app.getDatabaseTestHelper());

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    
    @BeforeEach
    void setUpLogger() {
        Logger root = (Logger) LoggerFactory.getLogger(CardResource.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }
    
    @Test
    void authorise_charge_success_google_pay() throws Exception {
        app.getWorldpayMockClient().mockAuthorisationSuccess();

        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(load("googlepay/example-auth-request.json"));

        testBaseExtension.givenSetup()
                .body(googlePayload)
                .post(testBaseExtension.authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(chargeId);
        assertThat(charge.get("email"), is(googlePayload.get("payment_info").get("email").asText()));
    }
    

    @Test
    void verify_auth_3ds_required_in_response_to_a_google_pay_request() throws Exception {
        app.getWorldpayMockClient().mockAuthorisationRequires3ds();

        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(load("googlepay/example-3ds-auth-request.json"));

        testBaseExtension.givenSetup()
                .body(googlePayload)
                .post(testBaseExtension.authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.getValue());
    }

    @Test
    void should_reject_authorisation_for_a_worldpay_error_card() throws JsonProcessingException {
        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode validPayload = Jackson.getObjectMapper().readTree(load("googlepay/example-auth-request.json"));
        app.getWorldpayMockClient().mockAuthorisationFailure();

        testBaseExtension.givenSetup()
                .body(validPayload)
                .post(testBaseExtension.authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains("This transaction was declined."))
                .body("error_identifier", is(ErrorIdentifier.AUTHORISATION_REJECTED.toString()));

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    void should_not_authorise_charge_for_invalid_google_pay_request() throws JsonProcessingException {
        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode invalidPayload = Jackson.getObjectMapper().readTree(
                load("googlepay/invalid-empty-signature-auth-request.json"));

        testBaseExtension.givenSetup()
                .body(invalidPayload)
                .post(testBaseExtension.authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(422)
                .body("message", contains("Field [signature] must not be empty"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }
}
