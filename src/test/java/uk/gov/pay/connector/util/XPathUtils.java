package uk.gov.pay.connector.util;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;

public class XPathUtils {
    
    public static XPathAndDocument getFromXmlString(String xml) throws ParserConfigurationException, IOException, SAXException {
        InputSource inputXML = new InputSource( new StringReader(xml));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(inputXML);
        XPathFactory xpathFactory = XPathFactory.newInstance();
        return new XPathAndDocument(xpathFactory.newXPath(), document);
    }

    public static class XPathAndDocument {
        
        private final XPath xpath;
        private final Document document;

        XPathAndDocument(XPath xpath, Document document) {
            this.xpath = xpath;
            this.document = document;
        }

        public XPath getXpath() {
            return xpath;
        }

        public Document getDocument() {
            return document;
        }
    }
}
