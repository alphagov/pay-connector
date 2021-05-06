package uk.gov.pay.connector.tasks;

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
            if (type.getValue().equals(recordTypeParam) || type.getValue().toUpperCase().equals(recordTypeParam)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Record type not recognized: " + recordTypeParam);
    }
}
