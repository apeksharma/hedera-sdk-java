package com.hedera.hashgraph.sdk.examples;

import com.hedera.hashgraph.proto.CurrentAndNextFeeSchedule;
import com.hedera.hashgraph.sdk.*;
import com.hedera.hashgraph.sdk.account.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.file.*;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Map;

public final class UpdateFeeSchedule {
    /************** LOCAL ***************/
//    private static final AccountId NODE_ID = AccountId.fromString("0.0.3");
//    private static final String NODE_ADDRESS = "192.168.29.162:50211";

    /************** STAGING ***************/
//    private static final AccountId NODE_ID = AccountId.fromString("0.0.3");
//    private static final String NODE_ADDRESS = "35.237.182.66:50211";

//    private static final AccountId NODE_ID = AccountId.fromString("0.0.7");
//    private static final String NODE_ADDRESS = "34.94.236.63:50211";

    /************** TESTNET ***************/
    private static final AccountId NODE_ID = AccountId.fromString("0.0.3");
    private static final String NODE_ADDRESS = "35.188.20.11:50211";

    // NOTE: On testnet, 0.0.50 has the permissions to update the feeSchedule.
    private static final AccountId OPERATOR_ID_2 = AccountId.fromString("0.0.2");
    private static final Ed25519PrivateKey OPERATOR_KEY = Ed25519PrivateKey.fromString("302e020100300506032b65700422042091132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137");
    private static final Ed25519PublicKey OPERATOR_PUBKEY = Ed25519PublicKey.fromString("302a300506032b65700321000aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92");
    private static final String filePath = "/Users/appy/Downloads/feeSchedule.txt";
    static Client client2;

    private UpdateFeeSchedule() { }

    public static void main(String[] args) throws Exception {
        client2 = new Client(Map.of(NODE_ID, NODE_ADDRESS));
        client2.setOperator(OPERATOR_ID_2, OPERATOR_KEY);

        printBalance(OPERATOR_ID_2, client2);
        printBalance(AccountId.fromString("0.0.56"), client2);

        printFeeSchedule();
//        updateFeeSchedule();
    }

    private static void updateFeeSchedule() throws Exception {
        FileId fileId = FileId.fromString("0.0.111");
        byte[] fileData = new FileInputStream(filePath).readAllBytes();
        int fileLength = fileData.length;
        int chunckSize = 5 * 1024; // 5k chunk sizes
        int index = 0;
        while (index < fileLength) {
            int len = Math.min(chunckSize, fileLength - index);
            byte[] contents = Arrays.copyOfRange(fileData, index, index + len);
            if (index == 0) {  // FileUpdate
                System.out.println("Updating file contents. Len = " + len);
                TransactionId txId = new FileUpdateTransaction()
                    .setFileId(fileId)
                    .setContents(contents)
                    .setMaxTransactionFee(100_00_000_000L)  // 10 hbars
                    .execute(client2);
                assertSuccess(txId.getReceipt(client2));
            } else {  // FileAppend
                System.out.println("Appending file contents. Len = " + len);
                TransactionId txId = new FileAppendTransaction()
                    .setFileId(fileId)
                    .setContents(contents)
                    .setMaxTransactionFee(100_00_000_000L)  // 10 hbars
                    .execute(client2);
                assertSuccess(txId.getReceipt(client2));
            }
            index = index + len;
            System.out.println("index = " + index);
        }
        byte[] newContents = new FileContentsQuery()
            .setFileId(fileId)
            .execute(client2);
        if (Arrays.compare(newContents, fileData) != 0) {
            System.out.println("File update failed. New file contents on Hedera not same as that in local file");
            System.out.println("Length of file on Hedera: " + newContents.length);
            System.exit(1);
        }
        System.exit(0);
    }

    private static void assertSuccess(TransactionReceipt receipt) {
        if (receipt.status != Status.Success) {
            System.out.println("Transaction failed");
            System.out.println(receipt.toProto());
            System.exit(1);
        }
    }

    private static void printBalance(AccountId account, Client client) throws Exception {
        System.out.println(account + ":" +
            new AccountBalanceQuery().setAccountId(account).execute(client));
    }

    private static void printFeeSchedule() throws Exception {
        FileId fileId = FileId.fromString("0.0.111");
        byte[] contents = new FileContentsQuery()
            .setFileId(fileId)
            .execute(client2);

        System.out.println("Size of contents:" + contents.length);
        CurrentAndNextFeeSchedule feeSchedule = CurrentAndNextFeeSchedule.parseFrom(contents);
        System.out.println(feeSchedule);
    }

    private static FileId createNewFile(String contents, Client client) throws Exception {
        TransactionId txId = new FileCreateTransaction()
            // Use the same key as the operator to "own" this file
            .addKey(OPERATOR_PUBKEY)
            .setContents(contents.getBytes())
            .setMaxTransactionFee(800000000)
            .execute(client);
        TransactionReceipt receipt = txId.getReceipt(client2);
        assertSuccess(receipt);
        return receipt.getFileId();
    }
}
