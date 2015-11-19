package uk.gov.pay.connector.mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class GatewayAccountMapper implements ResultSetMapper<Map<String, Object>> {

    private final ObjectMapper mapper;
    private static Logger logger = LoggerFactory.getLogger(GatewayAccountMapper.class);

    public GatewayAccountMapper() {
        this(new ObjectMapper());
    }

    GatewayAccountMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<String, Object> map(int index, ResultSet r, StatementContext ctx) throws SQLException {
        Map<String, Object> gatewayAccount = new HashMap<>();
        gatewayAccount.put("gateway_account_id", r.getLong("gateway_account_id"));
        gatewayAccount.put("payment_provider", r.getString("payment_provider"));
        PGobject credentialsPgObject = (PGobject) r.getObject("credentials");
        String credentialsJsonString = credentialsPgObject.toString();
        try {
            Map<String,String> credentialsMap = mapper.readValue(credentialsJsonString, new TypeReference<Map<String, String>>() {});
            gatewayAccount.put("credentials", credentialsMap);
            return gatewayAccount;
        } catch (IOException e) {
            String errorMessage = String.format("Unable to convert '%s' to a map", credentialsJsonString);
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        }
    }

}
