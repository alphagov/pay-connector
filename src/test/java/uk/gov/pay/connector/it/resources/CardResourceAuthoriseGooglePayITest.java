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
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.paymentprocessor.resource.CardResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CardResourceAuthoriseGooglePayITest extends ChargingITestBase {

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
    
    public CardResourceAuthoriseGooglePayITest() {
        super("sandbox");
    }

    @Before
    public void setUp() {
        Logger root = (Logger) LoggerFactory.getLogger(CardResource.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }
    
    @Test
    public void authoriseChargeSuccess() throws IOException {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(fixture("googlepay/example-auth-request.json"));

        givenSetup()
                .body(googlePayload)
                .post(authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        Map<String, Object> charge = databaseTestHelper.getChargeByExternalId(chargeId);
        assertThat(charge.get("email"), is(googlePayload.get("payment_info").get("email").asText()));

        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(true));
    }

    @Test
    public void tooLongCardHolderName_shouldResultInBadRequest() throws Exception {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String payload = fixture("googlepay/example-auth-request.json").replace("Example Name", 
                "tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars " +
                        "12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12");
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(payload);

        givenSetup()
                .body(googlePayload)
                .post(authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body("message", contains("Card holder name must be a maximum of 255 chars"));

        verify(mockAppender, times(0)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(false));
    }

    @Test
    public void tooLongEmail_shouldResultInBadRequest() throws Exception {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String payload = fixture("googlepay/example-auth-request.json").replace("example@test.example",
                "tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars@" +
                        "12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12");
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(payload);

        givenSetup()
                .body(googlePayload)
                .post(authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body("message", contains("Email must be a maximum of 254 chars"));

        verify(mockAppender, times(0)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(false));
    }
}
