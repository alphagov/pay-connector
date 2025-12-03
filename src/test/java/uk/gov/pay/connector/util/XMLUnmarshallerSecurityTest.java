package uk.gov.pay.connector.util;

import org.eclipse.persistence.exceptions.XMLMarshalException;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.util.XMLUnmarshallerException;

import jakarta.xml.bind.UnmarshalException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
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

        var exception = assertThrows(XMLUnmarshallerException.class, () -> XMLUnmarshaller.unmarshall(xmlData, XMLUnmarshallingAttackTest.class));

        assertThat(exception.getCause(),
                is(unmarshalExceptionWithLinkedSAXParseException("JAXP00010001: The parser has encountered more than \"1\" entity expansions in this document; this is the limit imposed by the JDK.")));
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

    private Matcher<Throwable> unmarshalExceptionWithLinkedSAXParseException(final String expectedMessage) {
        return new TypeSafeMatcher<Throwable>() {
            @Override
            protected boolean matchesSafely(Throwable throwable) {
                if (throwable instanceof UnmarshalException) {
                    UnmarshalException ex = (UnmarshalException) throwable;
                    XMLMarshalException linkedException = (XMLMarshalException) ex.getLinkedException();
                    Throwable internalException = linkedException.getInternalException();
                    if (internalException instanceof SAXParseException) {
                        return expectedMessage.equals(internalException.getMessage());
                    }
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("UnmarshalException with linked exception to be SAXParseException")
                        .appendText(" and message: '")
                        .appendValue(expectedMessage)
                        .appendText("'");
            }
        };
    }
}
