package uk.gov.pay.connector.gateway.worldpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public enum WorldpayStatus {
    AUTHORISED("AUTHORISED", ChargeStatus.AUTHORISATION_SUCCESS),
    CANCELLED("CANCELLED", ChargeStatus.AUTHORISATION_CANCELLED),
    CAPTURED("CAPTURED", ChargeStatus.CAPTURED),
    REFUSED("REFUSED", ChargeStatus.AUTHORISATION_REJECTED);

    private static final Logger logger = LoggerFactory.getLogger(WorldpayStatus.class);
    
    private final String worldpayStatus;
    private final ChargeStatus payStatus;
    private static Map<String, WorldpayStatus> stringWorldpayStatusMap = Stream.of(values()).collect(toMap(WorldpayStatus::toString, e -> e));

    WorldpayStatus(String worldpayStatus, ChargeStatus payStatus) {
        this.worldpayStatus = worldpayStatus;
        this.payStatus = payStatus;
    }

    public String getWorldpayStatus() {
        return worldpayStatus;
    }

    public ChargeStatus getPayStatus() {
        return payStatus;
    }
    
    @Override
    public String toString() {
        return getWorldpayStatus();
    }
    
    public static Optional<WorldpayStatus> fromString(String status) {
        return Optional.ofNullable(stringWorldpayStatusMap.get(status));
    }
}
