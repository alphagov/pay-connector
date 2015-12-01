package uk.gov.pay.connector.mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.postgresql.util.PGobject;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GatewayAccountMapperTest {

    @Test(expected = RuntimeException.class)
    public void shouldThrowExceptionIfMapperCannotParseString() throws IOException, SQLException {
        ObjectMapper oMapper = mock(ObjectMapper.class);
        doThrow(new IOException("")).when(oMapper).readValue(any(String.class), any(TypeReference.class));
        GatewayAccountMapper mapper = new GatewayAccountMapper(oMapper);

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("gateway_account_id")).thenReturn(1234L);
        when(resultSet.getString("payment_provider")).thenReturn("whatever");
        PGobject pgObject = new PGobject();
        pgObject.setType("json");
        pgObject.setValue("{}");
        when(resultSet.getObject("credentials")).thenReturn(pgObject);

        mapper.map(0, resultSet, null);


    }

}