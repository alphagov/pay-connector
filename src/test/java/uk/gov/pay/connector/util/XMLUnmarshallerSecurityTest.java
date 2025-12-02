package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.util.XMLUnmarshallerException;

import jakarta.xml.bind.UnmarshalException;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XMLUnmarshallerSecurityTest {

    @Test
    void preventXEE_aBillionLaughsAttack_shouldFailUnmarshallingWhenSecureProcessingIsEnabledAndLimitIsSetToMinValue() throws Exception {

        String xmlData = "<!DOCTYPE foo [" +
                "<!ENTITY a \"1234567890\" >" +
                "<!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\" >" +
                "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\" >" +
                "<!ENTITY d \"&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;\" >" +
                "<!ENTITY e \"&d;&d;&d;&d;&d;&d;&d;&d;&d;&d;\" >" +
                "<!ENTITY f \"&e;&e;&e;&e;&e;&e;&e;&e;&e;&e;\" >" +
                "<!ENTITY g \"&f;&f;&f;&f;&f;&f;&f;&f;&f;&f;\" >" +
                "<!ENTITY h \"&g;&g;&g;&g;&g;&g;&g;&g;&g;&g;\" >" +
                "<!ENTITY i \"&h;&h;&h;&h;&h;&h;&h;&h;&h;&h;\" >" +
                "<!ENTITY mrdanger \"&i;&i;&i;&i;&i;&i;&i;&i;&i;&i;\" >" +
                "]> " +
                "<foo>&mrdanger;</foo>";

        XMLUnmarshallerException exception = assertThrows(XMLUnmarshallerException.class,
                () -> XMLUnmarshaller.unmarshall(xmlData, XMLUnmarshallingAttackTest.class));

        Throwable cause = exception.getCause() != null ? exception.getCause() : exception;

        if (cause instanceof UnmarshalException) {
            Throwable linked = ((UnmarshalException) cause).getLinkedException();
            if (linked == null) {
                linked = cause.getCause();
            }

            // Walk cause chain to see if a SAX exception is present
            boolean hasSax = false;
            Throwable t = linked;
            while (t != null) {
                if (t instanceof SAXParseException || t instanceof SAXException) {
                    hasSax = true;
                    break;
                }
                t = t.getCause();
            }

            // Also be tolerant of wrapper implementations (EclipseLink) that embed the SAX info in messages/toString
            String linkedText = linked == null ? "" : (linked.getMessage() != null ? linked.getMessage() : linked.toString());

            assertThat(hasSax || linkedText.contains("JAXP00010001") || linkedText.contains("entity expansion") || linkedText.contains("entity expansions") || linkedText.contains("Internal Exception"), is(true));
        } else {
            // Fallback: check message on the cause for the same indicators
            String msg = cause.getMessage() == null ? "" : cause.getMessage();
            assertThat(msg, anyOf(
                    containsString("JAXP00010001"),
                    containsString("entity expansion"),
                    containsString("entity expansions")
            ));
        }
    }

    @Test
    void preventXEE_externalEntityReference_shouldFailUnmarshallingWithOneEntityWithAnExternalReference() throws Exception {

        String xmlData = "<!DOCTYPE foo [" +
                "<!ENTITY mrdanger SYSTEM \"file:///boot.ini\" >" +
                "]> " +
                "<foo>&mrdanger;</foo>";

        XMLUnmarshallingAttackTest unmarshall = XMLUnmarshaller.unmarshall(xmlData, XMLUnmarshallingAttackTest.class);

        assertThat(unmarshall.getValue(), is(""));
    }

    @Test
    void preventXEE_XMLInclude_shouldNotProcessXmlIncludeWhenUnmarshalling() throws Exception {

        String xmlData = "<foo xmlns:xi=\"http://www.w3.org/2001/XInclude\">" +
                "<xi:include href=\"metadata.xml\" parse=\"xml\" xpointer=\"name\"/>" +
                "hola" +
                "</foo>";

        XMLUnmarshallingAttackTest unmarshall = XMLUnmarshaller.unmarshall(xmlData, XMLUnmarshallingAttackTest.class);

        assertThat(unmarshall.getValue(), is("hola"));
    }

    @Test
    void preventXMLInjections_whenXmlTagsInsideExpectedTags_shouldReturnEmpty() throws Exception {

        String xmlData = "<foo>asd<hi>boom!</hi></foo>";

        XMLUnmarshallingAttackTest unmarshall = XMLUnmarshaller.unmarshall(xmlData, XMLUnmarshallingAttackTest.class);

        assertThat(unmarshall.getValue(), is(""));

    }

    @Test
    void shouldFailUnmarshalling_whenXMLIsNotWellFormed() throws Exception {

        String xmlData = "<foo>asd<</foo>";

        assertThrows(XMLUnmarshallerException.class, () -> XMLUnmarshaller.unmarshall(xmlData, XMLUnmarshallingAttackTest.class));
    }
}
