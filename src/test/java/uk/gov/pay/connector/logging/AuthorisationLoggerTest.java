package uk.gov.pay.connector.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import net.logstash.logback.argument.StructuredArgument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestLog;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.util.WorldpayAuthoriseRequestLogGenerator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;

import java.util.List;

import static ch.qos.logback.classic.Level.INFO;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthoriseRequestFixture.aWorldpayMotoAuthoriseRequestFixture;

@ExtendWith(MockitoExtension.class)
class AuthorisationLoggerTest {

    @Mock
    private AuthorisationRequestSummary mockAuthorisationRequestSummary;

    @Mock
    private AuthorisationRequestSummaryStringifier mockAuthorisationRequestSummaryStringifier;

    @Mock
    private AuthorisationRequestSummaryStructuredLogging mockAuthorisationRequestSummaryStructuredLogging;

    @Mock
    private AuthCardDetails mockAuthCardDetails;

    @Mock
    private WorldpayAuthoriseRequestLogGenerator mockWorldpayAuthoriseRequestLogGenerator;

    @Mock
    private GatewayResponse<?> mockGatewayResponse;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private final WorldpayAuthoriseRequest worldpayAuthoriseRequest = aWorldpayMotoAuthoriseRequestFixture().build();

    private final GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture.aGatewayAccountEntity()
            .withId(123L)
            .withAnalyticsId("AnalyticsId")
            .build();

    private final ChargeEntity chargeEntity = aValidChargeEntity()
            .withExternalId("abcdefghijklmnopqrstuvwxyz")
            .withGatewayAccountEntity(gatewayAccountEntity)
            .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
            .build();

    private Logger logger;

    private AuthorisationLogger authorisationLogger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger("Test");
        logger.addAppender(mockAppender);
        logger.setLevel(INFO);

        authorisationLogger = new AuthorisationLogger(mockAuthorisationRequestSummaryStringifier,
                mockAuthorisationRequestSummaryStructuredLogging, mockWorldpayAuthoriseRequestLogGenerator);
    }

    @Test
    void logsWithNullAuthorisationRequestSummary() {
        given(mockAuthorisationRequestSummaryStringifier.stringify(mockAuthorisationRequestSummary))
                .willReturn(null);

        given(mockAuthorisationRequestSummaryStructuredLogging.createArgs(mockAuthorisationRequestSummary))
                .willReturn(null);

        authorisationLogger.logChargeAuthorisation(logger, mockAuthorisationRequestSummary, chargeEntity,
                "payment-12345", mockGatewayResponse, ChargeStatus.ENTERING_CARD_DETAILS, ChargeStatus.AUTHORISATION_SUCCESS);

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        var loggingEvent = loggingEventArgumentCaptor.getAllValues().getFirst();

        assertThat(loggingEvent.getFormattedMessage(), is(
                "Authorisation for abcdefghijklmnopqrstuvwxyz " +
                        "(worldpay payment-12345) for AnalyticsId (123) - " +
                        "mockGatewayResponse .'. ENTERING CARD DETAILS -> AUTHORISATION SUCCESS"));
    }

    @Test
    void logsWithAuthorisationRequestSummary() {
        given(mockAuthorisationRequestSummaryStringifier.stringify(mockAuthorisationRequestSummary))
                .willReturn(" with all the fancy details");

        given(mockAuthorisationRequestSummaryStructuredLogging.createArgs(mockAuthorisationRequestSummary))
                .willReturn(new StructuredArgument[]{ kv("some", "fun"), kv("structured", "args") });

        authorisationLogger.logChargeAuthorisation(logger, mockAuthorisationRequestSummary, chargeEntity,
                "payment-12345", mockGatewayResponse, ChargeStatus.ENTERING_CARD_DETAILS, ChargeStatus.AUTHORISATION_SUCCESS);

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        var loggingEvent = loggingEventArgumentCaptor.getAllValues().getFirst();

        assertThat(loggingEvent.getFormattedMessage(), is(
                "Authorisation with all the fancy details for abcdefghijklmnopqrstuvwxyz " +
                        "(worldpay payment-12345) for AnalyticsId (123) - " +
                        "mockGatewayResponse .'. ENTERING CARD DETAILS -> AUTHORISATION SUCCESS"));

        assertThat(loggingEvent.getArgumentArray(), hasItemInArray(kv("some", "fun")));
        assertThat(loggingEvent.getArgumentArray(), hasItemInArray(kv("structured", "args")));
    }

    @Test
    void logsWithWorldpayAuthorisationRequest() {
        given(mockWorldpayAuthoriseRequestLogGenerator.generate(worldpayAuthoriseRequest, mockAuthCardDetails))
                        .willReturn(new AuthorisationRequestLog(" with all the fancy details",
                                List.of(kv("some", "fun"), kv("structured", "args"))));

        authorisationLogger.logChargeAuthorisation(logger, worldpayAuthoriseRequest, mockAuthCardDetails, chargeEntity,
                "payment-12345", mockGatewayResponse, ChargeStatus.ENTERING_CARD_DETAILS, ChargeStatus.AUTHORISATION_SUCCESS);

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        var loggingEvent = loggingEventArgumentCaptor.getAllValues().getFirst();

        assertThat(loggingEvent.getFormattedMessage(), is(
                "Authorisation with all the fancy details for abcdefghijklmnopqrstuvwxyz " +
                        "(worldpay payment-12345) for AnalyticsId (123) - " +
                        "mockGatewayResponse .'. ENTERING CARD DETAILS -> AUTHORISATION SUCCESS"));

        assertThat(loggingEvent.getArgumentArray(), hasItemInArray(kv("some", "fun")));
        assertThat(loggingEvent.getArgumentArray(), hasItemInArray(kv("structured", "args")));
    }

    @Test
    void logsWithNoAuthorisationRequest() {
        authorisationLogger.logChargeAuthorisation(logger, chargeEntity,
                "payment-12345", mockGatewayResponse, ChargeStatus.ENTERING_CARD_DETAILS, ChargeStatus.AUTHORISATION_SUCCESS);

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        var loggingEvent = loggingEventArgumentCaptor.getAllValues().getFirst();

        assertThat(loggingEvent.getFormattedMessage(), is(
                "Authorisation for abcdefghijklmnopqrstuvwxyz " +
                        "(worldpay payment-12345) for AnalyticsId (123) - " +
                        "mockGatewayResponse .'. ENTERING CARD DETAILS -> AUTHORISATION SUCCESS"));
    }

}
