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

    @JsonProperty("vat_number_company_number")
    private boolean vatNumberCompanyNumberCompleted = false;

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

    public boolean isVatNumberCompanyNumberCompleted() {
        return vatNumberCompanyNumberCompleted;
    }

    public void setVatNumberCompanyNumberCompleted(boolean vatNumberCompanyNumberCompleted) {
        this.vatNumberCompanyNumberCompleted = vatNumberCompanyNumberCompleted;
    }

}
