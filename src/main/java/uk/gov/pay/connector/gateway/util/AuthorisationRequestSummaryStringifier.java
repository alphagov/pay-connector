package uk.gov.pay.connector.gateway.util;

import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;

import java.util.Optional;
import java.util.StringJoiner;

import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

public class AuthorisationRequestSummaryStringifier {

    public String stringify(AuthorisationRequestSummary authorisationRequestSummary) {
        var stringJoiner = new StringJoiner(" and ", " ", "");

        if (authorisationRequestSummary.setUpAgreement() == PRESENT) {
            stringJoiner.add("with set up agreement");
        }

        switch (authorisationRequestSummary.billingAddress()) {
            case PRESENT -> stringJoiner.add("with billing address");
            case NOT_PRESENT -> stringJoiner.add("without billing address");
        }

        switch (authorisationRequestSummary.email()) {
            case PRESENT -> stringJoiner.add("with email address");
            case NOT_PRESENT -> stringJoiner.add("without email address");
        }
        
        if (authorisationRequestSummary.corporateCard()) {
            stringJoiner.add("with corporate card");
        }

        authorisationRequestSummary.corporateExemptionRequested()
                .filter(corporateExemptionRequested -> corporateExemptionRequested)
                .ifPresent(corporateExemptionRequestedTrue -> {
            stringJoiner.add("with corporate exemption requested");
            authorisationRequestSummary.corporateExemptionResult().map(Exemption3ds::getDisplayName).ifPresent(stringJoiner::add);
        });

        switch (authorisationRequestSummary.dataFor3ds()) {
            case PRESENT -> stringJoiner.add("with 3DS data");
            case NOT_PRESENT -> stringJoiner.add("without 3DS data");
        }

        switch (authorisationRequestSummary.dataFor3ds2()) {
            case PRESENT -> stringJoiner.add("with 3DS2 data");
            case NOT_PRESENT -> stringJoiner.add("without 3DS2 data");
        }

        switch (authorisationRequestSummary.deviceDataCollectionResult()) {
            case PRESENT -> stringJoiner.add("with device data collection result");
            case NOT_PRESENT -> stringJoiner.add("without device data collection result");
        }
        
        switch (authorisationRequestSummary.shouldForce3ds()) {
            case PRESENT -> stringJoiner.add("with forced 3ds");
            case NOT_PRESENT -> stringJoiner.add("without forced 3ds");
        }

        Optional.ofNullable(authorisationRequestSummary.ipAddress())
                .map(ipAddress -> stringJoiner.add("with remote IP " + ipAddress));

        return stringJoiner.toString();
    }

}
