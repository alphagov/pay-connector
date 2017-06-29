package uk.gov.pay.connector.service;

import static uk.gov.pay.connector.service.InterpretedStatus.Type.IGNORED;

public class IgnoredStatus implements InterpretedStatus {

    @Override
    public Type getType() {
        return IGNORED;
    }
}
