package uk.gov.pay.connector.gateway.epdq;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.Optional;

public class ChargeQueryResponse {
    private final ChargeStatus mappedStatus;
    private final String rawGatewayResponse;

    public ChargeQueryResponse(ChargeStatus mappedStatus, String rawGatewayResponse) {
        this.mappedStatus = mappedStatus;
        this.rawGatewayResponse = rawGatewayResponse;
    }

    public Optional<ChargeStatus> getMappedStatus() {
        return Optional.ofNullable(mappedStatus);
    }

    public String getRawGatewayResponse() {
        return rawGatewayResponse;
    }
}
