package uk.gov.pay.connector.charge.model.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

import static org.apache.commons.lang3.Strings.CS;

public enum FeeType {
    RADAR("radar"),
    THREE_D_S("three_ds"),
    TRANSACTION("transaction");

    FeeType(String name) {
        this.name = name;
    }

    private String name;

    @JsonValue
    public String getName() {
        return name;
    }

    public static FeeType fromString(String feeTypeValue) {
        return Arrays.stream(FeeType.values())
                .filter(feeTypeEnum -> CS.equals(feeTypeEnum.getName(), feeTypeValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("fee type not recognized: " + feeTypeValue));
    }
}
