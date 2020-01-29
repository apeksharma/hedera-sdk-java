package com.hedera.hashgraph.sdk.examples;

import com.hedera.hashgraph.proto.CurrentAndNextFeeSchedule;
import com.hedera.hashgraph.proto.ExchangeRate;
import com.hedera.hashgraph.proto.ExchangeRateSet;
import com.hedera.hashgraph.sdk.*;
import com.hedera.hashgraph.sdk.account.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.file.*;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Map;

public final class UpdateFeeSchedule {
    /************** LOCAL ***************/
//    private static final AccountId NODE_ID = AccountId.fromString("0.0.3");
//    private static final String NODE_ADDRESS = "172.31.99.179:50211";

    /************** STAGING ***************/
//    private static final AccountId NODE_ID = AccountId.fromString("0.0.3");
//    private static final String NODE_ADDRESS = "35.237.182.66:50211";

//    private static final AccountId NODE_ID = AccountId.fromString("0.0.7");
//    private static final String NODE_ADDRESS = "34.94.236.63:50211";

    /************** TESTNET ***************/
    private static final AccountId NODE_ID = AccountId.fromString("0.0.3");
    private static final String NODE_ADDRESS = "35.188.20.11:50211";

    private static final FileId FEE_SCHEDULE_FILE = FileId.fromString("0.0.111");
    private static final FileId EXCHANGE_RATE_FILE = FileId.fromString("0.0.112");
    private static final AccountId FEE_SCHEDULE_OWNER_ACCOUNT = AccountId.fromString("0.0.2");
    private static final Ed25519PrivateKey FEE_SCHEDULE_OWNER_KEY = Ed25519PrivateKey.fromString("302e020100300506032b65700422042091132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137");
    private static final String filePath = "/Users/appy/Downloads/feeSchedule.txt";
    static Client client;

    public static void main(String[] args) throws Exception {
        client = new Client(Map.of(NODE_ID, NODE_ADDRESS));
        client.setOperator(FEE_SCHEDULE_OWNER_ACCOUNT, FEE_SCHEDULE_OWNER_KEY);

        printBalance(FEE_SCHEDULE_OWNER_ACCOUNT, client);
        printBalance(AccountId.fromString("0.0.56"), client);

        //***** Fee schedule ops *********
//        printFeeSchedule();
        updateFeeSchedule();

        //***** Exchange rate ops *********
//        ExchangeRateSet exchangeRateSet = getExchangeRate();
//        updateExchangeRate(exchangeRateSet);
    }

    private static void updateFeeSchedule() throws Exception {
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
                    .setFileId(FEE_SCHEDULE_FILE)
                    .setContents(contents)
                    .setMaxTransactionFee(100_00_000_000L)  // 10 hbars
                    .execute(client);
                assertSuccess(txId.getReceipt(client));
            } else {  // FileAppend
                System.out.println("Appending file contents. Len = " + len);
                TransactionId txId = new FileAppendTransaction()
                    .setFileId(FEE_SCHEDULE_FILE)
                    .setContents(contents)
                    .setMaxTransactionFee(100_00_000_000L)  // 10 hbars
                    .execute(client);
                assertSuccess(txId.getReceipt(client));
            }
            index = index + len;
            System.out.println("index = " + index);
        }
        byte[] newContents = new FileContentsQuery()
            .setFileId(FEE_SCHEDULE_FILE)
            .execute(client);
        if (Arrays.compare(newContents, fileData) != 0) {
            System.out.println("File update failed. New file contents on Hedera not same as that in local file");
            System.out.println("Length of file on Hedera: " + newContents.length);
            System.exit(1);
        }
        System.exit(0);
    }

    private static void updateExchangeRate(ExchangeRateSet current) throws Exception {
        ExchangeRateSet newExchangeRate = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                .setCentEquiv(4)
                .setHbarEquiv(1)
                .setExpirationTime(current.getCurrentRate().getExpirationTime())
                .build())
            .setNextRate(ExchangeRate.newBuilder()
                .setCentEquiv(4)
                .setHbarEquiv(1)
                .setExpirationTime(current.getNextRate().getExpirationTime())
                .build())
            .build();
        TransactionId txId = new FileUpdateTransaction()
            .setFileId(EXCHANGE_RATE_FILE)
            .setContents(newExchangeRate.toByteArray())
            .setMaxTransactionFee(100_00_000_000L)  // 10 hbars
            .execute(client);
        assertSuccess(txId.getReceipt(client));
        ExchangeRateSet verifySet = getExchangeRate();
        if (Arrays.compare(verifySet.toByteArray(), newExchangeRate.toByteArray()) != 0) {
            System.out.println("File update failed. New file contents on Hedera not same as that in local file");
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
        byte[] contents = new FileContentsQuery()
            .setFileId(FEE_SCHEDULE_FILE)
            .execute(client);
        System.out.println("Size of contents:" + contents.length);
        CurrentAndNextFeeSchedule feeSchedule = CurrentAndNextFeeSchedule.parseFrom(contents);
        System.out.println(feeSchedule);
    }

    private static ExchangeRateSet getExchangeRate() throws Exception {
        byte[] contents = new FileContentsQuery()
            .setFileId(EXCHANGE_RATE_FILE)
            .execute(client);
        System.out.println("*************");
        System.out.println("EXCHANGE RATE (size = " + contents.length + ")");
        ExchangeRateSet exchangeRateSet = ExchangeRateSet.parseFrom(contents);
        System.out.println(exchangeRateSet);
        return exchangeRateSet;
    }
}
