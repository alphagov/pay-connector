package uk.gov.pay.connector.usernotification.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.usernotification.model.validation.AllowedStrings;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailNotificationPatchRequest {
    
    @Valid
    @AllowedStrings(allowed = {"replace"},  message = "The op field must be 'replace'")
    private String op;
    
    @Valid
    @AllowedStrings(
            allowed = {"/refund_issued/template_body", "/refund_issued/enabled", "/payment_confirmed/template_body", "/payment_confirmed/enabled"},
            message = "The paths field must be one of: [/refund_issued/template_body, /refund_issued/enabled, /payment_confirmed/template_body, /payment_confirmed/enabled]"
    )
    private String path;
    
    private String value;

    public String getOp() {
        return op;
    }

    public String getPath() {
        return path;
    }

    public String getValue() {
        return value;
    }
    
    public List<String> getPathTokens() {
        return Arrays.stream(path.split("/")).skip(1).collect(Collectors.toList());
    }
}
