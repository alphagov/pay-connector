package uk.gov.pay.connector.gateway.util;


import net.logstash.logback.argument.StructuredArgument;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthoriseRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.BILLING_ADDRESS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.CORPORATE_CARD;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.DATA_FOR_3DS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.EMAIL;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.IP_ADDRESS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.MOTO;

public class WorldpayAuthoriseRequestLogGenerator {

    public static String GATEWAY_REQUEST_RECORD = "gateway_request_record";

    public AuthorisationRequestLog generate(WorldpayAuthoriseRequest worldpayAuthoriseRequest, AuthCardDetails authCardDetails) {
        return switch (worldpayAuthoriseRequest) {
            case WorldpayMotoAuthoriseRequest worldpayMotoAuthoriseRequest -> generate(worldpayMotoAuthoriseRequest, authCardDetails);
        };
    }

    private AuthorisationRequestLog generate(WorldpayMotoAuthoriseRequest worldpayMotoAuthoriseRequest, AuthCardDetails authCardDetails) {
        List<StructuredArgument> structuredArguments = new ArrayList<>();
        structuredArguments.add(kv(GATEWAY_REQUEST_RECORD, true));

        var stringJoiner = new StringJoiner(" and ", " ", "");

        stringJoiner.add("without billing address");
        structuredArguments.add(kv(BILLING_ADDRESS, false));

        if (authCardDetails.isCorporateCard()) {
            stringJoiner.add("with corporate card");
            structuredArguments.add(kv(CORPORATE_CARD, true));
        } else {
            structuredArguments.add(kv(CORPORATE_CARD, false));
        }

        authCardDetails.getIpAddress().ifPresent(ipAddress -> {
            stringJoiner.add("with remote IP " + ipAddress);
            structuredArguments.add(kv(IP_ADDRESS, ipAddress));
        });

        structuredArguments.add(kv(EMAIL, false));
        structuredArguments.add(kv(DATA_FOR_3DS, false));
        structuredArguments.add(kv(MOTO, true));

        return new AuthorisationRequestLog(stringJoiner.toString(), List.copyOf(structuredArguments));
    }

}
