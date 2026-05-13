package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonView;

import java.util.Map;

public record AdyenCredentials(@JsonView({Views.Api.class}) String legalEntityId,
                               @JsonView({Views.Api.class}) String storeId,
                               @JsonView({Views.Api.class}) String accountHolderId,
                               @JsonView({Views.Api.class}) String balanceAccountId) implements GatewayCredentials {

    public static final String ADYEN_LEGAL_ENTITY_ID = "legal_entity_id";
    public static final String ADYEN_STORE_ID = "store_id";
    public static final String ADYEN_ACCOUNT_HOLDER_ID = "account_holder_id";
    public static final String ADYEN_BALANCE_ACCOUNT_ID = "balance_account_id";

    public Map<String, String> toMap() {
        return Map.of(
                ADYEN_LEGAL_ENTITY_ID, legalEntityId,
                ADYEN_STORE_ID, storeId,
                ADYEN_ACCOUNT_HOLDER_ID, accountHolderId,
                ADYEN_BALANCE_ACCOUNT_ID, balanceAccountId
        );
    }

    @Override
    public boolean hasCredentials() {
        return legalEntityId != null;
    }
}
