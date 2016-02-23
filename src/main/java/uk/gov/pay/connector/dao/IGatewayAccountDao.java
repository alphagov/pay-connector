package uk.gov.pay.connector.dao;

import uk.gov.pay.connector.model.domain.GatewayAccount;
import java.util.Optional;

public interface IGatewayAccountDao {

    String createGatewayAccount(String paymentProvider);

    boolean idIsMissing(String gatewayAccountId);

    Optional<GatewayAccount> findById(String gatewayAccountId);

    void saveCredentials(String credentialsJsonString, String gatewayAccountId);
}
