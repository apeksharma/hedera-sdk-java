package com.hedera.hashgraph.sdk.examples;

import com.hedera.hashgraph.proto.Timestamp;
import com.hedera.hashgraph.proto.mirror.ConsensusTopicQuery;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicInfo;

public class ReproGrpcBug {
    public static int main(String[] args) {
        ConsensusTopicInfo
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
            .setTopicID(Topic)
            .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())

    }
}
