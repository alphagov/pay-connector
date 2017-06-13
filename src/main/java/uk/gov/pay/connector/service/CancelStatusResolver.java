package uk.gov.pay.connector.service;

import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;

import java.util.Optional;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.DeferredStatusResolver;
import uk.gov.pay.connector.model.domain.InternalExternalStatus;
import uk.gov.pay.connector.service.StatusFlow;

public class CancelStatusResolver implements DeferredStatusResolver {

  @Override
  public Optional<InternalExternalStatus> resolve(ChargeEntity charge) {

    ChargeStatus currentChargeStatus = ChargeStatus.fromString(charge.getStatus());

    StatusFlow[] cancelStatusFlows = { StatusFlow.USER_CANCELLATION_FLOW, StatusFlow.SYSTEM_CANCELLATION_FLOW, StatusFlow.EXPIRE_FLOW };

    for (StatusFlow cancelStatusFlow : cancelStatusFlows) {
      if (cancelStatusFlow.getSubmittedState().equals(currentChargeStatus)) return Optional.of(cancelStatusFlow.getSuccessTerminalState());
    }

    return Optional.of(SYSTEM_CANCELLED);
  }
}
