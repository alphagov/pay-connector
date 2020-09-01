package uk.gov.pay.connector.northamericaregion;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.util.Map.entry;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.NEWFOUNDLAND_AND_LABRADOR;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.NOVA_SCOTIA;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.PRINCE_EDWARD_ISLAND;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.NEW_BRUNSWICK;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.QUEBEC;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.ONTARIO;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.SASKATCHEWAN;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.MANITOBA;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.ALBERTA;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.BRITISH_COLUMBIA;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.YUKON;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.NUNAVUT;
import static uk.gov.pay.connector.northamericaregion.CanadaProvinceOrTerritory.NORTHWEST_TERRITORIES;

public class CanadaPostalcodeToProvinceOrTerritoryMapper {

    private static final Pattern WELL_FORMED_POSTAL_CODE = Pattern.compile("([A-Z])[0-9][A-Z][0-9][A-Z][0-9]");
    private static final Pattern WELL_FORMED_X_POSTAL_CODE = Pattern.compile("(X[0-9][A-Z])[0-9][A-Z][0-9]");
    private static final String SANTA_CLAUS_POSTAL_CODE = "H0H0H0";

    public static final Map<String, CanadaProvinceOrTerritory> NON_X_POSTAL_CODE_TERRITORY_PROVINCE_MAP = Map.ofEntries(
            entry("A", NEWFOUNDLAND_AND_LABRADOR),
            entry("B", NOVA_SCOTIA),
            entry("C", PRINCE_EDWARD_ISLAND),
            entry("E", NEW_BRUNSWICK),
            entry("G", QUEBEC),
            entry("H", QUEBEC),
            entry("J", QUEBEC),
            entry("K", ONTARIO),
            entry("L", ONTARIO),
            entry("M", ONTARIO),
            entry("N", ONTARIO),
            entry("P", ONTARIO),
            entry("R", MANITOBA),
            entry("S", SASKATCHEWAN),
            entry("T", ALBERTA),
            entry("V", BRITISH_COLUMBIA),
            entry("Y", YUKON)
    );

    public static final Map<String, CanadaProvinceOrTerritory> X_POSTAL_CODE_TERRITORY_MAP = Map.ofEntries(
            entry("X0A", NUNAVUT),
            entry("X0B", NUNAVUT),
            entry("X0C", NUNAVUT),
            entry("X0E", NORTHWEST_TERRITORIES),
            entry("X0G", NORTHWEST_TERRITORIES)
    );
    
    public static Optional<CanadaProvinceOrTerritory> getProvinceOrTerritory(String normalisedPostalCode) {
        var xPostalCodeMatcher = WELL_FORMED_X_POSTAL_CODE.matcher(normalisedPostalCode);
        var postalCodeMatcher = WELL_FORMED_POSTAL_CODE.matcher(normalisedPostalCode);

        if (SANTA_CLAUS_POSTAL_CODE.equals(normalisedPostalCode)) {
            return Optional.empty();
        }

        if (xPostalCodeMatcher.matches()) {
            String firstThreeCharacters = xPostalCodeMatcher.group(1);
            return Optional.ofNullable(X_POSTAL_CODE_TERRITORY_MAP.get(firstThreeCharacters));
        } else if (postalCodeMatcher.matches()) {
            String firstLetter = postalCodeMatcher.group(1);
            return Optional.ofNullable(NON_X_POSTAL_CODE_TERRITORY_PROVINCE_MAP.get(firstLetter));
        }

        return Optional.empty();
    }
}
