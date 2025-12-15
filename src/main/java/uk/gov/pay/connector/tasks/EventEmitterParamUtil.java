package uk.gov.pay.connector.tasks;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.pay.connector.tasks.RecordType.fromString;

public class EventEmitterParamUtil {

    public static Optional<ZonedDateTime> getDateParam(Map<String, List<String>> parameters, String paramName) {
        String value = getParameterValue(parameters, paramName);
        if (isBlank(value)) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(ZonedDateTime.parse(value));
            } catch (DateTimeParseException exception) {
                return Optional.empty();
            }
        }
    }

    public static OptionalLong getOptionalLongParam(Map<String, List<String>> parameters, String paramName) {
        String value = getParameterValue(parameters, paramName);
        if (isBlank(value)) {
            return OptionalLong.empty();
        } else {
            return OptionalLong.of(Long.parseLong(value));
        }
    }

    public static Optional<Long> getLongParam(Map<String, List<String>> parameters, String paramName) {
        String value = getParameterValue(parameters, paramName);
        if (isBlank(value)) {
            return Optional.empty();
        } else {
            return Optional.of(Long.parseLong(value));
        }
    }

    public static Optional<String> getStringParam(Map<String, List<String>> parameters, String paramName) {
        return Optional.ofNullable(getParameterValue(parameters, paramName));
    }

    public static String getParameterValue(Map<String, List<String>> parameters, String paramName) {
        return (parameters.get(paramName) == null ||
                parameters.get(paramName).isEmpty()) ?
                null : parameters.get(paramName).getFirst();
    }

    public static Optional<RecordType> getRecordType(Map<String, List<String>> parameters) {
        String recordType = getParameterValue(parameters, "record_type");

        if (isEmpty(recordType)) {
            return Optional.empty();
        }

        return Optional.of(fromString(recordType));
    }
}
