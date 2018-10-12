@XmlSchema(
        xmlns = {
                @XmlNs(prefix = "soap", namespaceURI = "http://schemas.xmlsoap.org/soap/envelope/"),
                @XmlNs(prefix = "ns1", namespaceURI = "http://payment.services.adyen.com")
        }
) package uk.gov.pay.connector.gateway.smartpay;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlSchema;
