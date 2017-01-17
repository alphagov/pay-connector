package uk.gov.pay.connector.util.templates;

import org.apache.http.client.utils.URLEncodedUtils;
import uk.gov.pay.connector.service.OrderRequestBuilder.TemplateData;

import java.nio.charset.StandardCharsets;

public class FormUrlEncodedStringBuilder implements PayloadBuilder {

    private final PayloadDefinition payloadDefinition;

    public FormUrlEncodedStringBuilder(PayloadDefinition payloadDefinition) {
        this.payloadDefinition = payloadDefinition;
    }

    public String buildWith(TemplateData templateData) {
        return URLEncodedUtils.format(payloadDefinition.extract(templateData), StandardCharsets.UTF_8.toString());
    }

}