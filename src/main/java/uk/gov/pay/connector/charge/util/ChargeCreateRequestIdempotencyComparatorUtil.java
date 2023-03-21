package uk.gov.pay.connector.charge.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;

import java.util.Map;

public class ChargeCreateRequestIdempotencyComparatorUtil {
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());

    public static boolean equals(ChargeCreateRequest request, Map<String, Object> idempotencyRequestBody) {
        JsonNode requestNode = mapper.valueToTree(request);
        JsonNode idempotencyNode = mapper.valueToTree(idempotencyRequestBody);

        return requestNode.equals(idempotencyNode);
    }
    
    public static Map<String, MapDifference.ValueDifference<Object>> diff(ChargeCreateRequest request, Map<String, Object> idempotencyRequestBody) {
        Map<String, Object> requestMap = mapper.convertValue(request, new TypeReference<>() {});

        MapDifference<String, Object> diff = Maps.difference(requestMap, idempotencyRequestBody);
        return diff.entriesDiffering();
    }
}
