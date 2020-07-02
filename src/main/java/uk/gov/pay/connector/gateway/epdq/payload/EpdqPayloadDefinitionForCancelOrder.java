package uk.gov.pay.connector.gateway.epdq.payload;

import uk.gov.pay.connector.gateway.model.OrderRequestType;

public class EpdqPayloadDefinitionForCancelOrder extends EpdqPayloadDefinitionForMaintenanceOrder {

    @Override
    protected String getOperationType() {
        return "DES";
    }

    @Override
    protected OrderRequestType getOrderRequestType() {
        return OrderRequestType.CANCEL;
    }

}
