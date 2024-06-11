package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.pay.connector.util.validation.AllowedStrings;

import javax.validation.Valid;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StripeSetupPatchRequest {
    
    @Valid
    @AllowedStrings(allowed = {"replace"},  message = "The op field must be 'replace'")
    private String op;
    
    @Valid
    @AllowedStrings(
            allowed = { "bank_account", "responsible_person", "vat_number", "company_number", "director", "government_entity_document", "organisation_details" },
            message = "The paths field must be one of: [bank_account, responsible_person, vat_number, company_number, director, government_entity_document, organisation_details]"
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
}

