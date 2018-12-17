package uk.gov.pay.connector.fileupload;

import com.google.common.io.ByteStreams;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;

@Path("/")
public class FileUploadResource {

    @Path("/file-upload/simple-test")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public String post(@FormDataParam("test-data") String testData) {
        return testData;
    }

    @Path("/file-upload/file-test")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public String post(@FormDataParam("file") InputStream file,
                       @FormDataParam("file") FormDataContentDisposition fileDisposition) throws IOException {
        System.out.println(ByteStreams.toByteArray(file));

        return fileDisposition.getFileName();
    }

}
