package uk.gov.pay.connector.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.util.Map;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

public class JsonObjectMapper {
    private final Logger logger = LoggerFactory.getLogger(JsonObjectMapper.class);
    private ObjectMapper objectMapper;

    @Inject
    public JsonObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T getObject(String jsonResponse, Class<T> targetType) {
        try {
            return objectMapper.readValue(jsonResponse, targetType);
        } catch (IOException e) {
            logger.info("There was an exception parsing the payload [{}] into an [{}]", jsonResponse, targetType);
            throw new WebApplicationException(serviceErrorResponse(
                    format("There was an exception parsing the payload [%s] into an [%s], e=[%s]",
                    jsonResponse, targetType, e.getMessage())));
        }
    }

    public Map<String, String> getAsMap(JsonNode jsonNode) {
        if (jsonNode != null) {
            if ((jsonNode.isTextual() && !isEmpty(jsonNode.asText())) || (!jsonNode.isNull() && jsonNode.isObject())) {
                try {
                    return objectMapper.readValue(jsonNode.traverse(), new TypeReference<Map<String, String>>() {});
                } catch (IOException e) {
                    throw new RuntimeException("Malformed JSON object in value", e);
                }
            }
        }
        return null;
    }
}
