package uk.gov.pay.connector.dao;

import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IChargeDao {
    String saveNewCharge(String gatewayAccountId, Map<String, Object> charge);

    Optional<Map<String, Object>> findChargeForAccount(String chargeId, String accountId);

    Optional<Map<String, Object>> findById(String chargeId);

    Optional<ChargeEntity> findById(Long chargeId);

    void updateGatewayTransactionId(String chargeId, String transactionId);

    void updateStatusWithGatewayInfo(String provider, String gatewayTransactionId, ChargeStatus newStatus);

    void updateStatus(String chargeId, ChargeStatus newStatus);

    // updates the new status only if the charge is in one of the old statuses and returns num of rows affected
    // very specific transition happening here so check for a valid state before transitioning
    int updateNewStatusWhereOldStatusIn(String chargeId, ChargeStatus newStatus, List<ChargeStatus> oldStatuses);

    List<Map<String, Object>> findAllBy(String gatewayAccountId, String reference, ExternalChargeStatus status,
                                        String fromDate, String toDate);

    Optional<String> findAccountByTransactionId(String provider, String transactionId);
}
