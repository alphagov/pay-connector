package uk.gov.pay.connector.gateway.epdq.payload;

import uk.gov.pay.connector.gateway.model.OrderRequestType;

import javax.inject.Singleton;

@Singleton
public class EpdqPayloadDefinitionForCaptureOrder extends EpdqPayloadDefinitionForMaintenanceOrder {
    @Override
    protected String getOperationType() {
        return "SAS";
    }

    @Override
    protected OrderRequestType getOrderRequestType() {
        return OrderRequestType.CAPTURE;
    }
}
