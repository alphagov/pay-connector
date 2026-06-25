package uk.gov.pay.connector.logging;

import jakarta.inject.Inject;
import net.logstash.logback.argument.StructuredArgument;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestLog;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.util.WorldpayAuthoriseRequestLogGenerator;

import java.util.Locale;
import java.util.Optional;

public class AuthorisationLogger {

    private final AuthorisationRequestSummaryStringifier authorisationRequestSummaryStringifier;
    private final AuthorisationRequestSummaryStructuredLogging authorisationRequestSummaryStructuredLogging;
    private final WorldpayAuthoriseRequestLogGenerator worldpayAuthoriseRequestLogGenerator;

    @Inject
    public AuthorisationLogger(AuthorisationRequestSummaryStringifier authorisationRequestSummaryStringifier, 
                               AuthorisationRequestSummaryStructuredLogging authorisationRequestSummaryStructuredLogging,
                               WorldpayAuthoriseRequestLogGenerator worldpayAuthoriseRequestLogGenerator) {
        this.authorisationRequestSummaryStringifier = authorisationRequestSummaryStringifier;
        this.authorisationRequestSummaryStructuredLogging = authorisationRequestSummaryStructuredLogging;
        this.worldpayAuthoriseRequestLogGenerator = worldpayAuthoriseRequestLogGenerator;
    }

    public void logChargeAuthorisation(Logger logger,
                                       AuthorisationRequestSummary authorisationRequestSummary,
                                       ChargeEntity charge, 
                                       String transactionId,
                                       GatewayResponse<?> gatewayResponse,
                                       ChargeStatus oldStatus,
                                       ChargeStatus newStatus) {

        logChargeAuthorisation(logger,
                Optional.ofNullable(authorisationRequestSummary)
                        .map(authorisationRequestSummaryStringifier::stringify).orElse(null),
                Optional.ofNullable(authorisationRequestSummary)
                        .map(authorisationRequestSummaryStructuredLogging::createArgs).orElse(null),
                charge,
                transactionId,
                gatewayResponse,
                oldStatus,
                newStatus);
    }

    public void logChargeAuthorisation(Logger logger,
                                       WorldpayAuthoriseRequest worldpayAuthoriseRequest,
                                       AuthCardDetails authCardDetails,
                                       ChargeEntity charge,
                                       String transactionId,
                                       GatewayResponse<?> gatewayResponse,
                                       ChargeStatus oldStatus,
                                       ChargeStatus newStatus) {

        AuthorisationRequestLog authoriseRequestLog = worldpayAuthoriseRequestLogGenerator.generate(
                worldpayAuthoriseRequest, authCardDetails);

        logChargeAuthorisation(logger,
                authoriseRequestLog.authorisationRequest(),
                authoriseRequestLog.structuredArguments().toArray(new StructuredArgument[0]),
                charge,
                transactionId,
                gatewayResponse,
                oldStatus,
                newStatus);
    }

    public void logChargeAuthorisation(Logger logger,
                                       ChargeEntity charge,
                                       String transactionId,
                                       GatewayResponse<?> gatewayResponse,
                                       ChargeStatus oldStatus,
                                       ChargeStatus newStatus) {
        logChargeAuthorisation(logger,
                (String) null,
                (StructuredArgument[]) null,
                charge,
                transactionId,
                gatewayResponse,
                oldStatus,
                newStatus);
    }

    private void logChargeAuthorisation(Logger logger,
                                   String authorisationRequest,
                                   StructuredArgument[] authorisationRequestStructuredArguments,
                                   ChargeEntity charge,
                                   String transactionId,
                                   GatewayResponse<?> gatewayResponse,
                                   ChargeStatus oldStatus,
                                   ChargeStatus newStatus) {
        var logMessage = String.format(Locale.UK, "Authorisation%s for %s (%s %s) for %s (%s) - %s .'. %s -> %s",
                Optional.ofNullable(authorisationRequest).orElse(""),
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
                authorisationRequestStructuredArguments);

        logger.info(logMessage, structuredLoggingArguments);
    }

}
