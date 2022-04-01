package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AcceptLanguageHeaderParserTest {

    @Test
    void convertsLocale() {
        var acceptLanguageHeaderParser = new AcceptLanguageHeaderParser();
        assertThat(acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader("en-GB,en-US;q=0.9,en;q=0.8"), is("en-GB"));
        assertThat(acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader("fr;q=0.9, fr-CH;q=1.0, en;q=0.8, de;q=0.7, *;q=0.5"), is("fr-CH"));
        assertThat(acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader("en-gb,en-us;q=0.9,en;q=0.8"), is("en-GB"));
        assertThat(acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader("*"), is("en-GB"));
        assertThat(acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader(null), is("en-GB"));
        assertThat(acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader("i like kittens;q=a million"), is("en-GB"));
    }

}
