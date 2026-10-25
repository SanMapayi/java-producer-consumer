import java.util.*;
import java.util.concurrent.*;

/** Dependency-free regression checks with deadlines and nonzero failure exits. */
public class ConcurrencyChecks {
    private static Order item(int id) { return new Order(id, "Running shoes", 1); }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        try { new ShoeWareHouse(0); throw new AssertionError("Zero capacity accepted"); }
        catch (IllegalArgumentException expected) { }
        ShoeWareHouse fifo = new ShoeWareHouse(2);
        fifo.receiveOrder(item(1)); fifo.receiveOrder(item(2)); fifo.close();
        check(fifo.fulfillOrder().id() == 1, "FIFO order changed");
        check(fifo.fulfillOrder().id() == 2, "Pending item lost on close");
        check(fifo.fulfillOrder() == null, "Closed queue must terminate");
        try { fifo.receiveOrder(item(3)); throw new AssertionError("Accepted work after close"); }
        catch (IllegalStateException expected) { }
        ShoeWareHouse queue = new ShoeWareHouse(2);
        ExecutorService workers = Executors.newFixedThreadPool(7);
        Set<Integer> received = ConcurrentHashMap.newKeySet();
        List<Future<?>> consumers = new ArrayList<>();
        List<Future<?>> producers = new ArrayList<>();
        try {
            for (int c = 0; c < 4; c++) consumers.add(workers.submit(() -> {
                try {
                    Order next;
                    while ((next = queue.fulfillOrder()) != null)
                        check(received.add(next.id()), "Duplicate delivery");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }));
            for (int p = 0; p < 3; p++) {
                final int offset = p * 100;
                producers.add(workers.submit(() -> {
                    try { for (int i = 1; i <= 100; i++) queue.receiveOrder(item(offset + i)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                }));
            }
            for (Future<?> producer : producers) producer.get(10, TimeUnit.SECONDS);
            queue.close();
            for (Future<?> consumer : consumers) consumer.get(10, TimeUnit.SECONDS);
            check(received.size() == 300, "Missing deliveries");
            for (int id = 1; id <= 300; id++) check(received.contains(id), "Missing ID " + id);
        } finally {
            queue.close(); workers.shutdownNow();
            check(workers.awaitTermination(5, TimeUnit.SECONDS), "Worker leak");
        }
        checkWaiting(false, false); checkWaiting(true, false);
        checkWaiting(false, true); checkWaiting(true, true);
        System.out.println("PASS: FIFO, 300 concurrent deliveries, capacity, drain, interruption, and shutdown");
    }
    private static void checkWaiting(boolean producer, boolean close) throws Exception {
        ShoeWareHouse queue = new ShoeWareHouse(1);
        if (producer) queue.receiveOrder(item(1));
        CountDownLatch finished = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                if (producer) queue.receiveOrder(item(2));
                else if (queue.fulfillOrder() == null && close) finished.countDown();
            } catch (InterruptedException expected) { if (!close) finished.countDown(); }
            catch (IllegalStateException expected) { if (close && producer) finished.countDown(); }
        });
        worker.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (worker.getState() != Thread.State.WAITING && System.nanoTime() < deadline) Thread.yield();
            check(worker.getState() == Thread.State.WAITING, "Worker did not block");
            if (close) queue.close(); else worker.interrupt();
            check(finished.await(3, TimeUnit.SECONDS), "Blocked worker was not released");
        } finally { queue.close(); worker.interrupt(); worker.join(3000); }
        check(!worker.isAlive(), "Worker leaked");
    }
}