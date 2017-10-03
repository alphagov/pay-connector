package uk.gov.pay.connector.model.spike;

import static java.util.Collections.emptyList;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus;

public abstract class StateTransitionsNew {
  private Map<TransactionStatus, List<TransactionStatus>> transitionTable;

  StateTransitionsNew(Map<TransactionStatus, List<TransactionStatus>> transitionTable) {
    this.transitionTable = transitionTable;
  }

  public boolean isValidTransition(TransactionStatus state, TransactionStatus targetState) {
    return transitionTable.getOrDefault(state, emptyList()).contains(targetState);
  }

  static ImmutableList<TransactionStatus> validTransitions(TransactionStatus... statuses) {
    return ImmutableList.copyOf(statuses);
  }
}


