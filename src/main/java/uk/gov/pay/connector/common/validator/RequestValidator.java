package uk.gov.pay.connector.common.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.math.NumberUtils.isDigits;

public class RequestValidator {

    public List<String> checkIsNumeric(JsonNode payload, String... fieldNames) {
        return applyCheck(payload, isNotNumeric(), fieldNames, "Field [%s] must be a number");
    }

    public List<String> checkIsBoolean(JsonNode payload, String... fieldNames) {
        return applyCheck(payload, isNotBoolean(), fieldNames, "Field [%s] must be a boolean");
    }

    public List<String> checkIsString(JsonNode payload, String... fieldNames) {
        return applyCheck(payload, isNotString(), fieldNames, "Field [%s] must be a string");
    }

    public List<String> checkIfExistsOrEmpty(JsonNode payload, String... fieldNames) {
        return applyCheck(payload, notExistOrEmpty(), fieldNames, "Field [%s] is required");
    }

    public List<String> checkMaxLength(JsonNode payload, int maxLength, String... fieldNames) {
        return applyCheck(payload, exceedsMaxLength(maxLength), fieldNames, "Field [%s] must have a maximum length of " + maxLength + " characters");
    }

    public List<String> checkIsAllowedValue(JsonNode payload, Set<String> allowedValues, String... fieldNames) {
        return applyCheck(payload, isNotAllowedValue(allowedValues), fieldNames, "Field [%s] must be one of "
                + allowedValues.stream().sorted().collect(Collectors.joining(", ", "[", "]")));
    }

    private List<String> applyCheck(JsonNode payload, Function<JsonNode, Boolean> check, String[] fieldNames, String errorMessage) {
        List<String> errors = newArrayList();
        for (String fieldName : fieldNames) {
            if (check.apply(payload.get(fieldName))) {
                errors.add(format(errorMessage, fieldName));
            }
        }
        return errors;
    }

    private Function<JsonNode, Boolean> exceedsMaxLength(int maxLength) {
        return jsonNode -> jsonNode.asText().length() > maxLength;
    }

    private Function<JsonNode, Boolean> notExistOrEmpty() {
        return (jsonElement) -> {
            if (jsonElement instanceof NullNode) {
                return isNullValue().apply(jsonElement);
            } else if (jsonElement instanceof ArrayNode) {
                return notExistOrEmptyArray().apply(jsonElement);
            } else {
                return notExistOrBlankText().apply(jsonElement);
            }
        };
    }

    private Function<JsonNode, Boolean> notExistOrEmptyArray() {
        return jsonElement -> (
                jsonElement == null ||
                        ((jsonElement instanceof ArrayNode) && (jsonElement.size() == 0))
        );
    }

    private static Function<JsonNode, Boolean> notExistOrBlankText() {
        return jsonElement -> (
                jsonElement == null ||
                        isBlank(jsonElement.asText())
        );
    }

    private static Function<JsonNode, Boolean> isNullValue() {
        return jsonElement -> (
                jsonElement == null || jsonElement.isNull()
        );
    }

    private static Function<JsonNode, Boolean> isNotNumeric() {
        return jsonNode -> !isDigits(jsonNode.asText());
    }

    private static Function<JsonNode, Boolean> isNotBoolean() {
        return jsonNode -> jsonNode == null || !jsonNode.isBoolean();
    }

    private static Function<JsonNode, Boolean> isNotAllowedValue(Set<String> allowedValues) {
        return jsonNode -> jsonNode == null || !allowedValues.contains(jsonNode.asText());
    }

    private static Function<JsonNode, Boolean> isNotString() {
        return jsonNode -> !jsonNode.isTextual();
    }

}
