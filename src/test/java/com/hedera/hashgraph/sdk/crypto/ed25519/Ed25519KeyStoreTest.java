package com.hedera.hashgraph.sdk.crypto.ed25519;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ed25519KeyStoreTest {

    private final static String dataPath = "src/test/resources/KeyStoreTestsData/";

    @Test
    void read_Test() throws KeyStoreException {
        File treasury = new File(dataPath + "treasury.pem");

        Ed25519KeyStore keyStore = Ed25519KeyStore.read("123456789".toCharArray(), treasury);
        assertNotNull(keyStore);
        KeyPair keyPair = keyStore.get(0);
        assertNotNull(keyPair.getPublic());
        String publicKeyString = Hex.toHexString(((EdDSAPublicKey) keyPair.getPublic()).getAbyte());

        String publicKeyFromFile = "";
        try {
            publicKeyFromFile = new String(Files.readAllBytes(Paths.get(dataPath + "treasury.pub")));
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertEquals(publicKeyFromFile, publicKeyString);
    }

    @Test
    void write_Test() throws KeyStoreException {
        Ed25519PrivateKey privateKey = Ed25519PrivateKey.fromString(
            "302e020100300506032b657004220420bcd07afbebdace34ffbd13fb333ac8fb3f30472d220dda006d8571ea03563a8a");
        Ed25519KeyStore keyStore = new Ed25519KeyStore.Builder().withPassword("123456789".toCharArray()).build();

        keyStore.insertNewKeyPair(privateKey);

        keyStore.write(new File(dataPath + "key.pem"));

        Ed25519KeyStore readKeyStore =
            Ed25519KeyStore.read("123456789".toCharArray(), new File(dataPath + "key.pem"));

        assertNotNull(keyStore);
        KeyPair keyPair = keyStore.get(0);
        Ed25519PrivateKey readPrivateKey = Ed25519PrivateKey.fromBytes(keyPair.getPrivate().getEncoded());

        assertArrayEquals(privateKey.toBytes(), readPrivateKey.toBytes());

        new File(dataPath + "key.pem").delete();
    }
}
