package uk.gov.pay.connector.gateway.util;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

public class XMLUnmarshaller {

    private static final String JDK_ENTITY_EXPANSION_LIMIT = "http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit";
    private static final String JDK_ENTITY_EXPANSION_LIMIT_VALUE = "1";

    /**
     * Unmarshall XML payloads to Java instance
     *
     * @param payload Payload as XML format
     * @param clazz   Target Class of unmarshalling method
     * @param <T>
     * @return
     * @throws XMLUnmarshallerException
     * @implNote DTD validations are disabled by default (not http access is allowed) and mitigate XXE attack
     * NamespaceAware must be set to true (soap envelopes are being unmarshalled using same method)
     */
    public static <T> T unmarshall(String payload, Class<T> clazz) throws XMLUnmarshallerException {
        try {
            XMLReader xmlReader = buildXmlReader();
            return unmarshall(payload, clazz, xmlReader);
        } catch (ParserConfigurationException | SAXException | JAXBException e) {
            throw new XMLUnmarshallerException(e);
        }
    }

    private static <T> T unmarshall(String payload, Class<T> clazz, XMLReader xmlReader) throws JAXBException {
        InputSource inputSource = new InputSource(new ByteArrayInputStream(payload.getBytes(UTF_8)));
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return unmarshaller.unmarshal(new SAXSource(xmlReader, inputSource), clazz).getValue();
    }

    private static XMLReader buildXmlReader() throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setFeature(FEATURE_SECURE_PROCESSING, true); // Explicitly set. Do not remove
        disableExternalDTDs(spf);
        disableExternalEntities(spf);
        SAXParser saxParser = spf.newSAXParser();
        saxParser.setProperty(JDK_ENTITY_EXPANSION_LIMIT, JDK_ENTITY_EXPANSION_LIMIT_VALUE);
        return saxParser.getXMLReader();
    }

    private static void disableExternalEntities(SAXParserFactory saxParserFactory) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        saxParserFactory.setXIncludeAware(false);
    }

    private static void disableExternalDTDs(SAXParserFactory saxParserFactory) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        saxParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        saxParserFactory.setFeature("http://xml.org/sax/features/validation", false);
        saxParserFactory.setNamespaceAware(true);
    }
}
