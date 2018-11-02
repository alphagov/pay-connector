package uk.gov.pay.connector.applepay;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AsymmetricKeyVerifier {
    private static final String SIGNATURE_ALGORITHM_NAME = "SHA256withECDSA";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    AsymmetricKeyVerifier(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    boolean verify() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        byte[] data = UUID.randomUUID().toString().getBytes(UTF_8);
        byte[] digitalSignature = signData(data);
        Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM_NAME);
        signer.initVerify(publicKey);
        signer.update(data);
        return signer.verify(digitalSignature);
    }

    private byte[] signData(byte[] data) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM_NAME);
        signer.initSign(privateKey);
        signer.update(data);
        return signer.sign();
    }
}
