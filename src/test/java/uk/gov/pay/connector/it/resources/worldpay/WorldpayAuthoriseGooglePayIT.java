package uk.gov.pay.connector.it.resources.worldpay;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.paymentprocessor.resource.CardResource;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.io.IOException;
import java.util.Map;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class WorldpayAuthoriseGooglePayIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = AppWithPostgresAndSqsExtension.withPersistence();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app.getLocalPort(), app.getDatabaseTestHelper());

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
    
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
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(fixture("googlepay/example-auth-request.json"));

        app.givenSetup()
                .body(googlePayload)
                .post(ITestBaseExtension.authoriseChargeUrlForGooglePay(chargeId))
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
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(fixture("googlepay/example-3ds-auth-request.json"));

        app.givenSetup()
                .body(googlePayload)
                .post(ITestBaseExtension.authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.getValue());
    }

    @Test
    void should_reject_authorisation_for_a_worldpay_error_card() throws IOException {
        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode validPayload = Jackson.getObjectMapper().readTree(
                fixture("googlepay/example-auth-request.json"));
        app.getWorldpayMockClient().mockAuthorisationFailure();

        app.givenSetup()
                .body(validPayload)
                .post(ITestBaseExtension.authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains("This transaction was declined."))
                .body("error_identifier", is(ErrorIdentifier.AUTHORISATION_REJECTED.toString()));

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    void should_not_authorise_charge_for_invalid_google_pay_request() throws IOException {
        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode invalidPayload = Jackson.getObjectMapper().readTree(
                fixture("googlepay/invalid-empty-signature-auth-request.json"));

        app.givenSetup()
                .body(invalidPayload)
                .post(ITestBaseExtension.authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(422)
                .body("message", contains("Field [signature] must not be empty"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }
}
