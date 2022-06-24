package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseInquiryResponse;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ChargeQueryResponse {
    private ChargeStatus mappedStatus;
    private GatewayError gatewayError;
    private BaseInquiryResponse rawGatewayResponse;

    public ChargeQueryResponse(ChargeStatus mappedStatus, BaseInquiryResponse rawGatewayResponse) {
        this.mappedStatus = mappedStatus;
        this.rawGatewayResponse = rawGatewayResponse;
    }

    public ChargeQueryResponse(GatewayError gatewayError) {
        this.gatewayError = gatewayError;
    }

    public Optional<ChargeStatus> getMappedStatus() {
        return Optional.ofNullable(mappedStatus);
    }

    public Optional<BaseInquiryResponse> getRawGatewayResponse() {
        return Optional.ofNullable(rawGatewayResponse);
    }

    public String getRawGatewayResponseOrErrorMessage() {
        return getRawGatewayResponse()
                .map(Object::toString)
                .or(() -> getGatewayError().map(GatewayError::getMessage))
                .orElse(EMPTY);
    }

    public boolean foundCharge() {
        return getRawGatewayResponse()
                .map(response -> isNotBlank(response.getTransactionId()))
                .orElse(false);
    }

    public Optional<GatewayError> getGatewayError() {
        return Optional.ofNullable(gatewayError);
    }
}
