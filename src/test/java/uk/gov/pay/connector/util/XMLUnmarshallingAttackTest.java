package uk.gov.pay.connector.util;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;


@XmlRootElement(name = "foo")
public class XMLUnmarshallingAttackTest {

    private String value;

    @XmlValue
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "XMLUnmarshallingAttackTest{" +
                "value='" + value + '\'' +
                '}';
    }
}
