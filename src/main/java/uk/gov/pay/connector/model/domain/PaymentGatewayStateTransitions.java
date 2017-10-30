package uk.gov.pay.connector.model.domain;

public class PaymentGatewayStateTransitions {
    private static StateTransitions defaultTransitions = new DefaultStateTransitions();

    public static StateTransitions defaultTransitions() {
        return defaultTransitions;
    }
}
