package uk.gov.pay.connector.northamericaregion;

import uk.gov.pay.connector.common.model.domain.Address;

import jakarta.inject.Inject;
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
                return getNormalisedPostalCode(address).flatMap(usZipCodeToStateMapper::getState);
            case "CA":
                return getNormalisedPostalCode(address).flatMap(canadaPostalcodeToProvinceOrTerritoryMapper::getProvinceOrTerritory);
            default:
                return Optional.empty();
        }
    }

    private Optional<String> getNormalisedPostalCode(Address address) {
        if (address.getPostcode() == null) {
            return Optional.empty();
        }

        return Optional.of(address.getPostcode().replaceAll("\\s", "").toUpperCase(Locale.ENGLISH));
    }

}
