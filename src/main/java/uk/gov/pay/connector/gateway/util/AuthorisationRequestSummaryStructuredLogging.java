package uk.gov.pay.connector.gateway.util;

import net.logstash.logback.argument.StructuredArgument;
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
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.service.payments.commons.model.AgreementPaymentType;

import java.util.ArrayList;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayExemptionRequest.Type.CP;

public class AuthorisationRequestSummaryStructuredLogging {
    
    public static final String BILLING_ADDRESS = "billing_address";
    public static final String CORPORATE_CARD = "corporate_card";
    public static final String CORPORATE_EXEMPTION_REQUESTED = "corporate_exemption_requested";
    public static final String CORPORATE_EXEMPTION_RESULT = "corporate_exemption_result";
    public static final String DATA_FOR_3DS = "data_for_3ds";
    public static final String DATA_FOR_3DS2 = "data_for_3ds2";
    public static final String WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT = "worldpay_3ds_flex_device_data_collection_result";
    public static final String IP_ADDRESS = "remote_ip_address";
    public static final String EMAIL = "email_address";
    public static final String THREE_DS_REQUIRED = "3ds_required";
    public static final String AGREEMENT_PAYMENT_TYPE = "agreement_payment_type";
    public static final String MOTO = "moto";

    public StructuredArgument[] createArgs(GatewayAuthoriseRequest request, ChargeEntity charge, AuthCardDetails authCardDetails) {
        var structuredArguments = new ArrayList<StructuredArgument>();

        if (request instanceof WorldpayAuthoriseRequestWithOptionalBillingAddress reqWithOptionalAddress) {
            if (reqWithOptionalAddress.address() != null) {
                structuredArguments.add(kv(BILLING_ADDRESS, true));
            } else {
                structuredArguments.add(kv(BILLING_ADDRESS, false));
            }
        }

        if (request instanceof Worldpay3dsEligibleAuthoriseRequest) {
            structuredArguments.add(kv(DATA_FOR_3DS, true));
        } else if (request instanceof WorldpayAuthoriseRequest) {
            structuredArguments.add(kv(DATA_FOR_3DS, false));
        }

        if (request instanceof Worldpay3dsFlexEligibleAuthoriseRequest flexEligible) {
            if (flexEligible.dfReferenceId() != null) {
                structuredArguments.add(kv(DATA_FOR_3DS2, true));
            } else {
                structuredArguments.add(kv(DATA_FOR_3DS2, false));
            }
        }

        if (request instanceof WorldpayAuthoriseRequestWithOptionalEmail reqWithOptionalEmail) {
            if (reqWithOptionalEmail.email() != null) {
                structuredArguments.add(kv(EMAIL, true));
            } else {
                structuredArguments.add(kv(EMAIL, false));
            }
        }

        if (request instanceof Worldpay3dsFlexEligibleAuthoriseRequest flexEligible) {
            if (flexEligible.dfReferenceId() != null) {
                structuredArguments.add(kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, true));
            } else {
                structuredArguments.add(kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, false));
            }
        }

        if (request instanceof WorldpayAuthoriseRequestWithOptionalIpAddress reqWithOptionalIpAddress
                && reqWithOptionalIpAddress.ipAddress() != null) {
            structuredArguments.add(kv(IP_ADDRESS, reqWithOptionalIpAddress.ipAddress()));
        }

        structuredArguments.add(kv(CORPORATE_CARD, authCardDetails.isCorporateCard()));

        if (request instanceof WorldpayAuthoriseRequestWithOptional3dsExemption reqWithOptionalExemption
                && reqWithOptionalExemption.exemption() instanceof WorldpayExemptionRequest(
                WorldpayExemptionRequest.Type type, WorldpayExemptionRequest.Placement placement)
                && type == CP) {
            structuredArguments.add(kv(CORPORATE_EXEMPTION_REQUESTED, true);
            structuredArguments.add(kv(CORPORATE_EXEMPTION_RESULT, Optional.ofNullable(charge.getExemption3ds()).map(Exemption3ds::getDisplayName)));
        }

        Optional.ofNullable(charge.getAgreementPaymentType()).map(AgreementPaymentType::name)
                .ifPresent(agreementPaymentTypeName -> structuredArguments.add(kv(AGREEMENT_PAYMENT_TYPE, agreementPaymentTypeName));

        return structuredArguments.toArray(new StructuredArgument[structuredArguments.size()]);
    }

}
