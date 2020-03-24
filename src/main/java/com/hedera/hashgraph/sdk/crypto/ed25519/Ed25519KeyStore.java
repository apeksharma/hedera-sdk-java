package com.hedera.hashgraph.sdk.crypto.ed25519;

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.pem.PemGenerationException;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.DrbgParameters;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;

public class Ed25519KeyStore extends ArrayList<KeyPair> {

    private static final Provider BC_PROVIDER = new BouncyCastleProvider();
    private static final Provider ED_PROVIDER = new EdDSASecurityProvider();

    private final SecureRandom random;
    private final JcaPEMKeyConverter converter;
    private final char[] password;

    private Ed25519KeyStore(final char[] password, final JcaPEMKeyConverter converter, final SecureRandom random) {
        this.password = password;
        this.converter = converter;
        this.random = random;
    }

    public static Ed25519KeyStore read(final char[] password, final File source) throws KeyStoreException {
        final Ed25519KeyStore keyStore = new Builder().withPassword(password).build();
        keyStore.loadFile(source);
        return keyStore;
    }

    public void write(final File dest) throws KeyStoreException {
        //make parent directory if it doesn't exist
        if (dest.getParentFile() != null) {
            dest.getParentFile().mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            write(fos);
        } catch (IOException ex) {
            throw new KeyStoreException(ex);
        }
    }

    public void write(final OutputStream ostream) throws KeyStoreException {
        try {
            if (isEmpty()) {
                throw new KeyStoreException("KeyStore is currently empty and cannot be persisted.");
            }

            final OutputEncryptor encryptor = (new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.AES_256_CBC))
                .setPRF(PKCS8Generator.PRF_HMACSHA384)
                .setIterationCount(10000)
                .setRandom(random)
                .setPasssword(password)
                .setProvider(BC_PROVIDER)
                .build();

            try (final JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(ostream))) {
                for (KeyPair kp : this) {
                    pemWriter.writeObject(encodeKeyPair(kp, encryptor));
                }
                pemWriter.flush();
            }
        } catch (IOException | OperatorCreationException ex) {
            throw new KeyStoreException(ex);
        }
    }


    private void loadFile(final File source) throws KeyStoreException {
        try (final FileInputStream fis = new FileInputStream(source)) {
            loadKeyPairs(fis);
        } catch (IOException ex) {
            throw new KeyStoreException(ex);
        }
    }

    private void loadKeyPairs(final InputStream istream) throws KeyStoreException {
        clear();

        try (final PEMParser parser = new PEMParser(new InputStreamReader(istream))) {

            Object rawObject;
            while ((rawObject = parser.readObject()) != null) {
                add(decodeKeyPair(rawObject));
            }
        } catch (IOException ex) {
            throw new KeyStoreException(ex);
        }
    }

    private PemObject encodeKeyPair(KeyPair keyPair, OutputEncryptor encryptor) throws KeyStoreException {
        try {
            return new JcaPKCS8Generator(keyPair.getPrivate(), encryptor).generate();
        } catch (PemGenerationException ex) {
            throw new KeyStoreException(ex);
        }
    }

    private KeyPair decodeKeyPair(Object rawObject) throws KeyStoreException {
        try {
            KeyPair kp;

            if (rawObject instanceof PEMEncryptedKeyPair) {
                final PEMEncryptedKeyPair ekp = (PEMEncryptedKeyPair) rawObject;
                final PEMDecryptorProvider decryptor = new JcePEMDecryptorProviderBuilder().setProvider(
                    BC_PROVIDER).build(password);
                kp = converter.getKeyPair(ekp.decryptKeyPair(decryptor));
            } else if (rawObject instanceof PKCS8EncryptedPrivateKeyInfo) {
                final PKCS8EncryptedPrivateKeyInfo ekpi = (PKCS8EncryptedPrivateKeyInfo) rawObject;
                final InputDecryptorProvider decryptor = new JceOpenSSLPKCS8DecryptorProviderBuilder()
                    .setProvider(BC_PROVIDER)
                    .build(password);

                final PrivateKeyInfo pki = ekpi.decryptPrivateKeyInfo(decryptor);
                final EdDSAPrivateKey sk = (EdDSAPrivateKey) converter.getPrivateKey(pki);
                final EdDSAPublicKey pk = new EdDSAPublicKey(
                    new EdDSAPublicKeySpec(sk.getA(), EdDSANamedCurveTable.ED_25519_CURVE_SPEC));
                kp = new KeyPair(pk, sk);
            } else {
                final PEMKeyPair ukp = (PEMKeyPair) rawObject;
                kp = converter.getKeyPair(ukp);
            }

            return kp;
        } catch (IOException | OperatorCreationException | PKCSException ex) {
            throw new KeyStoreException(ex);
        }
    }

    public KeyPair insertNewKeyPair(Ed25519PrivateKey privateKey) throws KeyStoreException{
        try {
            final KeyPair kp = buildKeyPairFromMainnetPriKeyEncHex(privateKey.toString());
            this.add(kp);
            return kp;
        } catch (Exception e) {
            throw new KeyStoreException(e);
        }
    }

    /**
     * Build a KeyPair from a priKeyEncHex String generated by hedera key-gen tool
     *
     * @param priKeyEncHex
     * @return
     */
    private static KeyPair buildKeyPairFromMainnetPriKeyEncHex(final String priKeyEncHex) {
        byte[] privateKeyBytes = Hex.decode(priKeyEncHex);
        EdDSAPrivateKey privateKey;
        EdDSAPublicKey publicKey;
        try {
            // try encoded first
            final PKCS8EncodedKeySpec encodedPrivKey = new PKCS8EncodedKeySpec(privateKeyBytes);
            privateKey = new EdDSAPrivateKey(encodedPrivKey);
        } catch (InvalidKeySpecException e) {
            // key is invalid (likely not encoded)
            // try non encoded
            final EdDSAPrivateKeySpec privKey =
                new EdDSAPrivateKeySpec(privateKeyBytes, EdDSANamedCurveTable.ED_25519_CURVE_SPEC);
            privateKey = new EdDSAPrivateKey(privKey);
        }

        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

        publicKey = new EdDSAPublicKey(
            new EdDSAPublicKeySpec(privateKey.getAbyte(), spec));
        return new KeyPair(publicKey, privateKey);
    }

    public static final class Builder {
        private SecureRandom random;
        private JcaPEMKeyConverter converter;
        private char[] password;

        public Builder() {
        }

        public Builder withRandom(SecureRandom random) {
            this.random = random;
            return this;
        }

        public Builder withConverter(JcaPEMKeyConverter converter) {
            this.converter = converter;
            return this;
        }

        public Builder withPassword(char[] password) {
            this.password = password;
            return this;
        }

        public Ed25519KeyStore build() throws KeyStoreException {
            if (password == null) {
                password = new char[0];
            }
            if(converter == null) {
                this.converter = new JcaPEMKeyConverter().setProvider(ED_PROVIDER);
            }
            if(random == null) {
                try {
                    this.random = SecureRandom.getInstance("DRBG",
                        DrbgParameters.instantiation(256, DrbgParameters.Capability.RESEED_ONLY, null));
                } catch (NoSuchAlgorithmException ex) {
                    throw new KeyStoreException(ex);
                }
            }
            return new Ed25519KeyStore(password, converter, random);
        }
    }
}
