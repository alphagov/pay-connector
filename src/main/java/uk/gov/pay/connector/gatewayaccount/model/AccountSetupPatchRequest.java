package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.service.payments.commons.api.validation.AllowedStrings;

import jakarta.validation.Valid;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountSetupPatchRequest(
        @Valid
        @AllowedStrings(allowed = {"replace"},  message = "The op field must be 'replace'")
        String op,

        @Valid
        @AllowedStrings(
                allowed = { "bank_account", "responsible_person", "vat_number", "company_number", "director", "government_entity_document", "organisation_details" },
                message = "The paths field must be one of: [bank_account, responsible_person, vat_number, company_number, director, government_entity_document, organisation_details]"
        )
        String path,
        String value
) {
}

