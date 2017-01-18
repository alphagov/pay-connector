package uk.gov.pay.connector.util.templates;

import static uk.gov.pay.connector.service.OrderRequestBuilder.TemplateData;

public interface PayloadBuilder {

    String buildWith(TemplateData templateData);
}
