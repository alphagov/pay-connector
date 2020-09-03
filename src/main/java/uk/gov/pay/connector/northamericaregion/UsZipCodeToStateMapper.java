package uk.gov.pay.connector.northamericaregion;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

public class UsZipCodeToStateMapper {

    private final Pattern WELL_FORMED_ZIP_CODE_AND_OPTIONAL_PLUS_FOUR = Pattern.compile("([0-9]{3})[0-9]{2}(?:-[0-9]{4})?");
    private final Pattern WELL_FORMED_STATE_WITH_ZIP_CODE_AND_OPTIONAL_PLUS_FOUR = Pattern.compile("([A-Z]{2})[0-9]{5}(?:-[0-9]{4})?");
    private final Map<String, UsState> US_STATE_ABBREVIATIONS_MAPPING = Arrays.stream(UsState.values()).collect(
            toUnmodifiableMap(NorthAmericaRegion::getAbbreviation, identity())); 

    public Optional<UsState> getState(String normalisedZipCode) {
        var wellFormedZipCodeAndOptionalPlusFourMatcher = WELL_FORMED_STATE_WITH_ZIP_CODE_AND_OPTIONAL_PLUS_FOUR.matcher(normalisedZipCode);
        var wellFormedStateWithZipAndOptionalPlusFourMatcher = WELL_FORMED_ZIP_CODE_AND_OPTIONAL_PLUS_FOUR.matcher(normalisedZipCode);

        if (wellFormedStateWithZipAndOptionalPlusFourMatcher.matches()) {
            String stateAbbreviation = wellFormedStateWithZipAndOptionalPlusFourMatcher.group(1);
            return Optional.ofNullable(UsZipCodeToStateMap.ZIP_CODE_TO_US_STATE_ABBREVIATIONS.get(stateAbbreviation));
        } else if (wellFormedZipCodeAndOptionalPlusFourMatcher.matches()) {
            String firstThreeCharacters = wellFormedZipCodeAndOptionalPlusFourMatcher.group(1);
            return Optional.ofNullable(US_STATE_ABBREVIATIONS_MAPPING.get(firstThreeCharacters));
        }

        return Optional.empty();
    }
}
