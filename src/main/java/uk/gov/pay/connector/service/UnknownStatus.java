package uk.gov.pay.connector.service;

import static uk.gov.pay.connector.service.InterpretedStatus.Type.UNKNOWN;

public class UnknownStatus implements InterpretedStatus {

    @Override
    public Type getType() {
        return UNKNOWN;
    }
}
