package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.gateway.epdq.EpdqTemplateData;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static uk.gov.pay.connector.gateway.epdq.payload.EpdqParameterBuilder.newParameterBuilder;

public class EpdqPayloadDefinitionForNew3ds2Order extends EpdqPayloadDefinitionForNew3dsOrder {

    public final static String BROWSER_COLOR_DEPTH = "browserColorDepth";
    public final static String BROWSER_LANGUAGE = "browserLanguage";
    public final static String BROWSER_SCREEN_HEIGHT = "browserScreenHeight";
    public final static String DEFAULT_BROWSER_COLOR_DEPTH = "24";
    public final static String DEFAULT_BROWSER_SCREEN_HEIGHT = "480";

    private final static Set<String> VALID_SCREEN_COLOR_DEPTHS = Set.of("1", "2", "4", "8", "15", "16", "24", "32");
    
    private final SupportedLanguage paymentLanguage;

    public EpdqPayloadDefinitionForNew3ds2Order(String frontendUrl, SupportedLanguage paymentLanguage) {
        super(frontendUrl);
        this.paymentLanguage = paymentLanguage;
    }

    @Override
    public List<NameValuePair> extract(EpdqTemplateData templateData) {
        List<NameValuePair> nameValuePairs = super.extract(templateData);
        EpdqParameterBuilder parameterBuilder = newParameterBuilder(nameValuePairs)
                .add(BROWSER_COLOR_DEPTH, getBrowserColorDepth(templateData))
                .add(BROWSER_LANGUAGE, getBrowserLanguage(templateData))
                .add(BROWSER_SCREEN_HEIGHT, getBrowserScreenHeight(templateData));

        return parameterBuilder.build();
    }

    private String getBrowserScreenHeight(EpdqTemplateData templateData) {
        return templateData.getAuthCardDetails().getJsScreenHeight().orElse(DEFAULT_BROWSER_SCREEN_HEIGHT);
    }

    private String getBrowserLanguage(EpdqTemplateData templateData) {
        return templateData
                .getAuthCardDetails()
                .getJsNavigatorLanguage()
                .map(Locale::forLanguageTag)
                .map(Locale::toLanguageTag)
                .orElse(getDefaultBrowserLanguage());
    }

    private String getDefaultBrowserLanguage() {
        if (paymentLanguage == SupportedLanguage.ENGLISH) {
            return "en-GB";
        }
        return paymentLanguage.toString();
    }

    private String getBrowserColorDepth(EpdqTemplateData templateData) {
        return templateData.getAuthCardDetails().getJsScreenColorDepth()
                .filter(VALID_SCREEN_COLOR_DEPTHS::contains).orElse(DEFAULT_BROWSER_COLOR_DEPTH);
    }
}
