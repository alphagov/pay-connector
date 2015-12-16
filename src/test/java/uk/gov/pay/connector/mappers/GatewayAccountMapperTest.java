package uk.gov.pay.connector.mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.postgresql.util.PGobject;
import uk.gov.pay.connector.model.domain.GatewayAccount;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class GatewayAccountMapperTest {

    private ObjectMapper objectMapper = mock(ObjectMapper.class);

    @Test(expected = RuntimeException.class)
    public void shouldThrowExceptionIfMapperCannotParseString() throws IOException, SQLException {
        doThrow(new IOException("")).when(objectMapper).readValue(any(String.class), any(TypeReference.class));
        ServiceAccountMapper mapper = new ServiceAccountMapper(objectMapper);

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("gateway_account_id")).thenReturn(1234L);
        when(resultSet.getString("payment_provider")).thenReturn("sandbox");
        PGobject pgObject = new PGobject();
        pgObject.setType("json");
        pgObject.setValue("{}");
        when(resultSet.getObject("credentials")).thenReturn(pgObject);

        mapper.map(0, resultSet, null);


    }

    @Test
    public void shouldMapToAServiceAccountCorrectly() throws Exception {
        ObjectMapper oMapper = mock(ObjectMapper.class);
        ServiceAccountMapper mapper = new ServiceAccountMapper(oMapper);

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("gateway_account_id")).thenReturn(1234L);
        when(resultSet.getString("payment_provider")).thenReturn("worldpay");
        PGobject pgObject = new PGobject();
        pgObject.setType("json");
        pgObject.setValue("{}");
        when(resultSet.getObject("credentials")).thenReturn(pgObject);

        GatewayAccount account = mapper.map(0, resultSet, null);

        assertThat(account.getGatewayName(),is("worldpay"));
        assertThat(account.getId(),is(1234L));

    }

}