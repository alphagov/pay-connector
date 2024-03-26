package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.primitives.Chars.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.util.RandomIdGenerator.newId;
import static uk.gov.pay.connector.util.RandomIdGenerator.idFromExternalId;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

class RandomIdGeneratorTest {

    private static final List<Character> BASE32_DICTIONARY = asList("0123456789abcdefghijklmnopqrstuv".toCharArray());

    @Test
    void shouldGenerateUniqueRandomIds() {
        Set<String> randomIds = IntStream.range(0, 100)
            .parallel()
            .mapToObj(value -> newId())
            .collect(Collectors.toSet());

        assertEquals(100, randomIds.size());
    }

    @Test
    void shouldGenerateRandomIdsInBase32() {
        IntStream.range(0, 100)
            .parallel()
            .mapToObj(value -> newId())
            .map(id -> asList(id.toCharArray()))
            .forEach(idArray -> assertTrue(BASE32_DICTIONARY.containsAll(idArray)));
    }

    @Test
    void shouldGenerateIdsOf26CharsInLength() {
        IntStream.range(0, 100)
            .parallel()
            .mapToObj(value -> newId())
            .forEach(id -> assertEquals(26, id.length()));
    }

    @Test
    void randomUuid_shouldGenerateIdsOf32CharsInLength() {
        String randomUuid = randomUuid();
        assertEquals(32, randomUuid.length());
    }

    @Test
    void idFromExternalEntityId_shouldGenerate26Characters() {
        for (int i = 0; i < 100; i++) {
            String randomUuid = randomUuid();
            String randomId = idFromExternalId(randomUuid);
            assertEquals(26, randomId.length());
            assertFalse(randomId.contains("-"));
        }
    }

    @Test
    void idFromExternalEntityId_shouldGenerateId() {
        String externalId = "123b456c-789d012e-345f678g";
        String randomId = idFromExternalId(externalId);

        assertThat(randomId, is("102b63b370fd35418ad66b0100"));
    }

    @Test
    void random13ByteHexGenerator_shouldGenerate13BytesHexId() {
        RandomIdGenerator randomIdGenerator = new RandomIdGenerator();
        String correctionPaymentId = randomIdGenerator.random13ByteHexGenerator();
        assertEquals(26, correctionPaymentId.length(), "The length of the correction payment ID should be 26 characters");
        assertTrue(correctionPaymentId.matches("[0-9a-f]+"), "The correction payment ID should contain only hexadecimal characters");
    }
}
