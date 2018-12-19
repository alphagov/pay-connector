package uk.gov.pay.connector.fileupload;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

@Path("/")
public class FileUploadResource {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadResource.class);

    private StripeGatewayConfig stripeGatewayConfig;

    @Inject
    public FileUploadResource(StripeGatewayConfig stripeGatewayConfig) {
        this.stripeGatewayConfig = stripeGatewayConfig;
    }

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
                       @FormDataParam("file") FormDataContentDisposition fileDisposition)
            throws IOException, StripeException, MimeTypeException {
        // Get the image bytes
        byte[] imageBytes = ByteStreams.toByteArray(file);

        // Get the image name
        String imageName = fileDisposition.getFileName();
        logger.info("imageName: " + imageName);

        // Detect the image type
        String imageType = detectFileType(imageBytes, imageName);
        logger.info("imageType: " + imageType);

        // Upload to Stripe
        logger.info("Uploading to Stripe");
        com.stripe.model.File stripeFile = uploadToStripe(imageName, imageType, imageBytes);
        logger.info("Stripe file id: " + stripeFile.getId());
        logger.info("Stripe file: " + stripeFile);

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

    private com.stripe.model.File uploadToStripe(String imageName, String imageType, byte[] imageBytes)
            throws StripeException, IOException, MimeTypeException {
        Stripe.apiKey = stripeGatewayConfig.getAuthToken();

        // extract image name and valid image extension
        String imageNameWithoutExtension = imageName;
        if (imageNameWithoutExtension.contains(".")) {
            imageNameWithoutExtension =
                    imageNameWithoutExtension.substring(0, imageNameWithoutExtension.lastIndexOf('.'));
        }
        String imageNameExtension = MimeTypes.getDefaultMimeTypes().forName(imageType).getExtension();

        // create temporary file
        java.nio.file.Path path = Files.createTempFile(imageNameWithoutExtension, imageNameExtension);
        Files.write(path, imageBytes, StandardOpenOption.WRITE);

        // Upload file to Stripe
        Map<String, Object> fileParams = new HashMap<>();
        fileParams.put("purpose", "identity_document");
        fileParams.put("file", path.toFile());
        com.stripe.model.File stripeFile = com.stripe.model.File.create(fileParams);

        // delete temporary file
        Files.deleteIfExists(path);

        return stripeFile;
    }

}
