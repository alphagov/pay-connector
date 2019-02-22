package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.epdq.EpdqStatusMapper;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqQueryResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayQueryResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayStatus;

import java.util.Optional;

public class ChargeQueryResponse {
    private ChargeStatus mappedStatus;
    private final String rawGatewayResponse;

    public ChargeQueryResponse(ChargeStatus mappedStatus, String rawGatewayResponse) {
        this.mappedStatus = mappedStatus;
        this.rawGatewayResponse = rawGatewayResponse;
    }

    public ChargeQueryResponse(WorldpayStatus worldpayStatus, String rawGatewayResponse) {
        if (worldpayStatus != null) {
            this.mappedStatus = worldpayStatus.getPayStatus();
        }
        
        this.rawGatewayResponse = rawGatewayResponse;
    }

    public Optional<ChargeStatus> getMappedStatus() {
        return Optional.ofNullable(mappedStatus);
    }

    public String getRawGatewayResponse() {
        return rawGatewayResponse;
    }

    public static ChargeQueryResponse from(WorldpayQueryResponse worldpayInquiryStatusResponse) {
        return new ChargeQueryResponse(
                WorldpayStatus.fromString(worldpayInquiryStatusResponse.getLastEvent()).orElse(null),
                worldpayInquiryStatusResponse.toString()
        );
    }

    public static ChargeQueryResponse from(EpdqQueryResponse epdqQueryResponse) {
        return new ChargeQueryResponse(
                EpdqStatusMapper.map(epdqQueryResponse.getStatus()),
                epdqQueryResponse.toString()
        );
    }
}
