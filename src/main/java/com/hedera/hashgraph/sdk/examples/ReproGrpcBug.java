package com.hedera.hashgraph.sdk.examples;

import com.google.common.base.Stopwatch;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.mirror.MirrorClient;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.hashgraph.sdk.mirror.MirrorSubscriptionHandle;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ReproGrpcBug {
    public static final String MIRROR_NODE_GRPC = "127.0.0.1:5600";

    private static final int NUM_CLIENTS = 100;
    private static final CountDownLatch fullWorkloadLatch = new CountDownLatch(NUM_CLIENTS);
    private static final int NUM_MESSAGES_PER_CLIENT = 10_000;

    public static void main(String[] args) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        for (int i = 0; i < NUM_CLIENTS; i++) {
//            new Thread(new BugReproWorker(i)::run).start();
            new ScaleTestWorker(i).run();
        }
        fullWorkloadLatch.await();
        System.out.println("Finished full workload in " + stopwatch.elapsed(TimeUnit.SECONDS) + "s");
    }

    static class BugReproWorker extends Worker {
        public BugReproWorker(int workerId) {
            super(workerId);
        }
        public void run() {
            MirrorSubscriptionHandle subscription = subscribe(() -> {});
            try {
                Thread.sleep(2000);
                subscription.unsubscribe();
            } catch (InterruptedException e) {}
            System.out.println("Cancelled worker: " + workerId);
            fullWorkloadLatch.countDown();
        }
    }

    static class ScaleTestWorker extends Worker {
        public ScaleTestWorker(int workerId) {
            super(workerId);
        }
        public void run() {
            subscribe(() -> {});
        }
    }


    static abstract class Worker {
        protected final int workerId;

        abstract void run();

        Worker(int workerId) {
            this.workerId = workerId;
        }

        protected MirrorSubscriptionHandle subscribe(Runnable onFinish) {
            MirrorClient client = new MirrorClient(MIRROR_NODE_GRPC);
            MirrorConsensusTopicQuery query = new MirrorConsensusTopicQuery()
                .setTopicId(ConsensusTopicId.fromString("0.0.147228"))
                .setStartTime(Instant.EPOCH)
                .setEndTime(Instant.now())
                .setLimit(NUM_MESSAGES_PER_CLIENT);
            AtomicInteger count = new AtomicInteger();
            Stopwatch stopwatch = Stopwatch.createStarted();
            return query.subscribe(
                client,
                msg -> {
                    if (count.get() % (NUM_MESSAGES_PER_CLIENT / 4) == 0) { // log progress every 10%
                        System.out.println("Worker: " + workerId + " Count: " + count.get());
                    }
                    count.incrementAndGet();
                    if (count.get() == NUM_MESSAGES_PER_CLIENT) {
                        System.out.println("Finished worker " + workerId + " in " + stopwatch.elapsed(TimeUnit.SECONDS) + "s");
                        onFinish.run();
                        fullWorkloadLatch.countDown();
                    }
                },
                System.out::println
            );
        }
    }
}
