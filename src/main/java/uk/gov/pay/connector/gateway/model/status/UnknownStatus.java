package uk.gov.pay.connector.gateway.model.status;

public class UnknownStatus implements InterpretedStatus {

    @Override
    public Type getType() {
        return Type.UNKNOWN;
    }
}
