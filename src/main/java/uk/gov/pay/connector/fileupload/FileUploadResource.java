package uk.gov.pay.connector.fileupload;

import com.google.common.io.ByteStreams;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;

@Path("/")
public class FileUploadResource {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadResource.class);

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
        // Get the image bytes
        byte[] imageBytes = ByteStreams.toByteArray(file);

        // Get the image name
        String imageName = fileDisposition.getFileName();
        logger.info("imageName: " + imageName);

        // Detect the image type
        String imageType = detectFileType(imageBytes, imageName);
        logger.info("imageType: " + imageType);

        return fileDisposition.getFileName();
    }

    private String detectFileType(byte[] imageBytes, String imageName) throws IOException {
        TikaConfig config = TikaConfig.getDefaultConfig();
        Detector detector = config.getDetector();

        TikaInputStream stream = TikaInputStream.get(imageBytes);

        Metadata metadata = new Metadata();
        metadata.add(Metadata.RESOURCE_NAME_KEY, imageName);
        org.apache.tika.mime.MediaType mediaType = detector.detect(stream, metadata);

        return mediaType.toString();
    }

}
