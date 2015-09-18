package uk.gov.pay.connector.dao;

import org.apache.commons.lang3.StringUtils;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.exceptions.DBIException;
import org.skife.jdbi.v2.util.StringMapper;

import java.util.Optional;

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
            throw new PayDBIException(format("Unexpected: Failed to insert new chargeTokenId = '%s' into tokens table", tokenId));
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
            throw new PayDBIException(format("Unexpected: tokenId was not found for chargeId = '%s'", chargeId));
        }

        return tokenId;
    }

    public Optional<String> findChargeByTokenId(String tokenId) {
        String chargeId = jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT charge_id FROM tokens WHERE token_id=:token_id")
                                .bind("token_id", tokenId)
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
            throw new PayDBIException(format("Unexpected: Failed to delete chargeTokenId = '%s' from tokens table", tokenId));
        }
    }
}
