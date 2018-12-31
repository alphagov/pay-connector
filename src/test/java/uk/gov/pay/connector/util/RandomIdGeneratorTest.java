package uk.gov.pay.connector.util;

import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.primitives.Chars.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.util.RandomIdGenerator.newId;

public class RandomIdGeneratorTest {

    private static final List<Character> BASE32_DICTIONARY = asList("0123456789abcdefghijklmnopqrstuv".toCharArray());

    @Test
    public void shouldGenerateUniqueRandomIds() {
        Set<String> randomIds = IntStream.range(0, 100)
            .parallel()
            .mapToObj(value -> newId())
            .collect(Collectors.toSet());

        assertEquals(100, randomIds.size());
    }

    @Test
    public void shouldGenerateRandomIdsInBase32() {
        IntStream.range(0, 100)
            .parallel()
            .mapToObj(value -> newId())
            .map(id -> asList(id.toCharArray()))
            .forEach(idArray -> assertTrue(BASE32_DICTIONARY.containsAll(idArray)));
    }

    @Test
    public void shouldGenerateIdsOf26CharsInLength() {
        IntStream.range(0, 100)
            .parallel()
            .mapToObj(value -> newId())
            .forEach(id -> assertEquals(26, id.length()));
    }
}
