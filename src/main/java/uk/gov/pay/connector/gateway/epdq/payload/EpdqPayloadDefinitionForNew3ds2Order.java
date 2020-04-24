package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder;

import java.util.List;
import java.util.Set;

public class EpdqPayloadDefinitionForNew3ds2Order extends EpdqPayloadDefinitionForNew3dsOrder {

    public final static String BROWSER_COLOR_DEPTH = "browserColorDepth";
    
    private final static String DEFAULT_BROWSER_COLOR_DEPTH = "24";
    private final static Set<String> VALID_SCREEN_COLOR_DEPTHS = Set.of("1", "2", "4", "8", "15", "16", "24", "32");

    @Override
    public List<NameValuePair> extract(EpdqOrderRequestBuilder.EpdqTemplateData templateData) {
        List<NameValuePair> nameValuePairs = super.extract(templateData);
        return newParameterBuilder(nameValuePairs)
                .add(BROWSER_COLOR_DEPTH, getBrowserColorDepth(templateData))
                .build();
    }

    private String getBrowserColorDepth(EpdqOrderRequestBuilder.EpdqTemplateData templateData) {
        return templateData.getAuthCardDetails().getJsScreenColorDepth()
                .filter(VALID_SCREEN_COLOR_DEPTHS::contains).orElse(DEFAULT_BROWSER_COLOR_DEPTH);
    }
}
