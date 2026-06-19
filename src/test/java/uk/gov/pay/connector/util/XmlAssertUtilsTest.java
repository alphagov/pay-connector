package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

class XmlAssertUtilsTest {

    @Test
    void shouldCreateXmlAssertFromString() {
        XmlAssertUtils.assertThat("<root/>")
                .and("<root/>")
                .areIdentical();
    }

    @Test
    void shouldCreateXmlAssertFromInputStream() {
        XmlAssertUtils.assertThat(
                        new ByteArrayInputStream("<root/>".getBytes(StandardCharsets.UTF_8)))
                .and("<root/>")
                .areIdentical();
    }

    @Test
    void shouldCreateXmlAssertFromDocument() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream("<root/>".getBytes(StandardCharsets.UTF_8)));

        XmlAssertUtils.assertThat(document)
                .and("<root/>")
                .areIdentical();
    }
}
