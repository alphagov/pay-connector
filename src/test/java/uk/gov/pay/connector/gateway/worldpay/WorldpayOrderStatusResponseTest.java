package uk.gov.pay.connector.gateway.worldpay;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITHOUT_TRANSACTION_IDENTIFIER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITH_TRANSACTION_IDENTIFIER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE_WITH_INVALID_EXPIRY_YEAR;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE_WITH_MISSING_EXPIRY_DATE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class WorldpayOrderStatusResponseTest {
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    
    @BeforeEach
    void setUp() { 
        setUpLogging();
    }
    
    private void setUpLogging() {
        Logger root = (Logger) LoggerFactory.getLogger(WorldpayOrderStatusResponse.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"OUT_OF_SCOPE", "REJECTED"})
    void worldpay_response_should_be_soft_decline(String exemptionResponseResult) {
        var worldpayOrderStatusResponse = new WorldpayOrderStatusResponse();
        worldpayOrderStatusResponse.setLastEvent("REFUSED");
        worldpayOrderStatusResponse.setExemptionResponseResult(exemptionResponseResult);

        assertTrue(worldpayOrderStatusResponse.isSoftDecline());
    }

    @Test
    void worldpay_response_should_not_be_soft_decline() {
        var worldpayOrderStatusResponse = new WorldpayOrderStatusResponse();
        worldpayOrderStatusResponse.setLastEvent("REFUSED");
        worldpayOrderStatusResponse.setExemptionResponseResult("HONOURED");

        assertFalse(worldpayOrderStatusResponse.isSoftDecline());
    }

    @Test
    void worldpay_response_should_not_be_soft_decline_when_error() {
        var worldpayOrderStatusResponse = new WorldpayOrderStatusResponse();
        worldpayOrderStatusResponse.setLastEvent("ERROR");
        worldpayOrderStatusResponse.setExemptionResponseResult("REFUSED");

        assertFalse(worldpayOrderStatusResponse.isSoftDecline());
    }

    @Test
    void should_get_recurring_payment_token_with_transaction_scheme_identifier() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITH_TRANSACTION_IDENTIFIER);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);

        assertThat(worldpayOrderStatusResponse.getGatewayRecurringAuthToken().isPresent(), is(true));

        Map<String, String> gatewayRecurringAuthToken = worldpayOrderStatusResponse.getGatewayRecurringAuthToken().get();
        assertThat(gatewayRecurringAuthToken, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "9961191959944156907"));
        assertThat(gatewayRecurringAuthToken, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "1234567890"));
    }

    @Test
    void should_get_recurring_payment_token_without_transaction_scheme_identifier() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITHOUT_TRANSACTION_IDENTIFIER);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);

        assertThat(worldpayOrderStatusResponse.getGatewayRecurringAuthToken().isPresent(), is(true));

        Map<String, String> gatewayRecurringAuthToken = worldpayOrderStatusResponse.getGatewayRecurringAuthToken().get();
        assertThat(gatewayRecurringAuthToken, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "9961191959944156907"));
        assertThat(gatewayRecurringAuthToken, not(hasKey(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY)));
    }

    @Test
    void get_recurring_payment_token_should_return_empty_optional_when_no_token_id_in_response() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);

        assertThat(worldpayOrderStatusResponse.getGatewayRecurringAuthToken().isPresent(), is(false));
    }

    @Test
    void WorldpayAuthorisationRejectedCode_should_be_mapped_to_MappedAuthorisationRejectedReason_in_stringified_response() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_FAILED_RESPONSE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);
        assertTrue(worldpayOrderStatusResponse.toString().contains("Mapped rejection reason: DO_NOT_HONOUR"));
    }

    @Test
    void get_expiry_date_should_return_correctly_when_expiry_date_fields_are_present() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);
        assertThat(worldpayOrderStatusResponse.getCardExpiryDate().isPresent(), is(true));
        assertThat(worldpayOrderStatusResponse.getCardExpiryDate().get(), is(CardExpiryDate.valueOf("11/35")));
    }

    @Test
    void get_expiry_date_should_return_empty_optional_but_no_log_when_expiry_date_fields_are_absent_from_failed_response() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_FAILED_RESPONSE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);
        assertThat(worldpayOrderStatusResponse.getCardExpiryDate().isPresent(), is(false));
        ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockAppender, times(0)).doAppend(loggingEventArgumentCaptor.capture());
    }

    @Test
    void get_expiry_date_should_return_empty_optional_and_log_when_expiry_date_fields_are_absent_from_success_response() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE_WITH_MISSING_EXPIRY_DATE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);
        assertThat(worldpayOrderStatusResponse.getCardExpiryDate().isPresent(), is(false));
        verifyLogging(1, "Expiry date is not included in Worldpay wallet payment authorisation success response.");
    }
    
    @Test
    void get_expiry_date_should_return_empty_optional_and_log_when_expiry_date_is_invalid() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE_WITH_INVALID_EXPIRY_YEAR);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);
        assertThat(worldpayOrderStatusResponse.getCardExpiryDate().isPresent(), is(false));
        verifyLogging(1, "Expiry date in Worldpay wallet payment authorisation response is in an unexpected format; month has 2 digits, year has 3 digits.");
    }
    
    private void verifyLogging(int invocations, String logMessage) {
        ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockAppender, times(invocations)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.stream().anyMatch(le -> le.getFormattedMessage().contains(logMessage)), is(true));
    }

}
