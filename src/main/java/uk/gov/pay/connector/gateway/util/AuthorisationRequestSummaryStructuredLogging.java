package uk.gov.pay.connector.gateway.util;

import net.logstash.logback.argument.StructuredArgument;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;

import java.util.ArrayList;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;

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
    public static final String MOTO = "moto";

    public StructuredArgument[] createArgs(AuthorisationRequestSummary authorisationRequestSummary) {
        var structuredArguments = new ArrayList<StructuredArgument>();
        
        switch (authorisationRequestSummary.billingAddress()) {
            case PRESENT -> structuredArguments.add(kv(BILLING_ADDRESS, true));
            case NOT_PRESENT -> structuredArguments.add(kv(BILLING_ADDRESS, false));
        }
 
        switch (authorisationRequestSummary.dataFor3ds()) {
            case PRESENT -> structuredArguments.add(kv(DATA_FOR_3DS, true));
            case NOT_PRESENT -> structuredArguments.add(kv(DATA_FOR_3DS, false));
        }

        switch (authorisationRequestSummary.dataFor3ds2()) {
            case PRESENT -> structuredArguments.add(kv(DATA_FOR_3DS2, true));
            case NOT_PRESENT -> structuredArguments.add(kv(DATA_FOR_3DS2, false));
        }
        
        switch (authorisationRequestSummary.email()) {
            case PRESENT -> structuredArguments.add(kv(EMAIL, true));
            case NOT_PRESENT -> structuredArguments.add(kv(EMAIL, false));
        }

        switch (authorisationRequestSummary.deviceDataCollectionResult()) {
            case PRESENT -> structuredArguments.add(kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, true));
            case NOT_PRESENT -> structuredArguments.add(kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, false));
        }

        Optional.ofNullable(authorisationRequestSummary.ipAddress())
                .map(ipAddress -> structuredArguments.add(kv(IP_ADDRESS, ipAddress)));

        structuredArguments.add(kv(CORPORATE_CARD, authorisationRequestSummary.corporateCard()));

        authorisationRequestSummary.corporateExemptionRequested()
                .ifPresent(corporateExemptionRequested -> structuredArguments.add(kv(CORPORATE_EXEMPTION_REQUESTED, corporateExemptionRequested)));
 
        authorisationRequestSummary.corporateExemptionResult()
                .map(Exemption3ds::name)
                .ifPresent(exemption3dsResult -> structuredArguments.add(kv(CORPORATE_EXEMPTION_RESULT, exemption3dsResult)));
 
        return structuredArguments.toArray(new StructuredArgument[structuredArguments.size()]);
    }
    
    public StructuredArgument[] createArgsForPreAuthorisationLogging(WorldpayOrderRequestBuilder orderRequestBuilder, AuthCardDetails authCardDetails, boolean isMoto) {
        var structuredArguments = new ArrayList<StructuredArgument>();
        
        structuredArguments.add(kv(MOTO, isMoto));
        
        structuredArguments.add(kv(BILLING_ADDRESS, authCardDetails.getAddress().isPresent()));
        
        structuredArguments.add(kv(EMAIL, !isBlank(orderRequestBuilder.getWorldpayTemplateData().getPayerEmail())));
        
        Optional.ofNullable(orderRequestBuilder.getWorldpayTemplateData().getPayerIpAddress())
                .map(ipAddress -> structuredArguments.add(kv(IP_ADDRESS, ipAddress)));
        
        structuredArguments.add(kv(THREE_DS_REQUIRED, orderRequestBuilder.getWorldpayTemplateData().isRequires3ds()));
        
        return structuredArguments.toArray(new StructuredArgument[structuredArguments.size()]);
    }
}
