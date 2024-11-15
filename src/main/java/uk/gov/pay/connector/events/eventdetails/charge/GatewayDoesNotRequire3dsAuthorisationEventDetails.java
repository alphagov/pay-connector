package uk.gov.pay.connector.events.eventdetails.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Objects;

public class GatewayDoesNotRequire3dsAuthorisationEventDetails extends EventDetails {

    private final boolean requires3DS = false;

    @JsonProperty("requires_3ds")
    public boolean isRequires3DS() {
        return requires3DS;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GatewayDoesNotRequire3dsAuthorisationEventDetails &&
                this.requires3DS == ((GatewayDoesNotRequire3dsAuthorisationEventDetails) o).requires3DS;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requires3DS);
    }
}
