package com.hedera.hashgraph.sdk;

import com.hedera.hashgraph.sdk.contract.ContractFunctionResult;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public final class TransactionRecord {
    /** The ID of the transaction this record represents */
    public final TransactionId transactionId;

    /** The hash of the Transaction that executed */
    public final byte[] transactionHash;

    /** The actual transaction fee charged, unless there were insufficient funds in the operator account */
    public final long transactionFee;

    /** The consensus timestamp */
    @Nullable
    public final Instant consensusTimestamp;

    /** The memo that was submitted as part of the transaction (max 100 bytes) */
    @Nullable
    public final String transactionMemo;

    /**
     * The status (reach consensus, or failed, or is unknown), and the ID of any
     * new account/file/instance created
     */
    public final TransactionReceipt receipt;

    /**
     * All Hbar transfers as a result of this transaction, such as fees, or transfers performed by
     * the transaction, or by a smart contract it calls, or by the creation of threshold
     * records that it triggers.
     */
    public final List<Transfer> transfers;

    private final com.hedera.hashgraph.proto.TransactionRecord inner;

    @Internal
    public TransactionRecord(com.hedera.hashgraph.proto.TransactionRecord inner) {
        this.inner = inner;

        transactionId = new TransactionId(inner.getTransactionIDOrBuilder());
        transactionFee = inner.getTransactionFee();
        receipt = new TransactionReceipt(inner.getReceipt());
        transactionHash = inner.getTransactionHash().toByteArray();
        consensusTimestamp = inner.hasConsensusTimestamp() ? TimestampHelper.timestampTo(inner.getConsensusTimestamp()) : null;

        String memo = inner.getMemo();
        this.transactionMemo = memo.isEmpty() ? null : memo;

        this.transfers = inner.hasTransferList()
            ? inner.getTransferList()
            .getAccountAmountsList()
            .stream()
            .map(com.hedera.hashgraph.sdk.Transfer::new)
            .collect(Collectors.toList())
            : Collections.emptyList();
    }

    /**
     * Record of the value returned by the smart contract function (if it completed and didn't fail)
     * from {@link com.hedera.hashgraph.sdk.contract.ContractExecuteTransaction}.
     */
    public ContractFunctionResult getContractExecuteResult() {
        if (!inner.hasContractCallResult()) {
            throw new IllegalStateException("record does not contain a contract execute result");
        }

        return new ContractFunctionResult(inner.getContractCallResultOrBuilder());
    }

    /**
     * Record of the value returned by the smart contract constructor (if it completed and didn't fail)
     * from {@link com.hedera.hashgraph.sdk.contract.ContractCreateTransaction}.
     */
    public ContractFunctionResult getContractCreateResult() {
        if (!inner.hasContractCreateResult()) {
            throw new IllegalStateException("record does not contain a contract create result");
        }

        return new ContractFunctionResult(inner.getContractCreateResultOrBuilder());
    }
}
