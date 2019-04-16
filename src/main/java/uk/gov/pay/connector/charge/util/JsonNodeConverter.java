package uk.gov.pay.connector.charge.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import uk.gov.pay.connector.charge.exception.JsonNodeDatabaseConversionException;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.io.IOException;
import java.sql.SQLException;

@Converter
public class JsonNodeConverter implements AttributeConverter<JsonNode, PGobject> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public PGobject convertToDatabaseColumn(JsonNode jsonNode) {
        PGobject pgJson = new PGobject();
        pgJson.setType("jsonb");
        try {
            pgJson.setValue(mapper.writeValueAsString(jsonNode));
            mapper.writeValueAsString(jsonNode);
        } catch (SQLException | JsonProcessingException e) {
            throw new JsonNodeDatabaseConversionException("Cannot serialise JsonNode to PGObject");
        }
        return pgJson;
    }

    @Override
    public JsonNode convertToEntityAttribute(PGobject dbData) {
        if (dbData == null) {
            return null;
        }

        try {
            return mapper.readValue(dbData.toString(), JsonNode.class);
        } catch (IOException e) {
            throw new JsonNodeDatabaseConversionException("Cannot deserialize database value to JsonNode");
        }
    }
}
