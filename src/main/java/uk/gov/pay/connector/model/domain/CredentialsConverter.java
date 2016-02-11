package uk.gov.pay.connector.model.domain;

import org.postgresql.util.PGobject;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.sql.SQLException;

@Converter
public class CredentialsConverter implements AttributeConverter<String, PGobject> {
    @Override
    public PGobject convertToDatabaseColumn(String credentials) {
        PGobject pgCredentials = new PGobject();
        pgCredentials.setType("json");
        try {
            pgCredentials.setValue(credentials);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return pgCredentials;
    }

    @Override
    public String convertToEntityAttribute(PGobject credentials) {
        return credentials.toString();
    }
}
