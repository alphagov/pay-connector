package uk.gov.pay.connector.wallets.applepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ApplePayConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
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
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ApplePayDecrypter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplePayDecrypter.class);

    private static final byte[] COUNTER = {0x00, 0x00, 0x00, 0x01};
    private static final byte[] APPLE_OEM = "Apple".getBytes(UTF_8);
    private static final byte[] ALG_IDENTIFIER_BYTES = "id-aes256-GCM".getBytes(UTF_8);
    private static final String MERCHANT_ID_CERTIFICATE_OID = "1.2.840.113635.100.6.32";

    private final byte[] privateKeyBytes;
    private final byte[] publicCertificate;

    private final ObjectMapper objectMapper;
    private final static Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    
    @Inject
    public ApplePayDecrypter(ConnectorConfiguration configuration, ObjectMapper objectMapper) {
        ApplePayConfig applePayConfig = configuration.getWorldpayConfig().getApplePayConfig();
        this.privateKeyBytes = BASE64_DECODER.decode(applePayConfig.getPrivateKey());
        this.publicCertificate = BASE64_DECODER.decode(applePayConfig.getPublicCertificate());
        this.objectMapper = objectMapper;
    }

    public AppleDecryptedPaymentData performDecryptOperation(ApplePayAuthRequest applePayAuthRequest)  {
        try {
            byte[] data = BASE64_DECODER.decode(applePayAuthRequest.getEncryptedPaymentData().getData().getBytes(UTF_8));
            byte[] ephemeralPublicKey = BASE64_DECODER.decode(applePayAuthRequest.getEncryptedPaymentData().getHeader().getEphemeralPublicKey().getBytes(UTF_8));
            PrivateKey privateKey = generatePrivateKey();
            Certificate certificate = generateCertificate();

            AsymmetricKeyVerifier verifier = new AsymmetricKeyVerifier(privateKey, certificate.getPublicKey());
            if (!verifier.verify()) {
                throw new InvalidKeyException("Asymmetric keys do not match!");
            }
            byte[] rawData = decrypt(certificate, privateKey, ephemeralPublicKey, data);
            return objectMapper.readValue(new String(rawData, UTF_8), AppleDecryptedPaymentData.class);
        } catch (Exception e) {
            LOGGER.error("Error while trying to decrypt apple pay payload: " + e.getMessage());
            throw new InvalidKeyException("Error while trying to decrypt apple pay payload: " + e.getMessage());
        }
    }

    private byte[] decrypt(Certificate certificate, PrivateKey merchantPrivateKey, byte[] ephemeralPublicKeyBytes, byte[] data) throws Exception {
        // Reconstitute Ephemeral Public Key
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        X509EncodedKeySpec encodedKeySpec = new X509EncodedKeySpec(ephemeralPublicKeyBytes);
        ECPublicKey ephemeralPublicKey = (ECPublicKey) keyFactory.generatePublic(encodedKeySpec);
        // Perform KeyAgreement
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(merchantPrivateKey);
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
        return MessageDigest.getInstance("SHA-256")
                .digest(byteArrayOutputStream.toByteArray());
    }

    private Certificate generateCertificate() throws IOException, CertificateException {
        try (InputStream stream = new ByteArrayInputStream(publicCertificate)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return certificateFactory.generateCertificate(stream);
        }
    }

    private PrivateKey generatePrivateKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        return KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
    }

}
