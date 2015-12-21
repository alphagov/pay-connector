package uk.gov.pay.connector.mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.GatewayAccount;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class GatewayAccountMapper implements ResultSetMapper<GatewayAccount> {


    private static Logger logger = LoggerFactory.getLogger(GatewayAccountMapper.class);
    private final ObjectMapper mapper;

    public GatewayAccountMapper() {
        this(new ObjectMapper());
    }

    GatewayAccountMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public GatewayAccount map(int i, ResultSet resultSet, StatementContext statementContext) throws SQLException {
        PGobject credentialsPgObject = (PGobject) resultSet.getObject("credentials");
        String credentialsJsonString = credentialsPgObject.toString();
        long gatewayAccountId = resultSet.getLong("gateway_account_id");
        String paymentGatewayName = resultSet.getString("payment_provider");
        try {
            Map<String,String> credentialsMap = mapper.readValue(credentialsJsonString, new TypeReference<Map<String, String>>() {});
            return new GatewayAccount(gatewayAccountId, paymentGatewayName, credentialsMap);
        } catch (IOException e) {
            String errorMessage = String.format("Unable to convert '%s' to a map", credentialsJsonString);
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        }
    }
}
