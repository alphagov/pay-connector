package uk.gov.pay.connector.dao;

import org.apache.commons.lang3.StringUtils;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.util.StringMapper;
import uk.gov.pay.connector.model.ChargeStatus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;

public class TokenDao {
    private DBI jdbi;

    public TokenDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public void insertNewToken(String chargeId, String tokenId) {
        int rowsInserted = jdbi.withHandle(handle ->
                        handle
                                .createStatement("INSERT INTO tokens(charge_id, token_id) VALUES (:charge_id, :token_id)")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .bind("token_id", tokenId)
                                .execute()
        );
        if (rowsInserted != 1) {
            throw new RuntimeException("Unexpected: Failed to insert new chargeTokenId into tokens table");
        }
    }

    public String findByChargeId(String chargeId) {
        String tokenId = jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT token_id FROM tokens WHERE charge_id=:charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .map(StringMapper.FIRST)
                                .first()
        );

        if(StringUtils.isEmpty(tokenId)) {
            throw new RuntimeException("Unexpected: tokenId was not found for chargeId=" + chargeId);
        }

        return tokenId;
    }

    public Optional<String> findChargeByTokenId(String chargeTokenId) {
        String chargeId = jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT charge_id FROM tokens WHERE token_id=:token_id")
                                .bind("token_id", chargeTokenId)
                                .map(StringMapper.FIRST)
                                .first()
        );

        return Optional.ofNullable(chargeId);
    }

    public void deleteByTokenId(String tokenId) {
        int rowsDeleted = jdbi.withHandle(handle ->
                        handle
                                .createStatement("DELETE from tokens where token_id = :token_id")
                                .bind("token_id", tokenId)
                                .execute()
        );
        if (rowsDeleted != 1) {
            throw new RuntimeException(format("Unexpected: Failed to delete chargeTokenId='%s' from tokens table", tokenId));
        }
    }
}
