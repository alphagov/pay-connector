package uk.gov.pay.connector.gateway.util;

import net.logstash.logback.argument.StructuredArgument;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;

import java.util.ArrayList;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class AuthorisationRequestSummaryStructuredLogging {
    
    public static final String BILLING_ADDRESS = "billing_address";
    public static final String DATA_FOR_3DS = "data_for_3ds";
    public static final String DATA_FOR_3DS2 = "data_for_3ds2";
    public static final String WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT = "worldpay_3ds_flex_device_data_collection_result";

    public StructuredArgument[] createArgs(AuthorisationRequestSummary authorisationRequestSummary) {
        var structuredArguments = new ArrayList<StructuredArgument>();
        
        switch (authorisationRequestSummary.billingAddress()) {
            case PRESENT:
                structuredArguments.add(kv(BILLING_ADDRESS, true));
                break;
            case NOT_PRESENT:
                structuredArguments.add(kv(BILLING_ADDRESS, false));
                break;
            default:
                break;
        }

        switch (authorisationRequestSummary.dataFor3ds()) {
            case PRESENT:
                structuredArguments.add(kv(DATA_FOR_3DS, true));
                break;
            case NOT_PRESENT:
                structuredArguments.add(kv(DATA_FOR_3DS, false));
                break;
            default:
                break;
        }

        switch (authorisationRequestSummary.dataFor3ds2()) {
            case PRESENT:
                structuredArguments.add(kv(DATA_FOR_3DS2, true));
                break;
            case NOT_PRESENT:
                structuredArguments.add(kv(DATA_FOR_3DS2, false));
                break;
            default:
                break;
        }

        switch (authorisationRequestSummary.deviceDataCollectionResult()) {
            case PRESENT:
                structuredArguments.add(kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, true));
                break;
            case NOT_PRESENT:
                structuredArguments.add(kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, false));
                break;
            default:
                break;
        }

        return structuredArguments.toArray(new StructuredArgument[structuredArguments.size()]);
    }

}
