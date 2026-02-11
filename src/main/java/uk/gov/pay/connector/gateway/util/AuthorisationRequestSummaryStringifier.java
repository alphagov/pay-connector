package uk.gov.pay.connector.gateway.util;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.model.request.gateway.GatewayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.Worldpay3dsEligibleAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.Worldpay3dsFlexEligibleAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayAuthoriseRequestWithOptional3dsExemption;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayAuthoriseRequestWithOptionalBillingAddress;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayAuthoriseRequestWithOptionalEmail;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayAuthoriseRequestWithOptionalIpAddress;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest.Placement;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest.Type;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayOneOffAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayOneOffCardNumberMotoAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayRecurringCustomerInitiatedAuthoriseRequest;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;

import java.util.Optional;
import java.util.StringJoiner;

import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;
import static uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest.Type.CP;

public class AuthorisationRequestSummaryStringifier {

    public String stringify(GatewayAuthoriseRequest request, ChargeEntity charge, AuthCardDetails authCardDetails) {
        var stringJoiner = new StringJoiner(" and ", " ", "");

        if (request instanceof WorldpayOneOffCardNumberMotoAuthoriseRequest) {
            stringJoiner.add("MOTO");
        } else {
            stringJoiner.add("not MOTO");
        }

        if (charge.isSavePaymentInstrumentToAgreement()) {
            stringJoiner.add("with set up agreement");
        }

        if (request instanceof WorldpayAuthoriseRequestWithOptionalBillingAddress reqWithOptionalAddress) {
            if (reqWithOptionalAddress.address() != null) {
                stringJoiner.add("with billing address");
            } else {
                stringJoiner.add("without billing address");
            }
        }

        if (request instanceof WorldpayAuthoriseRequestWithOptionalEmail reqWithOptionalEmail) {
            if (reqWithOptionalEmail.email() != null) {
                stringJoiner.add("with email address");
            } else {
                stringJoiner.add("without email address");
            }
        }

        if (authCardDetails.isCorporateCard()) {
            stringJoiner.add("with corporate card");
        }

        if (request instanceof WorldpayAuthoriseRequestWithOptional3dsExemption reqWithOptionalExemption
                && reqWithOptionalExemption.exemption() instanceof WorldpayExemptionRequest(Type type, Placement placement)
                && type == CP) {
            stringJoiner.add("with corporate exemption requested");
            Optional.ofNullable(charge.getExemption3ds()).map(Exemption3ds::getDisplayName).ifPresent(stringJoiner::add);
        }

        if (request instanceof Worldpay3dsEligibleAuthoriseRequest) {
            stringJoiner.add("with 3DS data");
        } else if (request instanceof WorldpayAuthoriseRequest) {
            stringJoiner.add("without 3DS data");
        }

        if (request instanceof Worldpay3dsFlexEligibleAuthoriseRequest flexEligible) {
            if (flexEligible.dfReferenceId() != null) {
                stringJoiner.add("with device data collection result");
            } else {
                stringJoiner.add("without device data collection result");
            }
        }

        if (request instanceof WorldpayAuthoriseRequestWithOptionalIpAddress reqWithOptionalIpAddress
                && reqWithOptionalIpAddress.ipAddress() != null) {
            stringJoiner.add("with remote IP " + reqWithOptionalIpAddress.ipAddress());
        }

        Optional.ofNullable(charge.getAgreementPaymentType())
                .ifPresent(agreementPaymentType -> stringJoiner.add("with agreement payment type of " + agreementPaymentType.getName()));

        return stringJoiner.toString();
    }

}
