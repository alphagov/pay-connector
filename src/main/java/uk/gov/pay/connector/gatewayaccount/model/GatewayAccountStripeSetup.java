package uk.gov.pay.connector.gatewayaccount.model;

public class GatewayAccountStripeSetup {

    private boolean bankDetailsCompleted = false;
    private boolean responsiblePersonCompleted = false;
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
