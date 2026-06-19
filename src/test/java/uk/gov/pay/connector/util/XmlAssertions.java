package uk.gov.pay.connector.util;

import org.xmlunit.assertj3.XmlAssert;
import org.xmlunit.util.DocumentBuilderFactoryConfigurer;

import javax.xml.parsers.DocumentBuilderFactory;

public final class XmlAssertions {

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

    private XmlAssertions() {
    }


    public static XmlAssert assertThat(String xml) {
        return XmlAssert.assertThat(xml).withDocumentBuilderFactory(documentBuilderFactory);
    }
    
}
