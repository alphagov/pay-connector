package uk.gov.pay.connector.northamericaregion;

import java.util.Objects;

public enum CanadaProvinceOrTerritory implements NorthAmericaRegion {
    ALBERTA("AB", "Alberta"),
    BRITISH_COLUMBIA("BC", "British Columbia"),
    MANITOBA("MB", "Manitoba"),
    NEW_BRUNSWICK("NB", "New Brunswick"),
    NEWFOUNDLAND_AND_LABRADOR("NL", "Newfoundland and Labrador"),
    NORTHWEST_TERRITORIES("NT", "Northwest Territories"),
    NOVA_SCOTIA("NS", "Nova Scotia"),
    NUNAVUT("NU", "Nunavut"),
    ONTARIO("ON", "Ontario"),
    PRINCE_EDWARD_ISLAND("PE", "Prince Edward Island"),
    QUEBEC("QC", "Quebec"),
    SASKATCHEWAN("SK", "Saskatchewan"),
    YUKON("YT", "Yukon");

    private final String abbreviation;
    private final String fullName;

    CanadaProvinceOrTerritory(String abbreviation, String provinceOrTerritory) {
        this.abbreviation = Objects.requireNonNull(abbreviation);
        this.fullName = Objects.requireNonNull(provinceOrTerritory);
    }

    @Override
    public String getAbbreviation() {
        return abbreviation;
    }

    @Override
    public String getFullName() {
        return fullName;
    }
}
