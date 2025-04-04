package uk.gov.pay.connector.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.io.IOException;

import static java.lang.String.format;
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
}
