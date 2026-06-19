package uk.gov.pay.connector.util;

import org.w3c.dom.Document;
import org.xmlunit.assertj3.XmlAssert;
import org.xmlunit.util.DocumentBuilderFactoryConfigurer;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

public final class XmlAssertUtils {

    private static final DocumentBuilderFactoryConfigurer documentBuilderFactoryConfigurer =
            DocumentBuilderFactoryConfigurer.builder()
                    .withDTDLoadingDisabled()
                    .build();

    private static final DocumentBuilderFactory documentBuilderFactory =
            documentBuilderFactoryConfigurer.configure(DocumentBuilderFactory.newInstance());

    static {
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setXIncludeAware(false);
        documentBuilderFactory.setExpandEntityReferences(false);
    }

    private XmlAssertUtils() {
    }


    public static XmlAssert assertThat(String xml) {
        return XmlAssert.assertThat(xml).withDocumentBuilderFactory(documentBuilderFactory);
    }

    public static XmlAssert assertThat(InputStream inputStream) {
        return XmlAssert.assertThat(inputStream).withDocumentBuilderFactory(documentBuilderFactory);
    }

    public static XmlAssert assertThat(Document document) {
        return XmlAssert.assertThat(document).withDocumentBuilderFactory(documentBuilderFactory);
    }
}
