package com.hedera.hashgraph.sdk.examples;

import com.hedera.hashgraph.proto.TransactionBody;
import com.hedera.hashgraph.sdk.*;
import com.hedera.hashgraph.sdk.account.AccountId;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.*;

public class FeeScheduleUploader {
    /************** CONFIGURABLE PARAMS *****************/
    private static final String SIGNED_TRANSACTIONS_OUTPUT_PATH = "/Users/appy/Downloads/signedFeeScheduleTransactions";
    private static final AccountId NODE_ID = AccountId.fromString("0.0.3");  // Should be same as that used during signing
    private static final String NODE_ADDRESS = "35.188.20.11:50211";  // testnet
//    private static final String NODE_ADDRESS = "35.237.182.66:50211";  // staging
//    private static final String NODE_ADDRESS = "127.0.0.1:50211";

    private static final long MILLIS_PER_SEC = 1000L;

    public static void main(String[] args) throws Exception {
        Client client = new Client(Map.of(NODE_ID, NODE_ADDRESS));

        TreeMap<Long, List<byte[]>> signedTransactions = readSignedTransactions();
        long utcNow = (new Date().getTime()) / MILLIS_PER_SEC;
        Map.Entry<Long, List<byte[]>> entry = signedTransactions.floorEntry(utcNow);
        System.out.println("Running transactions with validStartTime = " + entry.getKey());

        for (byte[] txnBytes : entry.getValue()) {
            TransactionId txId = Transaction.fromBytes(txnBytes).execute(client);
            assertSuccess(txId.getReceipt(client));
            System.out.println("Processed transaction " + txId);
        }
        System.out.println("Successfully executed all transactions");
    }

    private static TreeMap<Long, List<byte[]>> readSignedTransactions() throws Exception {
        try(FileInputStream fin = new FileInputStream(SIGNED_TRANSACTIONS_OUTPUT_PATH);
            ObjectInputStream ois = new ObjectInputStream(fin)) {
            return (TreeMap<Long, List<byte[]>>)ois.readObject();
        }
    }

    private static void assertSuccess(TransactionReceipt receipt) {
        if (receipt.status != Status.Success) {
            System.out.println("Transaction failed");
            System.out.println(receipt.toProto());
            System.exit(1);
        }
    }
}
