package uk.gov.pay.connector.resources;

import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.builder.PatchRequestBuilder;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.resources.ChargesApiResource.AMOUNT_KEY;
import static uk.gov.pay.connector.resources.ChargesApiResource.EMAIL_KEY;
import static uk.gov.pay.connector.resources.ChargesApiResource.LANGUAGE_KEY;
import static uk.gov.pay.connector.resources.ChargesApiResource.MAXIMUM_FIELDS_SIZE;
import static uk.gov.pay.connector.resources.ChargesApiResource.MAX_AMOUNT;
import static uk.gov.pay.connector.resources.ChargesApiResource.MIN_AMOUNT;

class ApiValidators {

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
                Integer amountValue = Integer.valueOf(amount);
                return MIN_AMOUNT <= amountValue && MAX_AMOUNT >= amountValue;
            }
        },

        LANGUAGE(LANGUAGE_KEY) {
            @Override
            boolean validate(String iso639AlphaTwoCode) {
                return "en".equals(iso639AlphaTwoCode) || "cy".equals(iso639AlphaTwoCode);
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

    static Optional<List> validateQueryParams(List<Pair<String, String>> dateParams, List<Pair<String, Long>> nonNegativePairMap) {
        Map<String, String> invalidQueryParams = new HashMap<>();

        dateParams.forEach(param -> {
            String dateString = param.getRight();
            if (isNotBlank(dateString) && !parseZonedDateTime(dateString).isPresent()) {
                invalidQueryParams.put(param.getLeft(), "query param '%s' not in correct format");
            }
        });

        nonNegativePairMap.forEach(param -> {
            if (param.getRight() != null && param.getRight() < 1) {
                invalidQueryParams.put(param.getLeft(), "query param '%s' should be a non zero positive integer");
            }
        });

        if (!invalidQueryParams.isEmpty()) {
            List<String> invalidResponse = newArrayList();
            invalidResponse.addAll(invalidQueryParams.keySet()
                    .stream()
                    .map(param -> String.format(invalidQueryParams.get(param), param))
                    .collect(Collectors.toList()));
            return Optional.of(invalidResponse);
        }
        return Optional.empty();
    }

    static Either<String, Boolean> validateGatewayAccountReference(GatewayAccountDao gatewayAccountDao, Long gatewayAccountId) {
        if (!gatewayAccountDao.findById(gatewayAccountId).isPresent()) {
            return left(format("account with id %s not found", gatewayAccountId));
        }
        return right(true);
    }

    static Optional<List<String>> validateChargeParams(Map<String, String> inputData) {
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

    static boolean validateChargePatchParams(PatchRequestBuilder.PatchRequest chargePatchRequest) {
        boolean invalid = ChargeParamValidator.fromString(chargePatchRequest.getPath())
                .map(validator -> !validator.validate(chargePatchRequest.getValue()))
                .orElse(false);

        return !invalid;
    }

    static Either<List<String>, Pair<ZonedDateTime, ZonedDateTime>> validateFromDateIsBeforeToDate(
            String fromDateParamName, String fromDate, String toDateParamName, String toDate) {
        List<String> errors = newArrayList();

        Optional<ZonedDateTime> fromOptional = parseZonedDateTime(fromDate);
        if (!fromOptional.isPresent()) {
            errors.add("query param '" + fromDateParamName + "' not in correct format");
        }

        Optional<ZonedDateTime> toOptional = parseZonedDateTime(toDate);
        if (!toOptional.isPresent()) {
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

    static Optional<ZonedDateTime> parseZonedDateTime(String zdt) {
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
