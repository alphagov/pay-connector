package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.ApiResource;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class PaymentIntentDeserializer extends JsonDeserializer<PaymentIntent> {

    @Override
    public PaymentIntent deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        String valueAsString = p.getValueAsString();

        if (isBlank(valueAsString)) {
            return null;
        }
        
        return ApiResource.GSON.fromJson(valueAsString, PaymentIntent.class);
    }
}
