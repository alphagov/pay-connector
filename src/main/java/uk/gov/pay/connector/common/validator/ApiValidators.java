package uk.gov.pay.connector.common.validator;

import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.service.payments.commons.model.SupportedLanguage;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static fj.data.Either.left;
import static fj.data.Either.right;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.AMOUNT_KEY;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.DELAYED_CAPTURE_KEY;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.EMAIL_KEY;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.LANGUAGE_KEY;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.MAXIMUM_FIELDS_SIZE;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.MAX_AMOUNT;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.MIN_AMOUNT;

public class ApiValidators {

    private enum ChargeParamValidator {
        EMAIL(EMAIL_KEY) {
            @Override
            boolean validate(String email) {
                return email.length() <= MAXIMUM_FIELDS_SIZE.get(EMAIL_KEY);
            }
        },

        AMOUNT(AMOUNT_KEY) {
            @Override
            boolean validate(String amount) {
                int amountValue;
                try {
                    amountValue  = Integer.parseInt(amount);
                } catch (NumberFormatException e) {
                    return false;
                }
                return MIN_AMOUNT <= amountValue && MAX_AMOUNT >= amountValue;
            }
        },
        
        DELAYED_CAPTURE(DELAYED_CAPTURE_KEY) {
            @Override
            boolean validate(String delayedCapture) {
                return Boolean.TRUE.toString().equals(delayedCapture) || Boolean.FALSE.toString().equals(delayedCapture);
            }
        },

        LANGUAGE(LANGUAGE_KEY) {
            @Override
            boolean validate(String iso639AlphaTwoCode) {
                return Arrays.stream(SupportedLanguage.values())
                        .anyMatch(supportedLanguage -> supportedLanguage.toString().equals(iso639AlphaTwoCode));
            }
        };

        private final String type;

        ChargeParamValidator(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return type;
        }

        abstract boolean validate(String candidate);

        private static final Map<String, ChargeParamValidator> stringToEnum = new HashMap<>();

        static {
            for (ChargeParamValidator val : values()) {
                stringToEnum.put(val.toString(), val);
            }
        }

        public static Optional<ChargeParamValidator> fromString(String type) {
            return Optional.ofNullable(stringToEnum.get(type));
        }
    }

    public static Optional<List<String>> validateChargeParams(Map<String, String> inputData) {
        List<String> invalid = inputData.entrySet().stream()
                .filter(entry -> ChargeParamValidator.fromString(entry.getKey())
                        .map(validator -> !validator.validate(entry.getValue()))
                        .orElse(false))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return invalid.isEmpty()
                ? Optional.empty()
                : Optional.of(invalid);
    }

    public static boolean validateChargePatchParams(PatchRequestBuilder.PatchRequest chargePatchRequest) {
        boolean invalid = ChargeParamValidator.fromString(chargePatchRequest.getPath())
                .map(validator -> !validator.validate(chargePatchRequest.getValue()))
                .orElse(false);

        return !invalid;
    }

    public static Either<List<String>, Pair<ZonedDateTime, ZonedDateTime>> validateFromDateIsBeforeToDate(
            String fromDateParamName, String fromDate, String toDateParamName, String toDate) {
        List<String> errors = newArrayList();

        Optional<ZonedDateTime> fromOptional = parseZonedDateTime(fromDate);
        if (fromOptional.isEmpty()) {
            errors.add("query param '" + fromDateParamName + "' not in correct format");
        }

        Optional<ZonedDateTime> toOptional = parseZonedDateTime(toDate);
        if (toOptional.isEmpty()) {
            errors.add("query param '" + toDateParamName + "' not in correct format");
        }

        if (fromOptional.isPresent() && toOptional.isPresent()) {
            ZonedDateTime from = fromOptional.get();
            ZonedDateTime to = toOptional.get();
            if (to.isBefore(from)) {
                errors.add("query param '" + toDateParamName + "' must be later than '" + fromDateParamName + "'");
            } else {
                return right(Pair.of(from, to));
            }
        }

        return left(errors);
    }

    public static Optional<ZonedDateTime> parseZonedDateTime(String zdt) {
        if (zdt == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZonedDateTime.parse(zdt));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
