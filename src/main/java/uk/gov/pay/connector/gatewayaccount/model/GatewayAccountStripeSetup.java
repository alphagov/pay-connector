package uk.gov.pay.connector.gatewayaccount.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class GatewayAccountStripeSetup {

    @JsonProperty("bank_account_details")
    private boolean bankDetailsCompleted = false;

    @JsonProperty("responsible_person")
    private boolean responsiblePersonCompleted = false;

    @JsonProperty("organisation_vat_number_company_number")
    private boolean organisationDetailsCompleted = false;
    
    public boolean isBankDetailsCompleted() {
        return bankDetailsCompleted;
    }

    public void setBankDetailsCompleted(boolean bankDetailsCompleted) {
        this.bankDetailsCompleted = bankDetailsCompleted;
    }

    public boolean isResponsiblePersonCompleted() {
        return responsiblePersonCompleted;
    }

    public void setResponsiblePersonCompleted(boolean responsiblePersonCompleted) {
        this.responsiblePersonCompleted = responsiblePersonCompleted;
    }

    public boolean isOrganisationDetailsCompleted() {
        return organisationDetailsCompleted;
    }

    public void setOrganisationDetailsCompleted(boolean organisationDetailsCompleted) {
        this.organisationDetailsCompleted = organisationDetailsCompleted;
    }

}
