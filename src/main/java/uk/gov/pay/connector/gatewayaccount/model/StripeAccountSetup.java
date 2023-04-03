package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class StripeAccountSetup {

    @JsonProperty("bank_account")
    private boolean bankAccountCompleted = false;

    @JsonProperty("responsible_person")
    private boolean responsiblePersonCompleted = false;

    @JsonProperty("vat_number")
    private boolean vatNumberCompleted = false;

    @JsonProperty("company_number")
    private boolean companyNumberCompleted = false;

    @JsonProperty("director")
    private boolean directorCompleted = false;

    @JsonProperty("government_entity_document")
    private boolean governmentEntityDocument = false;
    
    @JsonProperty("organisation_details")
    private boolean organisationDetailsCompleted = false;

    public boolean isBankAccountCompleted() {
        return bankAccountCompleted;
    }

    public void setBankAccountCompleted(boolean bankAccountCompleted) {
        this.bankAccountCompleted = bankAccountCompleted;
    }

    public boolean isResponsiblePersonCompleted() {
        return responsiblePersonCompleted;
    }

    public void setResponsiblePersonCompleted(boolean responsiblePersonCompleted) {
        this.responsiblePersonCompleted = responsiblePersonCompleted;
    }

    public void setVatNumberCompleted(boolean vatNumberCompleted) {
        this.vatNumberCompleted = vatNumberCompleted;
    }

    public boolean isVatNumberCompleted() {
        return vatNumberCompleted;
    }

    public void setCompanyNumberCompleted(boolean companyNumberCompleted) {
        this.companyNumberCompleted = companyNumberCompleted;
    }

    public boolean isCompanyNumberCompleted() {
        return companyNumberCompleted;
    }

    public void setDirectorCompleted(boolean directorCompleted) {
        this.directorCompleted = directorCompleted;
    }

    public boolean isDirectorCompleted() {
        return directorCompleted;
    }

    public boolean isGovernmentEntityDocument() {
        return governmentEntityDocument;
    }

    public void setGovernmentEntityDocument(boolean governmentEntityDocument) {
        this.governmentEntityDocument = governmentEntityDocument;
    }

    public boolean isOrganisationDetailsCompleted() {
        return organisationDetailsCompleted;
    }

    public void setOrganisationDetailsCompleted(boolean organisationDetailsCompleted) {
        this.organisationDetailsCompleted = organisationDetailsCompleted;
    }
}
