package uk.gov.pay.connector.model.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.eclipse.persistence.annotations.Customizer;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.HistoryCustomizer;
import uk.gov.pay.connector.model.domain.RefundHistory;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public class RefundEntityNew {
    @Column(name = "smartpay_psprefrence")
    private String smartpayPspReference;

    @Column(name = "epdq_payid")
    private String epdqPayId;

    @Column(name = "epdq_payidsub")
    private String epdqPayIdSub;

    @Column(name = "refunded_by")
    private String refunded_by;
}
