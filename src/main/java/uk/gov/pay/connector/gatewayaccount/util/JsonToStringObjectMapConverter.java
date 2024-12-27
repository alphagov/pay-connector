package uk.gov.pay.connector.gatewayaccount.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@Converter
public class JsonToStringObjectMapConverter implements AttributeConverter<Map<String,Object>, PGobject> {
    
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PGobject convertToDatabaseColumn(Map<String,Object> stringObjectMap) {
        PGobject pGobject = new PGobject();
        pGobject.setType("json");
        try {
            pGobject.setValue(objectMapper.writeValueAsString(stringObjectMap));
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return pGobject;
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(PGobject pGobject) {
        try {
            return objectMapper.readValue(pGobject.toString(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
