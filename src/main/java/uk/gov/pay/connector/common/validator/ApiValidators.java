package uk.gov.pay.connector.common.validator;

import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
                return email == null || email.length() <= MAXIMUM_FIELDS_SIZE.get(EMAIL_KEY);
            }
        },
        AMOUNT(AMOUNT_KEY) {
            @Override
            boolean validate(String amount) {
                try {
                    int amountValue = Integer.parseInt(amount);
                    return amountValue >= MIN_AMOUNT && amountValue <= MAX_AMOUNT;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        },
        DELAYED_CAPTURE(DELAYED_CAPTURE_KEY) {
            @Override
            boolean validate(String delayedCapture) {
                return "true".equalsIgnoreCase(delayedCapture) || "false".equalsIgnoreCase(delayedCapture);
            }
        },
        LANGUAGE(LANGUAGE_KEY) {
            @Override
            boolean validate(String iso639AlphaTwoCode) {
                return Arrays.stream(SupportedLanguage.values())
                        .anyMatch(supportedLanguage -> supportedLanguage.toString().equals(iso639AlphaTwoCode));
            }
        };

        private final String key;

        ChargeParamValidator(String key) {
            this.key = key;
        }

        abstract boolean validate(String candidate);

        private static final Map<String, ChargeParamValidator> VALIDATORS =
                Arrays.stream(values()).collect(Collectors.toMap(v -> v.key, v -> v));

        public static Optional<ChargeParamValidator> fromString(String key) {
            return Optional.ofNullable(VALIDATORS.get(key));
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
        return ChargeParamValidator.fromString(chargePatchRequest.getPath())
                .map(validator -> validator.validate(chargePatchRequest.getValue()))
                .orElse(false);
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
