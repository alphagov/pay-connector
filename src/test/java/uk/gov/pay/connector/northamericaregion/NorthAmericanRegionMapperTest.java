package uk.gov.pay.connector.northamericaregion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.common.model.domain.Address;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class NorthAmericanRegionMapperTest {
    
    private final NorthAmericanRegionMapper mapper = new NorthAmericanRegionMapper(
            new UsZipCodeToStateMapper(),
            new CanadaPostalcodeToProvinceOrTerritoryMapper()
    );
    
    private Address address;
    
    @BeforeEach
    public void setUp() {
        address = new Address();
        address.setLine1("Line 1");
        address.setLine2("Line 2");
    }

    @Test
    public void shouldNotReturnRegionForNullCountry() {
        address.setCountry(null);
        address.setCity("Nowhere");
        address.setPostcode("20500");

        Optional<? extends NorthAmericaRegion> northAmericaRegion = mapper.getNorthAmericanRegionForCountry(address);

        assertThat(northAmericaRegion.isEmpty(), is (true));
    }

    @Test
    public void shouldNotReturnRegionForCountryOutsideNorthAmerica() {
        address.setCountry("GB");
        address.setCity("London");
        address.setPostcode("CR91AT");
        
        Optional<? extends NorthAmericaRegion> northAmericaRegion = mapper.getNorthAmericanRegionForCountry(address);

        assertThat(northAmericaRegion.isEmpty(), is (true));
    }

    @Test
    public void shouldReturnTheCorrectStateForValidAddressInUnitedStates() {
        address.setCountry("US");
        address.setCity("Washington D.C.");
        address.setPostcode("20500");

        Optional<? extends NorthAmericaRegion> northAmericaRegion = mapper.getNorthAmericanRegionForCountry(address);

        assertThat(northAmericaRegion.isPresent(), is (true));
        assertThat(northAmericaRegion.get(), is (UsState.WASHINGTON_DC));
    }

    @Test
    public void shouldReturnTheCorrectProvinceOrTerritoryForValidAddressInCanada() {
        address.setCountry("CA");
        address.setCity("Arctic Region");
        address.setPostcode("X0A0A0");

        Optional<? extends NorthAmericaRegion> northAmericaRegion = mapper.getNorthAmericanRegionForCountry(address);

        assertThat(northAmericaRegion.isPresent(), is (true));
        assertThat(northAmericaRegion.get(), is (CanadaProvinceOrTerritory.NUNAVUT));
    }

}
