package uk.gov.pay.connector.util;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Priority(1)
public class JsonMappingExceptionMapper implements ExceptionMapper<JsonMappingException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonMappingExceptionMapper.class);

    @Override
    public Response toResponse(JsonMappingException exception) {
        LOGGER.error(exception.getMessage());
        Map<String, String> entity = Map.of("message", sanitiseExceptionMessage(exception.getMessage()));
        return Response.status(400).entity(entity).type(APPLICATION_JSON).build();
    }

    private String sanitiseExceptionMessage(String message) {
        if (message.contains("Field [metadata] must be an object of JSON key-value pairs")) {
            return "Field [metadata] must be an object of JSON key-value pairs";
        }

        return message;
    }
}
