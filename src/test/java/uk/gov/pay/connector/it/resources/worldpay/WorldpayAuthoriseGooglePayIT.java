package uk.gov.pay.connector.it.resources.worldpay;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.List;
import java.util.Map;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class WorldpayAuthoriseGooglePayIT extends ChargingITestBase {

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

    public WorldpayAuthoriseGooglePayIT() {
        super("worldpay");
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
    public void authorise_charge_success() throws Exception {
        worldpayMockClient.mockAuthorisationSuccess();
        
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
    public void verify_auth_3ds_required_in_response_to_a_google_pay_request() throws Exception {
        worldpayMockClient.mockAuthorisationRequires3ds();

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode googlePayload = Jackson.getObjectMapper().readTree(fixture("googlepay/example-auth-request.json"));

        givenSetup()
                .body(googlePayload)
                .post(authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.getValue());
    }
}
