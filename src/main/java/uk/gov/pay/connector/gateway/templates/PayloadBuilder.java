package uk.gov.pay.connector.gateway.templates;

import static uk.gov.pay.connector.service.OrderRequestBuilder.TemplateData;

public interface PayloadBuilder {

    String buildWith(TemplateData templateData);
}
