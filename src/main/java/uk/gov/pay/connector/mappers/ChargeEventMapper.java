package uk.gov.pay.connector.mappers;


import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import uk.gov.pay.connector.model.domain.ChargeEvent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChargeEventMapper implements ResultSetMapper<ChargeEvent> {

    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    public ChargeEvent map(int i, ResultSet resultSet, StatementContext statementContext) throws SQLException {
        return new ChargeEvent(
                resultSet.getLong("charge_id"),
                resultSet.getString("status"),
                LocalDateTime.parse(resultSet.getString("updated"), dateTimeFormatter));
    }
}
