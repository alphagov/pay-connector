package uk.gov.pay.connector.it.resources;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.paymentprocessor.resource.CardResource;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.io.IOException;
import java.util.List;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CardResourceAuthoriseGooglePayWorldpayIT extends ChargingITestBase {
    
    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

    public CardResourceAuthoriseGooglePayWorldpayIT() {
        super("sandbox");
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        Logger root = (Logger) LoggerFactory.getLogger(CardResource.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    public void authoriseGooglePayWorldpayShouldReturn422IfCardholderNameIsTooLong() throws Exception {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String payload = fixture("googlepay/example-auth-request.json").replace("Example Name", "tenchars12".repeat(26));
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(payload);

        givenSetup()
                .body(googlePayload)
                .post(authoriseChargeUrlForGooglePayWorldpay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("message", contains("Card holder name must be a maximum of 255 chars"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        verify(mockAppender, times(0)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(false));
    }

    @Test
    public void authoriseGooglePayWorldpayShouldReturn422IfEmailIsTooLong() throws Exception {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String payload = fixture("googlepay/example-auth-request.json")
                .replace("example@test.example","tenchars12".repeat(10) + "@" + "12tenchars".repeat(16));
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(payload);

        givenSetup()
                .body(googlePayload)
                .post(authoriseChargeUrlForGooglePayWorldpay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("message", contains("Email must be a maximum of 254 chars"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        verify(mockAppender, times(0)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(false));
    }
    
    @Test
    public void authoriseGooglePayWorldpayShouldReturn422IfProtocolVersionIsEmpty() throws IOException {
        String chargeId = "a-valid-chargeId";
        String payload = fixture("googlepay/example-auth-request.json").replace("ECv1", "");
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(payload);

        givenSetup()
                .body(googlePayload)
                .post(authoriseChargeUrlForGooglePayWorldpay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("message", contains("Field [protocol_version] must not be empty"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        verify(mockAppender, times(0)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(false));
    }

    @Test
    public void authoriseGooglePayWorldpayShouldReturn422IfSignedMessageIsEmpty() throws IOException {
        String chargeId = "a-valid-chargeId";
        String payload = fixture("googlepay/example-auth-request.json").replace("aSignedMessage", "");
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(payload);

        givenSetup()
                .body(googlePayload)
                .post(authoriseChargeUrlForGooglePayWorldpay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("message", contains("Field [signed_message] must not be empty"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        verify(mockAppender, times(0)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(false));
    }

    @Test
    public void authoriseGooglePayWorldpayShouldReturn422IfSignatureIsEmpty() throws IOException {
        String chargeId = "a-valid-chargeId";
        String exampleAuthRequest = fixture("googlepay/example-auth-request.json").replace("MEYCIQC+a+AzSpQGr42UR1uTNX91DQM2r7SeKwzNs0UPoeSrrQIhAPpSzHjYTvvJGGzWwli8NRyHYE/diQMLL8aXqm9VIrwl", "");
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(exampleAuthRequest);

        givenSetup()
                .body(googlePayload)
                .post(authoriseChargeUrlForGooglePayWorldpay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("message", contains("Field [signature] must not be empty"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        verify(mockAppender, times(0)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(false));
    }
    
    
}
