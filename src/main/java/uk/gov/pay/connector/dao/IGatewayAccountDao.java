package uk.gov.pay.connector.dao;

import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.Optional;

public interface IGatewayAccountDao {

    String createGatewayAccount(String paymentProvider);

    boolean idIsMissing(String gatewayAccountId);

    Optional<GatewayAccount> findById(String gatewayAccountId);

    Optional<GatewayAccountEntity> findById(Long gatewayAccountId);

    void saveCredentials(String credentialsJsonString, String gatewayAccountId);
}
