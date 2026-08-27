package uk.gov.pay.connector.gateway.adyen;

import uk.gov.pay.connector.gateway.adyen.response.json.AdyenBrowserInfo;
import uk.gov.pay.connector.gateway.model.BrowserDataFor3ds;

import java.util.Locale;
import java.util.Optional;


public class AdyenBrowserInfoFactory {
    
    public static final String DEFAULT_BROWSER_ACCEPT_HEADER = "text/html";
    public static final int DEFAULT_BROWSER_COLOR_DEPTH = 1;
    public static final boolean DEFAULT_BROWSER_JAVA_SCRIPT_ENABLED = false;
    public static final String DEFAULT_BROWSER_LANGUAGE = "en_GB";
    public static final int DEFAULT_BROWSER_SCREEN_HEIGHT = 0;
    public static final int DEFAULT_BROWSER_SCREEN_WIDTH = 0;
    public static final int DEFAULT_BROWSER_TZ = 0;
    public static final String DEFAULT_BROWSER_USER_AGENT = "Mozilla/5.0";


    public AdyenBrowserInfo create(BrowserDataFor3ds browserDataFor3ds) {
        return new AdyenBrowserInfo(
                browserDataFor3ds.getBrowserAcceptHeader().orElse(DEFAULT_BROWSER_ACCEPT_HEADER),
                browserDataFor3ds.getBrowserColorDepth().flatMap(this::parseInteger).map(this::parseColorDepth).orElse(DEFAULT_BROWSER_COLOR_DEPTH),
                false,
                browserDataFor3ds.getBrowserJavaScriptEnabled().orElse(DEFAULT_BROWSER_JAVA_SCRIPT_ENABLED),
                browserDataFor3ds.getBrowserLanguage()
                        .filter(value -> !Locale.forLanguageTag(value).getLanguage().isEmpty())
                        .orElse(DEFAULT_BROWSER_LANGUAGE),
                browserDataFor3ds.getBrowserScreenHeight().flatMap(this::parseInteger).filter(value -> value >= 0).orElse(DEFAULT_BROWSER_SCREEN_HEIGHT),
                browserDataFor3ds.getBrowserScreenWidth().flatMap(this::parseInteger).filter(value -> value >= 0).orElse(DEFAULT_BROWSER_SCREEN_WIDTH),
                browserDataFor3ds.getBrowserTZ().flatMap(this::parseInteger).filter(value -> value >= -1440 && value <= 1440).orElse(DEFAULT_BROWSER_TZ),
                browserDataFor3ds.getBrowserUserAgent().orElse(DEFAULT_BROWSER_USER_AGENT)
        );
    }

    private Optional<Integer> parseInteger(String value) {
        try {
            return Optional.of(Integer.valueOf(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private int parseColorDepth(Integer colorDepth) {
        return switch (colorDepth) {
            case 1, 4, 8, 15, 16, 24, 32, 48 -> colorDepth;
            case Integer i when i > 1 && i < 4 -> 1;
            case Integer i when i > 4 && i < 8 -> 4;
            case Integer i when i > 8 && i < 15 -> 8;
            case Integer i when i > 16 && i < 24 -> 16;
            case Integer i when i > 24 && i < 32 -> 24;
            case Integer i when i > 32 && i < 48 -> 32;
            case Integer i when i > 48 -> 48;
            default -> 1;
        };
    }
}
