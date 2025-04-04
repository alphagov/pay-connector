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

import static org.apache.commons.lang3.StringUtils.isEmpty;

@Converter
public class JsonToStringStringMapConverter implements AttributeConverter<Map<String, String>, PGobject> {
    
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PGobject convertToDatabaseColumn(Map<String, String> keyValueMap) {
        PGobject pGobject = new PGobject();
        pGobject.setType("json");
        if(null != keyValueMap && !keyValueMap.isEmpty()) {
            try {
                pGobject.setValue(objectMapper.writeValueAsString(keyValueMap));
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return pGobject;
    }

    @Override
    public Map<String, String> convertToEntityAttribute(PGobject pgObject) {
        try {
            if (pgObject != null && !isEmpty(pgObject.toString())) {
                return objectMapper.readValue(pgObject.toString(), new TypeReference<Map<String, String>>() {});
            }
            return null;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
