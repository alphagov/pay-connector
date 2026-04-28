package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonView;

import java.util.Map;

public record AdyenCredentials(@JsonView({Views.Api.class}) String legalEntityId,
                               @JsonView({Views.Api.class}) String storeId) implements GatewayCredentials {

    public static final String ADYEN_LEGAL_ENTITY_ID = "legal_entity_id";
    public static final String ADYEN_STORE_ID = "store_id";

    public Map<String, String> toMap() {
        return Map.of(
                ADYEN_LEGAL_ENTITY_ID, legalEntityId,
                ADYEN_STORE_ID, storeId
        );
    }

    @Override
    public boolean hasCredentials() {
        return legalEntityId != null;
    }
}
