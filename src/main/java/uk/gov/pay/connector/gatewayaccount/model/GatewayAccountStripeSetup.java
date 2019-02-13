package uk.gov.pay.connector.gatewayaccount.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class GatewayAccountStripeSetup {

    @JsonProperty("bank_account")
    private boolean bankAccountCompleted = false;

    @JsonProperty("responsible_person")
    private boolean responsiblePersonCompleted = false;

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

    public boolean isOrganisationDetailsCompleted() {
        return organisationDetailsCompleted;
    }

    public void setOrganisationDetailsCompleted(boolean organisationDetailsCompleted) {
        this.organisationDetailsCompleted = organisationDetailsCompleted;
    }

}
