package uk.gov.pay.connector.gateway.model.status;

public class IgnoredStatus implements InterpretedStatus {

    @Override
    public Type getType() {
        return Type.IGNORED;
    }
}
