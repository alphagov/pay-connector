package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.IOException;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class GatewayCredentialsApiSerializer extends JsonSerializer<GatewayCredentials> {
    private final ObjectMapper objectMapper = JsonMapper.builder().disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .addModule(new Jdk8Module())
            .serializationInclusion(NON_NULL)
            .build();
    
    @Override
    public void serialize(GatewayCredentials credentials, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        objectMapper.writerWithView(GatewayCredentials.Views.Api.class)
                .writeValue(jsonGenerator, credentials);
    }
}
