package uk.gov.pay.connector.charge.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;

import java.util.Map;

public class ChargeCreateRequestIdempotencyComparatorUtil {
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());
    public static boolean compare(ChargeCreateRequest request, Map<String, Object> idempotencyRequestBody) {
        JsonNode requestNode = mapper.valueToTree(request);
        JsonNode idempotencyNode = mapper.valueToTree(idempotencyRequestBody);

        return requestNode.equals(idempotencyNode);
    }
}
