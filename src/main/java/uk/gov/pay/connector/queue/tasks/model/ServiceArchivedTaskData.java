package uk.gov.pay.connector.queue.tasks.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ServiceArchivedTaskData {
    
    @JsonProperty("service_external_id")
    private String serviceId;

    public ServiceArchivedTaskData() {
    }

    public ServiceArchivedTaskData(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceId() {
        return serviceId;
    }
}
