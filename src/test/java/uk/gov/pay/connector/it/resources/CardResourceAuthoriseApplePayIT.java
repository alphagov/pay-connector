package uk.gov.pay.connector.it.resources;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.card.resource.CardResource;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
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

    @Override
    @Before
    public void setUp() {
        super.setUp();
        Logger root = (Logger) LoggerFactory.getLogger(CardResource.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    public void shouldAuthoriseCharge_ForApplePay() {
        var chargeId = createNewChargeWithNoGatewayTransactionIdOrEmailAddress(ENTERING_CARD_DETAILS);
        var email = "mr@payment.test";
        shouldAuthoriseChargeForApplePay(chargeId, "mr payment", email)
                .statusCode(200);
        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        Map<String, Object> charge = databaseTestHelper.getChargeByExternalId(chargeId);
        assertThat(charge.get("email"), is(email));
        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received encrypted payload for charge with id")), is(true));
    }

    @Test
    public void shouldAuthoriseCharge_ForApplePay_withMinData() {
        var chargeId = createNewChargeWithNoGatewayTransactionIdOrEmailAddress(ENTERING_CARD_DETAILS);
        shouldAuthoriseChargeForApplePay(chargeId, null, null)
                .statusCode(200);
        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        Map<String, Object> charge = databaseTestHelper.getChargeByExternalId(chargeId);
        assertThat(charge.get("email"), is(nullValue()));
    }

    @Test
    public void shouldAuthoriseChargeAppropriately_ForApplePay_withMagicValues() {
        provideMagicValues().forEach(arguments -> {
            var desc = arguments.getLeft();
            var expectedCode = arguments.getMiddle();
            var expectedStatus = arguments.getRight();
            var chargeId = createNewChargeWithDescriptionAndNoGatewayTransactionIdOrEmailAddress(ENTERING_CARD_DETAILS, desc);
            shouldAuthoriseChargeForApplePay(chargeId, null, null)
                    .statusCode(expectedCode);
            assertFrontendChargeStatusIs(chargeId, expectedStatus.getValue());
        });
    }

    @Test
    public void shouldAuthoriseCharge_ForApplePay_andAddFakeExpiry_forSandboxProvider() {
        var chargeId = createNewChargeWithNoGatewayTransactionIdOrEmailAddress(ENTERING_CARD_DETAILS);
        shouldAuthoriseChargeForApplePay(chargeId, null, null)
                .statusCode(200);
        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        Map<String, Object> charge = databaseTestHelper.getChargeByExternalId(chargeId);
        assertThat(charge.get("expiry_date"), is("12/50"));
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

    private ValidatableResponse shouldAuthoriseChargeForApplePay(String chargeId, String cardHolderName, String email) {

        return givenSetup()
                .body(buildJsonApplePayAuthorisationDetails(cardHolderName, email))
                .post(authoriseChargeUrlForApplePay(chargeId))
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
