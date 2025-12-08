package uk.gov.pay.connector.northamericaregion;


import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class CanadaPostalcodeToProvinceOrTerritoryMapperTest {

    final CanadaPostalcodeToProvinceOrTerritoryMapper mapper = new CanadaPostalcodeToProvinceOrTerritoryMapper();

    @Test
    void shouldReturnTheCorrectStateForNonXPostalCode() {
        Optional<CanadaProvinceOrTerritory> canadaProvinceTerritory = mapper.getProvinceOrTerritory("A1A1A1");

        assertThat(canadaProvinceTerritory.isPresent(), is(true));
        assertThat(canadaProvinceTerritory.get(), is(CanadaProvinceOrTerritory.NEWFOUNDLAND_AND_LABRADOR));
    }

    @Test
    void shouldReturnTheCorrectStateForNunavutPostalCode() {
        Optional<CanadaProvinceOrTerritory> canadaProvinceTerritory = mapper.getProvinceOrTerritory("X0A0A0");

        assertThat(canadaProvinceTerritory.isPresent(), is(true));
        assertThat(canadaProvinceTerritory.get(), is(CanadaProvinceOrTerritory.NUNAVUT));
    }

    @Test
    void shouldReturnTheCorrectStateForNorthwestTerritoriesPostalCode() {
        Optional<CanadaProvinceOrTerritory> canadaProvinceTerritory = mapper.getProvinceOrTerritory("X0E1Z0");

        assertThat(canadaProvinceTerritory.isPresent(), is(true));
        assertThat(canadaProvinceTerritory.get(), is(CanadaProvinceOrTerritory.NORTHWEST_TERRITORIES));
    }

    @Test
    void shouldNotReturnAStateForSantaPostalCode() {
        Optional<CanadaProvinceOrTerritory> canadaProvinceTerritory = mapper.getProvinceOrTerritory("H0H0H0");

        assertThat(canadaProvinceTerritory.isEmpty(), is(true));
    }
}
