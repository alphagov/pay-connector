package uk.gov.pay.connector.gateway.templates;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.service.OrderRequestBuilder;

public interface PayloadDefinition<T extends OrderRequestBuilder.TemplateData> {

    ImmutableList<NameValuePair> extract(T templateData);

}
