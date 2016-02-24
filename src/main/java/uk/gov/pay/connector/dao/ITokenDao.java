package uk.gov.pay.connector.dao;

import java.util.Optional;

public interface ITokenDao {

    void insertNewToken(String chargeId, String tokenId);

    String findByChargeId(String chargeId);

    Optional<String> findChargeByTokenId(String tokenId);

    void deleteByTokenId(String tokenId);
}
