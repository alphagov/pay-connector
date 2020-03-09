package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.response.BaseInquiryResponse;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ChargeQueryResponse {
    private ChargeStatus mappedStatus;
    private final BaseInquiryResponse rawGatewayResponse;

    public ChargeQueryResponse(ChargeStatus mappedStatus, BaseInquiryResponse rawGatewayResponse) {
        this.mappedStatus = mappedStatus;
        this.rawGatewayResponse = rawGatewayResponse;
    }

    public Optional<ChargeStatus> getMappedStatus() {
        return Optional.ofNullable(mappedStatus);
    }

    public String getRawGatewayResponseString() {
        return rawGatewayResponse.toString();
    }
    
    public boolean foundCharge() {
        return isNotBlank(rawGatewayResponse.getTransactionId());
    }
}
