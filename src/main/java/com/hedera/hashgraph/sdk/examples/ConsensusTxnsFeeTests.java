package com.hedera.hashgraph.sdk.examples;

import com.hedera.hashgraph.proto.ExchangeRate;
import com.hedera.hashgraph.sdk.*;
import com.hedera.hashgraph.sdk.account.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.CryptoTransferTransaction;
import com.hedera.hashgraph.sdk.consensus.*;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ConsensusTxnsFeeTests {
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

    private static final AccountId ACCOUNT_2 = AccountId.fromString("0.0.2");
    private static final Ed25519PrivateKey ACCOUNT_2_KEY = Ed25519PrivateKey.fromString("302e020100300506032b65700422042091132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137");
    private static final Ed25519PublicKey OPERATOR_PUBKEY = Ed25519PublicKey.fromString("302a300506032b65700321000aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92");
    static Client client2;

    private static final String memoSize100 = "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";
    private static final Ed25519PrivateKey key1 = Ed25519PrivateKey.fromString("302e020100300506032b657004220420db484b828e64b2d8f12ce3c0a0e93a0b8cce7af1bb8f39c97732394482538e10");
    private static final Ed25519PrivateKey key2 = Ed25519PrivateKey.fromString("5f66a51931e8c99089472e0d70516b6272b94dd772b967f8221e1077f966dbda2b60cf7ee8cf10ecd5a076bffad9a7c7b97df370ad758c0f1dd4ef738e04ceb6");
    private static AccountId autoRenewAccount;
    private static final Ed25519PrivateKey autoRenewAccountKey = Ed25519PrivateKey.fromString("c284c25b3a1458b59423bc289e83703b125c8eefec4d5aa1b393c2beb9f2bae66188a344ba75c43918ab12fa2ea4a92960eca029a2320d8c6a1c3b94e06c9985");

    private static final List<FeeCompareStat> feeComparisions = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        client2 = new Client(Map.of(NODE_ID, NODE_ADDRESS));
        client2.setOperator(ACCOUNT_2, ACCOUNT_2_KEY);

        setup();
        feeForTopicCreate();
        feeForMessageSubmit();
        feeForTopicUpdate();
        feeForTopicDelete();
        feeForGetTopicInfo();
        printFeeComparisons();
    }

    private static void setup() throws Exception {
        // Create crypto account to be used as autoRenewAccount
        TransactionId txId = new AccountCreateTransaction()
            .setKey(autoRenewAccountKey.publicKey)
            .build(client2)
            .sign(autoRenewAccountKey)
            .execute(client2);
        assertSuccess(txId);
        autoRenewAccount = txId.getReceipt(client2).getAccountId();
        System.out.println("AutoRenewAccount = " + autoRenewAccount);

        long amount = 1000_00_000_000L;  // 1000 hbars
        txId = new CryptoTransferTransaction()
            .addSender(ACCOUNT_2, amount)
            .addRecipient(autoRenewAccount, amount)
            .execute(client2);
        assertSuccess(txId);
        System.out.println("Sent " + amount + " tinyhbars to autoRenewAccount");
    }

    private static void feeForTopicCreate() throws Exception {
        TransactionId txId = new ConsensusTopicCreateTransaction().setAutoRenewPeriod(Duration.ofHours(2160)).execute(client2);
        compareFee("CreateTopic Base (min for all params)",
            txId, 0.00982,
            "sigs=1, keys=0, topicMemoLength=0, autoRenewAccount=no", 0);

        txId = new ConsensusTopicCreateTransaction()
            .setAutoRenewPeriod(Duration.ofHours(2160))
            .setTopicMemo(memoSize100)
            .execute(client2);
        compareFee("CreateTopic max topic memo length",
            txId, 0.01039,
            "sigs=1, keys=0, topicMemoLength=100, autoRenewAccount=no", 0);

        txId = new ConsensusTopicCreateTransaction()
            .setAutoRenewPeriod(Duration.ofHours(2160))
            .setAdminKey(key1.publicKey)
            .setSubmitKey(key2.publicKey)
            .build(client2)
            .sign(key1)
            .execute(client2);
        compareFee("CreateTopic max keys",
            txId, 0.01606,
            "sigs=2, keys=2, topicMemoLength=0, autoRenewAccount=no", 0);

        txId = new ConsensusTopicCreateTransaction()
            .setAutoRenewPeriod(Duration.ofHours(2160))
            .setAdminKey(key1.publicKey)
            .setAutoRenewAccountId(autoRenewAccount)
            .build(client2)
            .sign(key1)
            .sign(autoRenewAccountKey)
            .execute(client2);
        compareFee("CreateTopic with AutoRenewAccount",
            txId, 0.0219,
            "sigs=3, keys=1(admin), topicMemoLength=0, autoRenewAccount=yes", 0);
        System.out.println("Finished TopicCreate");
    }

    private static void feeForMessageSubmit() throws Exception {
        ConsensusTopicId topicId = getNewTopic(key1);

        TransactionId txId = new ConsensusMessageSubmitTransaction()
            .setTopicId(topicId)
            .setMessage("1")
            .execute(client2);
        compareFee("SubmitMessage message length = 1", txId, 0.0001, "", 0);

        byte[] maxMessage = new byte[5000];
        Arrays.fill(maxMessage, (byte) '!');
        txId = new ConsensusMessageSubmitTransaction()
            .setTopicId(topicId)
            .setMessage(maxMessage)
            .execute(client2);
        compareFee("SubmitMessage message length = 5000", txId, 0.00022, "", 0);
        System.out.println("Finished SubmitMessage");
    }

    private static void feeForTopicUpdate() throws Exception {
        ConsensusTopicId topicId = getNewTopic(key1);

        TransactionId txId = new ConsensusTopicUpdateTransaction()
            .setTopicId(topicId)
            .setAdminKey(key2.publicKey)
            .build(client2)
            .sign(key1)
            .sign(key2)
            .execute(client2);
        compareFee("TopicUpdate change admin key", txId, 0.00048, "sigs=3 (payer+old admin+new admin)", 0);

        topicId = getNewTopic(ACCOUNT_2_KEY);
        txId = new ConsensusTopicUpdateTransaction()
            .setTopicId(topicId)
            .setSubmitKey(key2.publicKey)
            .execute(client2);
        compareFee("TopicUpdate set submit key (1sig)", txId, 0.00022, "sigs=1 (payer==admin)", 0);

        topicId = getNewTopic(key1);
        txId = new ConsensusTopicUpdateTransaction()
            .setTopicId(topicId)
            .setSubmitKey(key2.publicKey)
            .build(client2)
            .sign(key1)
            .execute(client2);
        compareFee("TopicUpdate set submit key (2sig)", txId, 0.00035, "sigs=2 (payer+admin)", 0);

        topicId = getNewTopic(key1);
        txId = new ConsensusTopicUpdateTransaction()
            .setTopicId(topicId)
            .setAutoRenewAccountId(autoRenewAccount)
            .build(client2)
            .sign(key1)
            .sign(autoRenewAccountKey)
            .execute(client2);
        compareFee("TopicUpdate set auto renew account", txId, 0.00048, "sigs=3 (payer+admin+autoRenewAccount)", 0);

        topicId = getNewTopic(ACCOUNT_2_KEY);
        txId = new ConsensusTopicUpdateTransaction()
            .setTopicId(topicId)
            .setAutoRenewPeriod(Duration.ofHours(2161))
            .execute(client2);
        compareFee("TopicUpdate update autoRenewPeriod", txId, 0.00022, "sigs=1", 0);

        topicId = getNewTopic(ACCOUNT_2_KEY);
        txId = new ConsensusTopicUpdateTransaction()
            .setTopicId(topicId)
            .setTopicMemo(memoSize100)
            .execute(client2);
        compareFee("TopicUpdate set memo", txId, 0.00022, "sigs=1", 0);
        System.out.println("Finished TopicUpdate");
    }

    private static ConsensusTopicId getNewTopic(Ed25519PrivateKey adminKey) throws Exception {
        ConsensusTopicCreateTransaction txn = new ConsensusTopicCreateTransaction()
            .setAutoRenewPeriod(Duration.ofHours(2160))
            .setAdminKey(adminKey.publicKey);
        if (adminKey != ACCOUNT_2_KEY) { // double signing with same key not allowed
            txn.build(client2)
                .sign(adminKey);
        }
        TransactionId txId = txn.execute(client2);
        assertSuccess(txId);
        return txId.getReceipt(client2).getConsensusTopicId();
    }

    private static void feeForGetTopicInfo() throws Exception {
        ConsensusTopicId topicId = getNewTopic(key1);

        long queryCostEstimate = new ConsensusTopicInfoQuery()
            .setTopicId(topicId)
            .getCost(client2);
        Transaction paymentTransaction = new CryptoTransferTransaction()
            .setNodeAccountId(NODE_ID)
            .setTransactionId(new TransactionId(ACCOUNT_2))
            .addSender(ACCOUNT_2, queryCostEstimate)
            .addRecipient(NODE_ID, queryCostEstimate)
            .setMaxTransactionFee(10_000_000)
            .build(null)
            .sign(ACCOUNT_2_KEY);
        new ConsensusTopicInfoQuery()
            .setTopicId(topicId)
            .setPaymentTransaction(paymentTransaction)
            .execute(client2);
        assertSuccess(paymentTransaction.id);
        compareFee("GetTopicInfo", paymentTransaction.id, 0.0001, "", queryCostEstimate);
    }

    private static void feeForTopicDelete() throws Exception {
        ConsensusTopicId topicId = getNewTopic(key1);

        TransactionId txId = new ConsensusTopicDeleteTransaction()
            .setTopicId(topicId)
            .build(client2)
            .sign(key1)
            .execute(client2);
        assertSuccess(txId);
        compareFee("TopicDelete", txId, 0.008, "sigs=2", 0);
        System.out.println("Finished TopicDelete");
    }

    private static void compareFee(
        String name, TransactionId txId, double dollarsExpected, String description, long queryCost) throws Exception {
        assertSuccess(txId);
        TransactionRecord record = txId.getRecord(client2);
        double hbarsActual = (record.transactionFee + queryCost) * 1.0 / 100_000_000L;
        ExchangeRate exchangeRate = record.receipt.toProto().getExchangeRate().getCurrentRate();
        double dollarsActual = (hbarsActual * record.receipt.toProto().getExchangeRate().getCurrentRate().getCentEquiv())
            / (100 * exchangeRate.getHbarEquiv());
        feeComparisions.add(
            new FeeCompareStat(name, description, dollarsActual, dollarsExpected));
    }

    private static void assertSuccess(TransactionId txId) throws Exception {
        TransactionReceipt receipt = txId.getReceipt(client2);
        if (receipt.status != Status.Success) {
            System.out.println("Transaction failed");
            System.out.println(receipt.toProto());
            System.exit(1);
        }
    }

    private static void printFeeComparisons() {
        System.out.format("%40s|%10s|%11s|%10s|%80s\n", "name", "$(actual)",  "$(expected)", "$(diff)", "description");
        for (FeeCompareStat stat : feeComparisions) {
            System.out.format("%40s|   %.5f|    %.5f|   %.5f|%s\n",
                stat.name, stat.dollarsActual, stat.dollarsExpected, (stat.dollarsActual - stat.dollarsExpected), stat.description);
        }
    }

    private static class FeeCompareStat {
        String name;
        String description;
        double dollarsActual;
        double dollarsExpected;

        // No point comparing hbars since that value can differ a lot based on centEquiv of calc/staging
        public FeeCompareStat(String name, String description, double dollarsActual, double dollarsExpected) {
            this.name = name;
            this.description = description;
            this.dollarsActual = truncateTo5digits(dollarsActual);
            this.dollarsExpected = dollarsExpected;
        }
    }

    private static double truncateTo5digits(double number) {
        return ((long) (number * 100000)) * 1.0 / 100000;
    }
}
