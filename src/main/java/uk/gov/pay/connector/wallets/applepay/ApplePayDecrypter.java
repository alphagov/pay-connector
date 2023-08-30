package uk.gov.pay.connector.wallets.applepay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ApplePayConfig;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.DAYS;

public class ApplePayDecrypter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplePayDecrypter.class);

    private static final byte[] COUNTER = {0x00, 0x00, 0x00, 0x01};
    private static final byte[] APPLE_OEM = "Apple".getBytes(UTF_8);
    private static final byte[] ALG_IDENTIFIER_BYTES = "id-aes256-GCM".getBytes(UTF_8);
    private static final String MERCHANT_ID_CERTIFICATE_OID = "1.2.840.113635.100.6.32";

    private final PrivateKey primaryPrivateKey;
    private final X509Certificate primaryCertificate;
    private final Optional<PrivateKey> secondaryPrivateKey;
    private final Optional<X509Certificate> secondaryCertificate;

    private final ObjectMapper objectMapper;
    private final static Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    @Inject
    public ApplePayDecrypter(WorldpayConfig worldpayConfig, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        ApplePayConfig applePayConfig = worldpayConfig.getApplePayConfig();
        try {
            primaryPrivateKey = generatePrivateKey(removeWhitespace(applePayConfig.getPrimaryPrivateKey()));
            primaryCertificate = generateCertificate(removeWhitespace(applePayConfig.getPrimaryPublicCertificate()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        secondaryPrivateKey = applePayConfig.getSecondaryPrivateKey().map(privateKey -> {
            try {
                return generatePrivateKey(removeWhitespace(privateKey));
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new RuntimeException();
            }
        });
        secondaryCertificate = applePayConfig.getSecondaryPublicCertificate().map(cert -> {
            try {
                return generateCertificate(removeWhitespace(cert));
            } catch (IOException | CertificateException e) {
                throw new RuntimeException();
            }
        });

        long daysToExpiry = DAYS.between(Instant.now(), primaryCertificate.getNotAfter().toInstant());
        LOGGER.info("The Apple Pay payment processing cert will expire in {} days", daysToExpiry);
    }

    private String removeWhitespace(String input) {
        return input.replaceAll("\\s+", "");
    }

    public AppleDecryptedPaymentData performDecryptOperation(ApplePayAuthRequest applePayAuthRequest) {

        ApplePayPaymentData applePayPaymentData = deserialisePaymentData(applePayAuthRequest.getPaymentData());

        byte[] ephemeralPublicKey = BASE64_DECODER.decode(applePayPaymentData.getHeader().getEphemeralPublicKey().getBytes(UTF_8));
        byte[] data = BASE64_DECODER.decode(applePayPaymentData.getData().getBytes(UTF_8));
        byte[] rawData;

        try {
            rawData = decrypt(primaryCertificate, primaryPrivateKey, ephemeralPublicKey, data);
        } catch (Exception e) {
            if (secondaryCertificate.isPresent() && secondaryPrivateKey.isPresent()) {
                LOGGER.info("Could not decrypt Apple auth request with primary key, trying with secondary key.");
                try {
                    rawData = decrypt(secondaryCertificate.get(), secondaryPrivateKey.get(), ephemeralPublicKey, data);
                } catch (Exception ex) {
                    LOGGER.error("Error while trying to decrypt apple pay payload: " + ex.getMessage());
                    throw new InvalidKeyException("Error while trying to decrypt apple pay payload: " + ex.getMessage());
                }
            } else {
                LOGGER.info("Could not decrypt Apple auth request with primary key, and secondary key is not " +
                        "present. Throwing an InvalidKeyException.");
                LOGGER.error("Error while trying to decrypt apple pay payload: " + e.getMessage());
                throw new InvalidKeyException("Error while trying to decrypt apple pay payload: " + e.getMessage());
            }
        }

        try {
            return objectMapper.readValue(new String(rawData, UTF_8), AppleDecryptedPaymentData.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error while trying to decrypt apple pay payload: " + e.getMessage());
            throw new InvalidKeyException("Error while trying to decrypt apple pay payload: " + e.getMessage());
        }
    }

    private ApplePayPaymentData deserialisePaymentData(String paymentData) {
        try {
            return objectMapper.readValue(paymentData, ApplePayPaymentData.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error deserialising apple pay payment data: " + e.getMessage());
            throw new InvalidApplePayPaymentDataException("Error while trying to deserialise apple pay payload: " + e.getMessage());
        }
    }

    private byte[] decrypt(Certificate certificate, PrivateKey privateKey, byte[] ephemeralPublicKeyBytes, byte[] data) throws Exception {
        // Reconstitute Ephemeral Public Key
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        X509EncodedKeySpec encodedKeySpec = new X509EncodedKeySpec(ephemeralPublicKeyBytes);
        ECPublicKey ephemeralPublicKey = (ECPublicKey) keyFactory.generatePublic(encodedKeySpec);
        // Perform KeyAgreement
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(ephemeralPublicKey, true);
        byte[] sharedSecret = agreement.generateSecret();

        // Perform KDF
        byte[] derivedSecret = performKeyDerivationFunction(((X509Certificate) certificate), sharedSecret);

        // Use the derived secret to decrypt the data
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec key = new SecretKeySpec(derivedSecret, "AES");
        aesCipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, new byte[16]));
        return aesCipher.doFinal(data);
    }

    private byte[] performKeyDerivationFunction(X509Certificate certificate, byte[] sharedSecret) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(COUNTER);
        byteArrayOutputStream.write(sharedSecret);
        byteArrayOutputStream.write(ALG_IDENTIFIER_BYTES.length);
        byteArrayOutputStream.write(ALG_IDENTIFIER_BYTES);
        byteArrayOutputStream.write(APPLE_OEM);
        // Add Merchant Id
        byteArrayOutputStream.write(Hex.decodeHex(new String((certificate.getExtensionValue(MERCHANT_ID_CERTIFICATE_OID)), UTF_8).substring(4).toCharArray()));
        return MessageDigest.getInstance("SHA-256").digest(byteArrayOutputStream.toByteArray());
    }

    private X509Certificate generateCertificate(String publicCertificate) throws IOException, CertificateException {
        byte[] publicCertificateBytes = BASE64_DECODER.decode(publicCertificate);
        try (InputStream stream = new ByteArrayInputStream(publicCertificateBytes)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certificateFactory.generateCertificate(stream);
        }
    }

    private PrivateKey generatePrivateKey(String privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] privateKeyBytes = BASE64_DECODER.decode(privateKey);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
    }

}
