package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.model.spike.StateTransitionsNew;
import uk.gov.pay.connector.service.PaymentGatewayName;

class PaymentGatewayStateTransitions {
    private static StateTransitions defaultTransitions = new DefaultStateTransitions();

    static StateTransitions stateTransitionsFor(PaymentGatewayName gatewayName) {
        return defaultTransitions;
    }
}
