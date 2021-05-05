package uk.gov.pay.connector.tasks;

import org.apache.commons.lang3.StringUtils;

public enum RecordType {

    CHARGE("charge"),
    REFUND("refund");

    String value;

    RecordType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String toString() {
        return this.getValue();
    }

    public static RecordType fromString(String recordTypeParam) {
        for (RecordType type : values()) {
            if (StringUtils.equals(type.getValue(), recordTypeParam) ||
                    StringUtils.equals(type.getValue().toUpperCase(), recordTypeParam)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Record type not recognized: " + recordTypeParam);
    }
}
