package uk.gov.pay.connector.it.resources;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.paymentprocessor.resource.CardResource;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonApplePayAuthorisationDetails;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CardResourceAuthoriseApplePayIT extends ChargingITestBase {

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

    public CardResourceAuthoriseApplePayIT() {
        super("sandbox");
    }

    @Before
    public void setUp() {
        Logger root = (Logger) LoggerFactory.getLogger(CardResource.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    public void shouldAuthoriseCharge_ForApplePay() {
        shouldAuthoriseChargeForApplePay("mr payment", "mr@payment.test");

        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(true));
    }

    @Test
    public void shouldAuthoriseCharge_ForApplePay_withMinData() {
        shouldAuthoriseChargeForApplePay(null, null);
    }

    private String shouldAuthoriseChargeForApplePay(String cardHolderName, String email) {
        String chargeId = createNewChargeWithNoTransactionIdOrEmailAddress(ENTERING_CARD_DETAILS);

        givenSetup()
                .body(buildJsonApplePayAuthorisationDetails(cardHolderName, email))
                .post(authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        Map<String, Object> charge = databaseTestHelper.getChargeByExternalId(chargeId);
        assertThat(charge.get("email"), is(email));
        return chargeId;
    }

    @Test
    public void tooLongCardHolderName_shouldResultInBadRequest() {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String payload = buildJsonApplePayAuthorisationDetails(
                "tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars " +
                        "12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12",
                "mr@payment.test");

        givenSetup()
                .body(payload)
                .post(authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("message", contains("Card holder name must be a maximum of 255 chars"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        verify(mockAppender, times(0)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(false));
    }

    @Test
    public void tooLongEmail_shouldResultInBadRequest() {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String payload = buildJsonApplePayAuthorisationDetails("Example Name",
                "tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars@" +
                        "12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12tenchars12");

        givenSetup()
                .body(payload)
                .post(authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("message", contains("Email must be a maximum of 254 chars"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        verify(mockAppender, times(0)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(false));
    }
}
