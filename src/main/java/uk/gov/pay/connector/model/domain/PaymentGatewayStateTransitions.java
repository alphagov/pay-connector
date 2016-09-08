package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.resources.PaymentGatewayName;

class PaymentGatewayStateTransitions {

    private static StateTransitions sandboxTransitions = new SandboxStateTransitions();
    private static StateTransitions defaultTransitions = new DefaultStateTransitions();

    static StateTransitions stateTransitionsFor(PaymentGatewayName gatewayName) {
        if (gatewayName == PaymentGatewayName.SANDBOX) {
            return sandboxTransitions;
        }
        return defaultTransitions;
    }
}
