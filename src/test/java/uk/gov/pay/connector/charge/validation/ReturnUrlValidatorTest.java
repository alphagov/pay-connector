package uk.gov.pay.connector.charge.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ReturnUrlValidatorTest {
    
    @ParameterizedTest
    @MethodSource("urlProvider")
    void testUrlValidations(String urls, boolean expectedResult) {
        assertThat(ReturnUrlValidator.isValid(urls), is(expectedResult));
    }

    private static Stream<Arguments> urlProvider() {
        return Stream.of(
                Arguments.of("http://a.valid.url.local", true),
                Arguments.of("http://a.valid.url.local/with/path", true),
                Arguments.of("https://a.valid.url.local", true),
                Arguments.of("https://a.valid.url.local/with/path", true),
                Arguments.of("https://a.valid.url.internal", true),
                Arguments.of("https://a.valid.url.gov.uk", true),
                Arguments.of("not.a.valid.url", false),
                Arguments.of("not.a.valid.url.local", false),
                Arguments.of("1235asdasd", false)
        );
    }
}
