package uk.gov.pay.connector.cloudfront;

public class CloudfrontEncryptedField  {
    private final String value;

    public CloudfrontEncryptedField(String value) {
        this.value = value;
    }
    
    public String toString() {
        return value;
    }
}
