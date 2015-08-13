package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.util.LongMapper;

public class PaymentDao {
    private DBI jdbi;

    public PaymentDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public long insertAmountAndReturnNewId(long amount) {
        return jdbi.withHandle(handle ->
                        handle
                                .createStatement("INSERT INTO payments(amount) VALUES (:amount)")
                                .bind("amount", amount)
                                .executeAndReturnGeneratedKeys(LongMapper.FIRST)
                                .first()
        );
    }

    public long getAmountById(long payId) {
        return jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT amount FROM payments WHERE pay_id=:pay_id")
                                .bind("pay_id", payId)
                                .map(LongMapper.FIRST)
                                .first()
        );
    }
}
