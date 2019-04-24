package uk.gov.pay.connector.charge.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import uk.gov.pay.commons.model.charge.ExternalMetadata;

import java.io.IOException;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ExternalMetadataSerialiser extends StdSerializer<ExternalMetadata> {

    public ExternalMetadataSerialiser() {
        this(null);
    }

    private ExternalMetadataSerialiser(Class<ExternalMetadata> t) {
        super(t);
    }

    @Override
    public void serialize(ExternalMetadata externalMetadata, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        serializerProvider.defaultSerializeValue(externalMetadata.getMetadata(), jsonGenerator);
    }
}
