package uk.gov.pay.connector.gateway.adyen.utils;

import net.logstash.logback.argument.StructuredArgument;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.records.AdyenApplePayAuthorisePayload;
import uk.gov.pay.connector.gateway.model.request.records.AdyenAuthoriseRequest;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestLog;
import uk.gov.pay.connector.wallets.WalletType;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.BILLING_ADDRESS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.EMAIL;
import static uk.gov.service.payments.logging.LoggingKeys.WALLET;

public class AdyenAuthoriseRequestLogGenerator {

    public static String GATEWAY_REQUEST_RECORD = "gateway_request_record";

    public AuthorisationRequestLog generate(AdyenAuthoriseRequest adyenAuthoriseRequest, AuthCardDetails authCardDetails) {
        return switch (adyenAuthoriseRequest) {
            case AdyenApplePayAuthorisePayload adyenApplePayAuthorisePayload -> generate(adyenApplePayAuthorisePayload, authCardDetails);
        };
    }

    private AuthorisationRequestLog generate(AdyenApplePayAuthorisePayload adyenApplePayAuthorisePayload, AuthCardDetails authCardDetails) {
        List<StructuredArgument> structuredArguments = new ArrayList<>();
        structuredArguments.add(kv(GATEWAY_REQUEST_RECORD, true));

        var stringJoiner = new StringJoiner(" and ", " ", "");

        stringJoiner.add("with Apple Pay");
        structuredArguments.add(kv(WALLET, WalletType.APPLE_PAY));

        structuredArguments.add(kv(BILLING_ADDRESS, false));
        structuredArguments.add(kv(EMAIL, false));

        return new AuthorisationRequestLog(stringJoiner.toString(), List.copyOf(structuredArguments));
    }

}
