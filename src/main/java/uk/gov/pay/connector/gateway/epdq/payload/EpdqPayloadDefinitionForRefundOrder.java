package uk.gov.pay.connector.gateway.epdq.payload;

import uk.gov.pay.connector.gateway.model.OrderRequestType;

import javax.inject.Singleton;

@Singleton
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
