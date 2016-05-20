package uk.gov.pay.connector.resources;

import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.util.DateTimeUtils;

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

public class ApiValidators {

    public static Optional<List> validateQueryParams(List<Pair<String, String>> dateParams, List<Pair<String, Long>> nonNegativePairMap) {
        Map<String, String> invalidQueryParams = new HashMap<>();

        dateParams.stream()
                .forEach(param -> {
                    String dateString = param.getRight();
                    if (isNotBlank(dateString) && !DateTimeUtils.toUTCZonedDateTime(dateString).isPresent()) {
                        invalidQueryParams.put(param.getLeft(), "query param '%s' not in correct format");
                    }
                });

        nonNegativePairMap.stream()
                .forEach(param -> {
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

    public static Either<String, Boolean> validateGatewayAccountReference(GatewayAccountDao gatewayAccountDao, Long gatewayAccountId) {
        if (!gatewayAccountDao.findById(gatewayAccountId).isPresent()) {
            return left(format("account with id %s not found", gatewayAccountId));
        }
        return right(true);
    }
}
