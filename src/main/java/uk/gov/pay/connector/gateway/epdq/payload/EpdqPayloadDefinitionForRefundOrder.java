package uk.gov.pay.connector.gateway.epdq.payload;

import uk.gov.pay.connector.gateway.model.OrderRequestType;

public class EpdqPayloadDefinitionForRefundOrder extends EpdqPayloadDefinitionForMaintenanceOrder {

    @Override
    protected String getOperationType() {
        return "RFD";
    }

    @Override
    protected OrderRequestType getOrderRequestType() {
        return OrderRequestType.REFUND;
    }

}
