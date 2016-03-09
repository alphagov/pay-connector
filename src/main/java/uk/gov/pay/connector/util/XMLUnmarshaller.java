package uk.gov.pay.connector.util;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;

public class XMLUnmarshaller {

    /**
     * Unmarshall XML payloads to Java instance
     * @implNote DTD validations are disabled by default (not http access is allowed) and mitigate XXE attack
     * NamespaceAware must be set to true (soap envelopes are being unmarshalled using same method)
     * @param payload Payload as XML format
     * @param clazz Target Class of unmarshalling method
     * @param <T>
     * @return
     * @throws XMLUnmarshallerException
     */
    public static <T> T unmarshall(String payload, Class<T> clazz) throws XMLUnmarshallerException {
        try {
            XMLReader xmlReader = buildXmlReaderWithDtdValidationDisabled();
            return unmarshall(payload, clazz, xmlReader);
        } catch (ParserConfigurationException | SAXException | JAXBException e) {
            throw new XMLUnmarshallerException(e);
        }
    }

    private static <T> T unmarshall(String payload, Class<T> clazz, XMLReader xmlReader) throws JAXBException {
        InputSource inputSource = new InputSource(new ByteArrayInputStream(payload.getBytes()));
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return unmarshaller.unmarshal(new SAXSource(xmlReader, inputSource), clazz).getValue();
    }

    private static XMLReader buildXmlReaderWithDtdValidationDisabled() throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        spf.setFeature("http://xml.org/sax/features/validation", false);
        spf.setNamespaceAware(true);
        return spf.newSAXParser().getXMLReader();
    }
}
