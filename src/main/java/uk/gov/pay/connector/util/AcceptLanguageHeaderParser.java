package uk.gov.pay.connector.util;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static java.util.function.Predicate.not;

public class AcceptLanguageHeaderParser {

    private final static String DEFAULT_LOCALE = "en-GB";

    public String getPreferredLanguageFromAcceptLanguageHeader(String acceptLanguageHeader) {
        return parseLanguageRanges(acceptLanguageHeader)
                .stream()
                .map(Locale.LanguageRange::getRange)
                .filter(not(languageRange -> languageRange.equals("*")))
                .map(Locale::forLanguageTag)
                .map(Locale::toLanguageTag)
                .findFirst()
                .orElse(DEFAULT_LOCALE);
    }

    private static List<Locale.LanguageRange> parseLanguageRanges(String acceptLanguageHeader) {
        if (acceptLanguageHeader == null) {
            return Collections.emptyList();
        }
        try {
            return Locale.LanguageRange.parse(acceptLanguageHeader);
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }
    }
}
