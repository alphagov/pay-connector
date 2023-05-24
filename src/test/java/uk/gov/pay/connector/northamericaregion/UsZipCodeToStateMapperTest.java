package uk.gov.pay.connector.northamericaregion;


import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


class UsZipCodeToStateMapperTest {

    final UsZipCodeToStateMapper mapper = new UsZipCodeToStateMapper();

    @Test
    void shouldReturnTheCorrectStateForValidZipCode() {
        Optional<UsState> usState = mapper.getState("05910");

        assertThat(usState.isPresent(), is (true));
        assertThat(usState.get(), is (UsState.VERMONT));
    }

    @Test
    void shouldReturnTheCorrectStateForValidZipCodePlusFour() {
        Optional<UsState> usState = mapper.getState("05910-1234");

        assertThat(usState.isPresent(), is (true));
        assertThat(usState.get(), is (UsState.VERMONT));
    }

    @Test
    void shouldReturnTheCorrectStateForValidStateAndZipCode() {
        Optional<UsState> usState = mapper.getState("VT05910");

        assertThat(usState.isPresent(), is (true));
        assertThat(usState.get(), is (UsState.VERMONT));
    }

    @Test
    void shouldReturnTheCorrectStateForValidStateAndZipCodeFourPlus() {
        Optional<UsState> usState = mapper.getState("VT05910-1234");

        assertThat(usState.isPresent(), is (true));
        assertThat(usState.get(), is (UsState.VERMONT));
    }

    @Test
    void shouldNotReturnStateForInvalidStateAndZipCodeFormat() {
        Optional<UsState> usState = mapper.getState("XX05910");
        assertThat(usState.isEmpty(), is (true));
    }

    @Test
    void shouldNotReturnStateForInvalidStateAndZipCodePlusFourFormat() {
        Optional<UsState> usState = mapper.getState("XX05910-1234");
        assertThat(usState.isEmpty(), is (true));
    }

    @Test
    void shouldNotReturnStateForZipCodeNotInUse() {
        Optional<UsState> usState = mapper.getState("00000");
        assertThat(usState.isEmpty(), is (true));
    }

    @Test
    void shouldNotReturnStateForInvalidZipCodeFormat() {
        Optional<UsState> usState = mapper.getState("xxxxx");
        assertThat(usState.isEmpty(), is (true));
    }
}
