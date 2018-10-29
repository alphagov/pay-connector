package uk.gov.pay.connector.gatewayaccount;

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.collect.Maps.newHashMap;

public class CredentialsMapper {
    
    private static Map<String, Function<Map<String, String>, Map<String, String>>> CREDENTIALS_MAPPER = new HashMap() {{
        put("stripe", stripeCredentialMapper());
    }} ;

    private static Function<Map<String, String>, Map<String, String>> stripeCredentialMapper() {
        return input -> { 
            if (input == null || input.isEmpty()) {
                return newHashMap();
            }
            return ImmutableMap.of("account_id", input.get("account_id")); 
        };
    }
    
    public static Optional<Function<Map<String, String>, Map<String, String>>> getCredentialsMapperForPaymentProvider(String paymentProvider) {
        return Optional.ofNullable(CREDENTIALS_MAPPER.get(paymentProvider));
    }
}
