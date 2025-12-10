package uk.gov.pay.connector.logging;

import jakarta.inject.Inject;
import net.logstash.logback.argument.StructuredArgument;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;

import java.util.Locale;
import java.util.Optional;

public class AuthorisationLogger {

    private final AuthorisationRequestSummaryStringifier authorisationRequestSummaryStringifier;
    private final AuthorisationRequestSummaryStructuredLogging authorisationRequestSummaryStructuredLogging;

    @Inject
    public AuthorisationLogger(AuthorisationRequestSummaryStringifier authorisationRequestSummaryStringifier,
                               AuthorisationRequestSummaryStructuredLogging authorisationRequestSummaryStructuredLogging) {
        this.authorisationRequestSummaryStringifier = authorisationRequestSummaryStringifier;
        this.authorisationRequestSummaryStructuredLogging = authorisationRequestSummaryStructuredLogging;
    }

    public void logChargeAuthorisation(Logger logger,
                                       AuthorisationRequestSummary authorisationRequestSummary,
                                       ChargeEntity charge,
                                       String transactionId,
                                       GatewayResponse gatewayResponse,
                                       ChargeStatus oldStatus,
                                       ChargeStatus newStatus) {

        var logMessage = String.format(Locale.UK, "Authorisation%s for %s (%s %s) for %s (%s) - %s .'. %s -> %s",
                Optional.ofNullable(authorisationRequestSummary).map(authorisationRequestSummaryStringifier::stringify).orElse(""),
                charge.getExternalId(),
                charge.getPaymentGatewayName().getName(),
                transactionId,
                charge.getGatewayAccount().getAnalyticsId(),
                charge.getGatewayAccount().getId(),
                gatewayResponse,
                oldStatus,
                newStatus);

        var structuredLoggingArguments = ArrayUtils.addAll(
                charge.getStructuredLoggingArgs(),
                Optional.ofNullable(authorisationRequestSummary)
                        .map(authorisationRequestSummaryStructuredLogging::createArgs)
                        .orElse(new StructuredArgument[0]));
        logger.info(logMessage, (Object[]) structuredLoggingArguments);
    }

    public void logChargeAuthorisation(Logger logger,
                                       ChargeEntity charge,
                                       String transactionId,
                                       GatewayResponse gatewayResponse,
                                       ChargeStatus oldStatus,
                                       ChargeStatus newStatus) {
        logChargeAuthorisation(logger, null, charge, transactionId, gatewayResponse, oldStatus, newStatus);
    }
}
