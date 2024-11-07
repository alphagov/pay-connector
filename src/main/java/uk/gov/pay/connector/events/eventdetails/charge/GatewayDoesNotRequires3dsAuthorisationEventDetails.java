package uk.gov.pay.connector.events.eventdetails.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Optional;

public class GatewayDoesNotRequires3dsAuthorisationEventDetails extends EventDetails {

    private final boolean requires3DS;

    private GatewayDoesNotRequires3dsAuthorisationEventDetails() {
        this.requires3DS = false;
    }

    public static GatewayDoesNotRequires3dsAuthorisationEventDetails from() {
        return new GatewayDoesNotRequires3dsAuthorisationEventDetails();
    }

    @JsonProperty("requires_3ds")
    public boolean isRequires3DS() {
        return requires3DS;
    }
}
