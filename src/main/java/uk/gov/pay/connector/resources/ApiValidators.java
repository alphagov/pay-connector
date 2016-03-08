package uk.gov.pay.connector.resources;

import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.util.List;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.*;
import static org.apache.commons.lang3.math.NumberUtils.isNumber;

public class ApiValidators {

    public static Optional<String> validateDateQueryParams(List<Pair<String, String>> queryParams) {
        List<String> invalidQueryParams = newArrayList();

        queryParams.stream()
                .forEach(param -> {
                    String dateString = param.getRight();
                    if (isNotBlank(dateString) && !DateTimeUtils.toUTCZonedDateTime(dateString).isPresent()) {
                        invalidQueryParams.add(param.getLeft());
                    }
                });

        if (invalidQueryParams.size() > 0) {
            return Optional.of(format("query parameters [%s] not in correct format",
                    join(invalidQueryParams, ", ")));
        }
        return Optional.empty();
    }

    public static Either<String, Boolean> validateGatewayAccountReference(GatewayAccountDao gatewayAccountDao, String gatewayAccountId) {
        if (isBlank(gatewayAccountId)) {
            return left("missing gateway account reference");
        } else if (!isNumber(gatewayAccountId)) {
            return left(format("invalid gateway account reference %s", gatewayAccountId));
        } else if (!gatewayAccountDao.findById(Long.valueOf(gatewayAccountId)).isPresent()) {
            return left(format("account with id %s not found", gatewayAccountId));
        }
        return right(true);
    }
}
