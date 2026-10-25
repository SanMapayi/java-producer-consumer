import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Finite demo with bounded memory, four consumers, and explicit shutdown. */
public class Main {
    public static void main(String[] args) throws Exception {
        ShoeWareHouse warehouse = new ShoeWareHouse(5);
        ExecutorService workers = Executors.newFixedThreadPool(5);
        AtomicInteger completed = new AtomicInteger();
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int c = 0; c < 4; c++) tasks.add(workers.submit(() -> {
                try {
                    Order item;
                    while ((item = warehouse.fulfillOrder()) != null) {
                        System.out.println(Thread.currentThread().getName() + " fulfilled " + item);
                        completed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Consumer interrupted", e);
                }
            }));
            tasks.add(workers.submit(() -> {
                try {
                    for (int id = 1; id <= 20; id++) warehouse.receiveOrder(new Order(id, "Running shoes", 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Producer interrupted", e);
                } finally { warehouse.close(); }
            }));
            for (Future<?> task : tasks) task.get(10, TimeUnit.SECONDS);
            if (completed.get() != 20) throw new IllegalStateException("Orders were lost");
            System.out.println("Completed " + completed.get() + " orders; all workers finished.");
        } finally {
            warehouse.close();
            workers.shutdownNow();
            if (!workers.awaitTermination(5, TimeUnit.SECONDS))
                throw new IllegalStateException("Workers did not stop");
        }
    }
}