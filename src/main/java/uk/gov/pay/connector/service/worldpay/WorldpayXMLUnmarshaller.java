package uk.gov.pay.connector.service.worldpay;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.Reader;
import java.io.StringReader;

public class WorldpayXMLUnmarshaller {

    public static <T> T unmarshall(String payload, Class<T> clazz) throws JAXBException {
        Reader reader = new StringReader(payload);
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return clazz.cast(unmarshaller.unmarshal(reader));
    }
}
