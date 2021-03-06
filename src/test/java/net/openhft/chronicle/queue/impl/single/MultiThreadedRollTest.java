package net.openhft.chronicle.queue.impl.single;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.time.SetTimeProvider;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.RollingChronicleQueue;
import net.openhft.chronicle.threads.NamedThreadFactory;
import net.openhft.chronicle.wire.DocumentContext;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static net.openhft.chronicle.queue.ChronicleQueueTestBase.getTmpDir;
import static net.openhft.chronicle.queue.RollCycles.TEST_SECONDLY;

/**
 * @author Rob Austin.
 */
public class MultiThreadedRollTest {

    final ExecutorService reader = Executors.newSingleThreadExecutor(new NamedThreadFactory("reader", true));

    @After
    public void after() {
        reader.shutdown();
    }

    @Test(timeout = 10000)
    public void test() throws ExecutionException, InterruptedException {

        final SetTimeProvider timeProvider = new SetTimeProvider();
        timeProvider.currentTimeMillis(1000);
        final String path = getTmpDir() + "/backRoll.q";

        final RollingChronicleQueue wqueue = SingleChronicleQueueBuilder.binary(path)
                .timeProvider(timeProvider)
                .rollCycle(TEST_SECONDLY)
                .build();

        wqueue.acquireAppender().writeText("hello world");

        final RollingChronicleQueue rqueue = SingleChronicleQueueBuilder.binary(path)
                .timeProvider(timeProvider)
                .rollCycle(TEST_SECONDLY)
                .build();

        ExcerptTailer tailer = rqueue.createTailer();
        Future f = reader.submit(() -> {
            long index;
            do {
                try (DocumentContext documentContext = tailer.readingDocument()) {
                    System.out.println("tailer.state: " + tailer.state());
                    // index is only meaningful if present.
                    index = documentContext.index();
                    //    if (documentContext.isPresent())
                    final boolean present = documentContext.isPresent();
                    System.out.println("documentContext.isPresent=" + present
                            + (present ? ",index=" + Long.toHexString(index) : ", no index"));
                    Jvm.pause(50);
                }
            } while (index != 0x200000000L && !reader.isShutdown());

        });

        timeProvider.currentTimeMillis(2000);
        ((SingleChronicleQueueExcerpts.StoreAppender)  wqueue.acquireAppender())
        .writeEndOfCycleIfRequired();
        Jvm.pause(200);
        wqueue.acquireAppender().writeText("hello world");
        f.get();
    }


}