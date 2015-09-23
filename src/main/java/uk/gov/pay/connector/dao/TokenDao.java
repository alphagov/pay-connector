package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
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
                                .createStatement("INSERT INTO tokens(charge_id, secure_redirect_token) VALUES (:charge_id, :secure_redirect_token)")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .bind("secure_redirect_token", tokenId)
                                .execute()
        );
        if (rowsInserted != 1) {
            throw new PayDBIException(format("Unexpected: Failed to insert new chargeTokenId = '%s' into tokens table", tokenId));
        }
    }

    public String findByChargeId(String chargeId) {
        String tokenId = jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT secure_redirect_token FROM tokens WHERE charge_id=:charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .map(StringMapper.FIRST)
                                .first()
        );

        return tokenId;
    }

    public Optional<String> findChargeByTokenId(String tokenId) {
        String chargeId = jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT charge_id FROM tokens WHERE secure_redirect_token = :secure_redirect_token")
                                .bind("secure_redirect_token", tokenId)
                                .map(StringMapper.FIRST)
                                .first()
        );

        return Optional.ofNullable(chargeId);
    }

    public void deleteByTokenId(String tokenId) {
        int rowsDeleted = jdbi.withHandle(handle ->
                        handle
                                .createStatement("DELETE from tokens where secure_redirect_token = :secure_redirect_token")
                                .bind("secure_redirect_token", tokenId)
                                .execute()
        );
        if (rowsDeleted != 1) {
            throw new PayDBIException(format("Unexpected: Failed to delete chargeTokenId = '%s' from tokens table", tokenId));
        }
    }
}
