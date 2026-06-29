package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus.NOT_STARTED;

@JsonInclude(Include.NON_NULL)
public class AdyenAccountSetup {
    
    @JsonProperty("service_id")
    private String serviceId;

    @JsonProperty("credential_external_id")
    private String credentialExternalId;

    @JsonProperty("gateway_account_id")
    private long gatewayAccountId;
    
    @JsonProperty("tasks")
    private Map<String, String> tasks;
    
    public Map<String, Map<String, AdyenAccountSetupStatus>> getTasks() {
        var setupTasks = new HashMap<String, Map<String, AdyenAccountSetupStatus>>();
        Arrays.stream(AdyenAccountSetupTask.values()).forEach(task -> 
                setupTasks.put(task.getValue(), 
                Map.of("status", NOT_STARTED)));
        
        return setupTasks;
    }

    public void setGatewayAccountId(long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
    }

    public void setCredentialExternalId(String credentialExternalId) {
        this.credentialExternalId = credentialExternalId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }



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
