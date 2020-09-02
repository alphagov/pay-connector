package uk.gov.pay.connector.northamericaregion;

import org.junit.Test;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CanadaPostalcodeToProvinceOrTerritoryMapperTest {

    public final CanadaPostalcodeToProvinceOrTerritoryMapper mapper = new CanadaPostalcodeToProvinceOrTerritoryMapper();

    @Test
    public void shouldReturnTheCorrectStateForNonXPostalCode() {
        Optional<CanadaProvinceOrTerritory> canadaProvinceTerritory = mapper.getProvinceOrTerritory("A1A1A1");

        assertThat(canadaProvinceTerritory.isPresent(), is (true));
        assertThat(canadaProvinceTerritory.get(), is (CanadaProvinceOrTerritory.NEWFOUNDLAND_AND_LABRADOR));
    }

    @Test
    public void shouldReturnTheCorrectStateForNunavutPostalCode() {
        Optional<CanadaProvinceOrTerritory> canadaProvinceTerritory = mapper.getProvinceOrTerritory("X0A0A0");

        assertThat(canadaProvinceTerritory.isPresent(), is (true));
        assertThat(canadaProvinceTerritory.get(), is (CanadaProvinceOrTerritory.NUNAVUT));
    }

    @Test
    public void shouldReturnTheCorrectStateForNorthwestTerritoriesPostalCode() {
        Optional<CanadaProvinceOrTerritory> canadaProvinceTerritory = mapper.getProvinceOrTerritory("X0E1Z0");

        assertThat(canadaProvinceTerritory.isPresent(), is (true));
        assertThat(canadaProvinceTerritory.get(), is (CanadaProvinceOrTerritory.NORTHWEST_TERRITORIES));
    }
    
    @Test
    public void shouldNotReturnAStateForSantaPostalCode() {
        Optional<CanadaProvinceOrTerritory> canadaProvinceTerritory = mapper.getProvinceOrTerritory("H0H0H0");

        assertThat(canadaProvinceTerritory.isEmpty(), is (true));
    }
}
