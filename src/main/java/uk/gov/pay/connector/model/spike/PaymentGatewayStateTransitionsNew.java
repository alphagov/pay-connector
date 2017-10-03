package uk.gov.pay.connector.model.spike;

import uk.gov.pay.connector.service.PaymentGatewayName;

public class PaymentGatewayStateTransitionsNew {
  private static StateTransitionsNew defaultTransitions = new DefaultStateTransitionsNew();

  public static StateTransitionsNew stateTransitionsFor(PaymentGatewayName gatewayName) {
    return defaultTransitions;
  }
}

