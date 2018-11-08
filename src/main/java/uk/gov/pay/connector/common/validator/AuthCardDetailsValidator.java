package uk.gov.pay.connector.common.validator;

import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class AuthCardDetailsValidator {

    private static final Pattern TWELVE_TO_NINETEEN_DIGITS = compile("[0-9]{12,19}");
    private static final Pattern THREE_TO_FOUR_DIGITS = compile("[0-9]{3,4}");
    private static final Pattern THREE_TO_FOUR_DIGITS_POSSIBLY_SURROUNDED_BY_WHITESPACE = compile("\\s*[0-9]{3,4}\\s*");
    private static final Pattern EXPIRY_DATE = compile("[0-9]{2}/[0-9]{2}");
    private static final Pattern CONTAINS_MORE_THAN_11_NOT_NECESSARILY_CONTIGUOUS_DIGITS = compile(".*([0-9].*){12,}");

    public static boolean isWellFormatted(AuthCardDetails authCardDetails) {
        return isValidCardNumberLength(authCardDetails.getCardNo()) &&
                isBetween3To4Digits(authCardDetails.getCvc()) &&
                hasExpiryDateFormat(authCardDetails.getEndDate()) &&
                isValidAddress(authCardDetails.getAddress()) &&
                hasCardBrand(authCardDetails.getCardBrand()) &&
                unlikelyToBeCvc(authCardDetails.getCardHolder()) &&
                unlikelyToContainACardNumber(authCardDetails.getCardHolder()) &&
                unlikelyToContainACardNumber(authCardDetails.getCardBrand()) &&
                addressUnlikelyToContainACardNumber(authCardDetails.getAddress());
    }

    private static boolean isValidAddress(Address address) {
        return address == null || isAddressComplete(address);
    }

    private static boolean isAddressComplete(Address address) {
        return isNotBlank(address.getCity()) &&
                isNotBlank(address.getLine1()) &&
                isNotBlank(address.getPostcode()) &&
                isNotBlank(address.getCountry());
    }

    private static boolean hasCardBrand(String cardBrand) {
        return isNoneBlank(cardBrand);
    }

    private static boolean isValidCardNumberLength(String number) {
        return notNullAndMatches(TWELVE_TO_NINETEEN_DIGITS, number);
    }

    private static boolean isBetween3To4Digits(String number) {
        return notNullAndMatches(THREE_TO_FOUR_DIGITS, number);
    }

    private static boolean hasExpiryDateFormat(String date) {
        return notNullAndMatches(EXPIRY_DATE, date);
    }
    
    private static boolean addressUnlikelyToContainACardNumber(Address address) {
        return address == null ||
                (unlikelyToContainACardNumber(address.getLine1()) &&
                        unlikelyToContainACardNumber(address.getLine2()) &&
                        unlikelyToContainACardNumber(address.getCity()) &&
                        unlikelyToContainACardNumber(address.getCounty()) &&
                        unlikelyToContainACardNumber(address.getPostcode()) &&
                        unlikelyToContainACardNumber(address.getCountry()));
    }
    
    private static boolean unlikelyToContainACardNumber(String field) {
        return nullOrDoesNotMatch(CONTAINS_MORE_THAN_11_NOT_NECESSARILY_CONTIGUOUS_DIGITS, field);
    }

    private static boolean unlikelyToBeCvc(String field) {
        return nullOrDoesNotMatch(THREE_TO_FOUR_DIGITS_POSSIBLY_SURROUNDED_BY_WHITESPACE, field);
    }

    private static boolean notNullAndMatches(Pattern regex, String string) {
        return string != null && regex.matcher(string).matches();
    }

    private static boolean nullOrDoesNotMatch(Pattern regex, String string) {
        return string == null || !regex.matcher(string).matches();
    }

}
