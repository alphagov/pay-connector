package uk.gov.pay.connector.gateway.util;

import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;

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
            case PRESENT:
                stringJoiner.add("with billing address");
                break;
            case NOT_PRESENT:
                stringJoiner.add("without billing address");
                break;
            default:
                break;
        }

        switch (authorisationRequestSummary.dataFor3ds()) {
            case PRESENT:
                stringJoiner.add("with 3DS data");
                break;
            case NOT_PRESENT:
                stringJoiner.add("without 3DS data");
                break;
            default:
                break;
        }

        switch (authorisationRequestSummary.dataFor3ds2()) {
            case PRESENT:
                stringJoiner.add("with 3DS2 data");
                break;
            case NOT_PRESENT:
                stringJoiner.add("without 3DS2 data");
                break;
            default:
                break;
        }

        switch (authorisationRequestSummary.deviceDataCollectionResult()) {
            case PRESENT:
                stringJoiner.add("with device data collection result");
                break;
            case NOT_PRESENT:
                stringJoiner.add("without device data collection result");
                break;
            default:
                break;
        }

        switch (authorisationRequestSummary.exemptionRequest()) {
            case PRESENT:
                stringJoiner.add("with exemption");
                break;
            case NOT_PRESENT:
                stringJoiner.add("without exemption");
                break;
            default:
                break;
        }

        Optional.ofNullable(authorisationRequestSummary.ipAddress())
                .map(ipAddress -> stringJoiner.add("with remote IP " + ipAddress));

        return stringJoiner.toString();
    }

}
