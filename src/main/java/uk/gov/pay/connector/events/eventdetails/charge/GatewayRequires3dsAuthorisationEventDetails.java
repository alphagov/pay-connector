package uk.gov.pay.connector.events.eventdetails.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.paymentprocessor.model.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Optional;

public class GatewayRequires3dsAuthorisationEventDetails extends EventDetails {

    private final boolean requires3DS;
    private String version3DS;

    public GatewayRequires3dsAuthorisationEventDetails(String version3DS) {
        this.requires3DS = true;
        this.version3DS = version3DS;
    }

    public static GatewayRequires3dsAuthorisationEventDetails from(ChargeEntity charge) {
        return new GatewayRequires3dsAuthorisationEventDetails(
                Optional.ofNullable(charge.getChargeCardDetails().get3dsRequiredDetails())
                        .map(Auth3dsRequiredEntity::getThreeDsVersion)
                        .orElse(null)
        );
    }

    @JsonProperty("version_3ds")
    public String getVersion3DS() {
        return version3DS;
    }

    @JsonProperty("requires_3ds")
    public boolean isRequires3DS() {
        return requires3DS;
    }
}
