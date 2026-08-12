package uk.gov.pay.connector.gateway.adyen;

import org.jspecify.annotations.NullMarked;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenBrowserInfo;
import uk.gov.pay.connector.gateway.model.BrowserDataFor3ds;

import java.util.Locale;
import java.util.Optional;

@NullMarked
public class AdyenBrowserInfoFactory {

    public AdyenBrowserInfo create(BrowserDataFor3ds browserDataFor3ds) {
        return new AdyenBrowserInfo(
                browserDataFor3ds.getBrowserAcceptHeader().orElse("text/html"),
                browserDataFor3ds.getBrowserColorDepth().flatMap(this::parseInteger).map(this::parseColorDepth).orElse(1),
                false,
                browserDataFor3ds.getBrowserJavaScriptEnabled().orElse(false),
                browserDataFor3ds.getBrowserLanguage()
                        .filter(value -> !Locale.forLanguageTag(value).getLanguage().isEmpty())
                        .orElse("en-GB"),
                browserDataFor3ds.getBrowserScreenHeight().flatMap(this::parseInteger).filter(value -> value >= 0).orElse(0),
                browserDataFor3ds.getBrowserScreenWidth().flatMap(this::parseInteger).filter(value -> value >= 0).orElse(0),
                browserDataFor3ds.getBrowserTZ().flatMap(this::parseInteger).filter(value -> value >= -1440 && value <= 1440).orElse(0),
                browserDataFor3ds.getBrowserUserAgent().orElse("Mozilla/5.0")
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
