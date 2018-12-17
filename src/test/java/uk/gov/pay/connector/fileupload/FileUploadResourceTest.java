package uk.gov.pay.connector.fileupload;

import io.dropwizard.testing.junit.ResourceTestRule;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Entity;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class FileUploadResourceTest {

    @ClassRule
    public static final ResourceTestRule resource = ResourceTestRule.builder()
            .addProvider(MultiPartFeature.class)
            .addResource(new FileUploadResource())
            .build();

    @Test
    public void testClientMultipart() {
        final FormDataMultiPart multiPart = new FormDataMultiPart()
                .field("test-data", "Hello Multipart");
        final String response = resource.target("/file-upload")
                .register(MultiPartFeature.class)
                .request()
                .post(Entity.entity(multiPart, multiPart.getMediaType()), String.class);
        assertThat(response, is("Hello Multipart"));
    }

}
