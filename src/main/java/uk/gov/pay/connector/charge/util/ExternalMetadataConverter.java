package uk.gov.pay.connector.charge.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.exception.ExternalMetadataConverterException;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@Converter
public class ExternalMetadataConverter implements AttributeConverter<ExternalMetadata, PGobject> {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public PGobject convertToDatabaseColumn(ExternalMetadata externalMetadata) {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");

        if (externalMetadata == null) {
            return pgObject;
        }

        try {
            pgObject.setValue(mapper.writeValueAsString(externalMetadata.getMetadata()));
        } catch (JsonProcessingException | SQLException e) {
            throw new ExternalMetadataConverterException("Failed to serialise externalMetadata");
        }
        return pgObject;
    }

    @Override
    public ExternalMetadata convertToEntityAttribute(PGobject dbData) {
        if (dbData == null) {
            return null;
        }

        try {
            Map<String, Object> metadata = mapper.readValue(dbData.toString(), new TypeReference<Map<String, Object>>() {});
            return new ExternalMetadata(metadata);
        } catch (IOException e) {
            throw new ExternalMetadataConverterException("Failed to deserialise metadata to externalMetadata");
        }
    }
}
