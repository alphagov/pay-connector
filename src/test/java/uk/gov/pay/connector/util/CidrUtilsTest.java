package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CidrUtilsTest {
    @Test
    void shouldReturnAnImmutableIpAddressesSet() {
        Set<String> expectedIpAddresses = Set.of("1.1.1.0", "1.1.1.1", "16.20.108.12");

        Set<String> ipAddresses = CidrUtils.getIpAddresses(Set.of("1.1.1.0/31", "16.20.108.12/32"));

        assertThat(ipAddresses, is(expectedIpAddresses));
        assertThrows(UnsupportedOperationException.class, () -> ipAddresses.add("9.9.9.9"));
    }

    @Test
    void shouldReturnAnEmptyImmutableIpAddressesSetAfterAcceptingAnEmptyList() {
        Set<String> ipAddresses = CidrUtils.getIpAddresses(Collections.EMPTY_LIST);

        assertThat(ipAddresses, is(Collections.EMPTY_SET));
    }

    @Test
    void shouldThrowNullPointerExceptionAfterAcceptingNullValue() {
        assertThrows(NullPointerException.class, () -> CidrUtils.getIpAddresses(null));
    }
}
