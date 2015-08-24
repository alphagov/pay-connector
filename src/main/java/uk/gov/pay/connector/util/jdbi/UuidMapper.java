package uk.gov.pay.connector.util.jdbi;

import org.skife.jdbi.v2.util.TypedMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UuidMapper extends TypedMapper<UUID> {

    /**
     * An instance which extracts value from the first field
     */
    public static final UuidMapper FIRST = new UuidMapper(1);


    /**
     * Create a new instance which extracts the value from the first column
     */
    public UuidMapper()
    {
        super();
    }

    /**
     * Create a new instance which extracts the value positionally
     * in the result set
     *
     * @param index 1 based column index into the result set
     */
    public UuidMapper(int index)
    {
        super(index);
    }

    /**
     * Create a new instance which extracts the value by name or alias from the result set
     *
     * @param name The name or alias for the field
     */
    public UuidMapper(String name)
    {
        super(name);
    }

    @Override
    protected UUID extractByName(ResultSet r, String name) throws SQLException
    {
        return UUID.fromString(r.getString(name));
    }

    @Override
    protected UUID extractByIndex(ResultSet r, int index) throws SQLException
    {
        return UUID.fromString(r.getString(index));
    }

}
