package com.hedera.hashgraph.sdk.examples;

import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.file.FileAppendTransaction;
import com.hedera.hashgraph.sdk.file.FileId;
import com.hedera.hashgraph.sdk.file.FileUpdateTransaction;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class FeeScheduleSigner {
    /************** CONFIGURABLE PARAMS *****************/
    // Must change
    private static final String PROTO_FEE_SCHEDULE_PATH = "/Users/appy/Downloads/feeSchedule.txt";
    private static final Ed25519PrivateKey FEE_SCHEDULE_OWNER_KEY = Ed25519PrivateKey.fromString("302e020100300506032b657004220420bcd07afbebdace34ffbd13fb333ac8fb3f30472d220dda006d8571ea03563a8a");
    private static final String SIGNED_TRANSACTIONS_OUTPUT_PATH = "/Users/appy/Downloads/signedFeeScheduleTransactions";
    private static final String WINDOW_START = "2020-01-29 01:00:00";  // starting validStart

    // Optional
    private static final AccountId FEE_SCHEDULE_OWNER = AccountId.fromString("0.0.50");
    private static final AccountId NODE_ID = AccountId.fromString("0.0.3");
    private static final long WINDOW_DURATION_SEC = 86400 * 3; // 3 day
    private static final int CHUNK_SIZE = 5 * 1024; // 5k chunk sizes
    /****************************************************/

    private static final FileId FEE_SCHEDULE_FILE = FileId.fromString("0.0.111");

    public static void main(String[] args) throws Exception {
        List<byte[]> fileChunks = getFileChunks();
        // Map from validStartTime => List of serialized file update + append transactions
        TreeMap<Long, List<byte[]>> signedTransactions = new TreeMap<>();

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long windowStartSec = df.parse(WINDOW_START).toInstant().getEpochSecond();
        long windowEndSec = windowStartSec + WINDOW_DURATION_SEC;

        for (long validStartSec = windowStartSec; validStartSec < windowEndSec; validStartSec += 120) {
            signedTransactions.put(validStartSec, createTransactionsForValidStart(fileChunks, validStartSec));
        }
        writeOutput(signedTransactions);
        System.out.println("Generated signed transactions for window " + windowStartSec + " - " + windowEndSec);
    }

    // Chunks file contents into parts of maximum CHUNK_SIZE size.
    private static List<byte[]> getFileChunks() throws Exception {
        List<byte[]> fileChunks = new ArrayList<>();
        byte[] fileData = new FileInputStream(PROTO_FEE_SCHEDULE_PATH).readAllBytes();
        int fileLength = fileData.length;
        int index = 0;
        while (index < fileLength) {
            int len = Math.min(CHUNK_SIZE, fileLength - index);
            fileChunks.add(Arrays.copyOfRange(fileData, index, index + len));
            index = index + len;
        }
        System.out.println("File size = " + fileData.length);
        System.out.println(String.format("Num chunks (max size : %d) = %d", CHUNK_SIZE, fileChunks.size()));
        return fileChunks;
    }

    private static List<byte[]> createTransactionsForValidStart(List<byte[]> fileChunks, long validStartSeconds) {
        List<byte[]> signedTransactions = new ArrayList<>();
        for (int i = 0; i < fileChunks.size(); i++) {
            if (i == 0) {
                signedTransactions.add(makeFileUpdateTransaction(fileChunks.get(i), validStartSeconds));
            } else {
                signedTransactions.add(makeFileAppendTransaction(fileChunks.get(i), validStartSeconds));
            }
            validStartSeconds++;
        }
        return signedTransactions;
    }

    private static byte[] makeFileUpdateTransaction(byte[] contents, long validStartSeconds) {
        return new FileUpdateTransaction()
            .setTransactionValidDuration(Duration.ofMinutes(3))
            .setFileId(FEE_SCHEDULE_FILE)
            .setContents(contents)
            .setMaxTransactionFee(100_00_000_000L)  // 10 hbars
            .setNodeAccountId(NODE_ID)
            .setTransactionId(TransactionId.withValidStart(FEE_SCHEDULE_OWNER, Instant.ofEpochSecond(validStartSeconds, 0)))
            .build(null)
            .sign(FEE_SCHEDULE_OWNER_KEY)  // both txn payer and file owner (until perms are changed to multisig)
            .toProto()
            .toByteArray();
    }

    private static byte[] makeFileAppendTransaction(byte[] contents, long validStartSeconds) {
        return new FileAppendTransaction()
            .setFileId(FEE_SCHEDULE_FILE)
            .setContents(contents)
            .setMaxTransactionFee(100_00_000_000L)  // 10 hbars
            .setNodeAccountId(NODE_ID)
            .setTransactionId(TransactionId.withValidStart(FEE_SCHEDULE_OWNER, Instant.ofEpochSecond(validStartSeconds, 0)))
            .build(null)
            .sign(FEE_SCHEDULE_OWNER_KEY)  // both txn payer and file owner (until perms are changed to multisig)
            .toProto()
            .toByteArray();
    }

    private static void writeOutput(TreeMap<Long, List<byte[]>> signedTransactions) throws Exception {
        try(FileOutputStream fout = new FileOutputStream(SIGNED_TRANSACTIONS_OUTPUT_PATH);
            ObjectOutputStream oos = new ObjectOutputStream(fout)) {
            oos.writeObject(signedTransactions);
        }
        System.out.println("Output file: " + SIGNED_TRANSACTIONS_OUTPUT_PATH);
    }
}
