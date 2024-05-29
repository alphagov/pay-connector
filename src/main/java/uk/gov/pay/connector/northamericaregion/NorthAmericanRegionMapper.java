package uk.gov.pay.connector.northamericaregion;

import uk.gov.pay.connector.common.model.domain.Address;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Optional;

public class NorthAmericanRegionMapper {

    private final UsZipCodeToStateMapper usZipCodeToStateMapper;
    private final CanadaPostalcodeToProvinceOrTerritoryMapper canadaPostalcodeToProvinceOrTerritoryMapper;

    public NorthAmericanRegionMapper() {
        this.usZipCodeToStateMapper = new UsZipCodeToStateMapper();
        this.canadaPostalcodeToProvinceOrTerritoryMapper = new CanadaPostalcodeToProvinceOrTerritoryMapper();
    }

    @Inject
    public NorthAmericanRegionMapper(UsZipCodeToStateMapper usZipCodeToStateMapper, CanadaPostalcodeToProvinceOrTerritoryMapper canadaPostalcodeToProvinceOrTerritoryMapper) {
        this.usZipCodeToStateMapper = usZipCodeToStateMapper;
        this.canadaPostalcodeToProvinceOrTerritoryMapper = canadaPostalcodeToProvinceOrTerritoryMapper;
    }

    public Optional<? extends NorthAmericaRegion> getNorthAmericanRegionForCountry(Address address) {
        if (address.getCountry() == null) {
            return Optional.empty();
        }

        switch (address.getCountry()) {
            case "US":
                return usZipCodeToStateMapper.getState(getNormalisedPostalCode(address));
            case "CA":
                return canadaPostalcodeToProvinceOrTerritoryMapper.getProvinceOrTerritory(getNormalisedPostalCode(address));
            default:
                return Optional.empty();
        }
    }

    private String getNormalisedPostalCode(Address address) {
        return address.getPostcode().replaceAll("\\s", "").toUpperCase(Locale.ENGLISH);
    }
}
