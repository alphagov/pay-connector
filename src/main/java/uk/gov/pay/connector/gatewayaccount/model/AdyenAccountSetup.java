package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class AdyenAccountSetup {

    @JsonProperty("bank_account")
    private AdyenAccountSetupStatus bankAccountStatus;

    @JsonProperty("responsible_person")
    private AdyenAccountSetupStatus responsiblePersonStatus;

    @JsonProperty("vat_number")
    private AdyenAccountSetupStatus vatNumberStatus;

    @JsonProperty("company_number")
    private AdyenAccountSetupStatus companyNumberStatus;

    @JsonProperty("director")
    private AdyenAccountSetupStatus directorStatus;

    @JsonProperty("government_entity_document")
    private AdyenAccountSetupStatus governmentEntityDocument;

    @JsonProperty("organisation_details")
    private AdyenAccountSetupStatus organisationDetailsStatus;

    public void setBankAccountStatus(AdyenAccountSetupStatus bankAccountStatus) {
        this.bankAccountStatus = bankAccountStatus;
    }

    public void setResponsiblePersonStatus(AdyenAccountSetupStatus responsiblePersonStatus) {
        this.responsiblePersonStatus = responsiblePersonStatus;
    }

    public void setVatNumberStatus(AdyenAccountSetupStatus vatNumberStatus) {
        this.vatNumberStatus = vatNumberStatus;
    }

    public void setCompanyNumberStatus(AdyenAccountSetupStatus companyNumberStatus) {
        this.companyNumberStatus = companyNumberStatus;
    }

    public void setGovernmentEntityDocument(AdyenAccountSetupStatus governmentEntityDocument) {
        this.governmentEntityDocument = governmentEntityDocument;
    }

    public void setDirectorStatus(AdyenAccountSetupStatus directorStatus) {
        this.directorStatus = directorStatus;
    }

    public void setOrganisationDetailsStatus(AdyenAccountSetupStatus organisationDetailsStatus) {
        this.organisationDetailsStatus = organisationDetailsStatus;
    }
}
