import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

/** Bounded FIFO monitor: closing rejects new work and permits draining pending orders. */
public class ShoeWareHouse implements AutoCloseable {
    public static final String[] orderType = {"Running shoes", "Hiking shoes", "Work shoes"};
    private final Queue<Order> orders = new ArrayDeque<>();
    private final int capacity;
    private boolean closed;

    public ShoeWareHouse() { this(10); }
    public ShoeWareHouse(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
    }
    public synchronized void receiveOrder(Order order) throws InterruptedException {
        Objects.requireNonNull(order, "order");
        while (orders.size() >= capacity && !closed) wait();
        if (closed) throw new IllegalStateException("Warehouse is closed");
        orders.add(order);
        notifyAll();
    }
    /** Returns null only after closing and draining the queue. */
    public synchronized Order fulfillOrder() throws InterruptedException {
        while (orders.isEmpty() && !closed) wait();
        Order order = orders.poll();
        notifyAll();
        return order;
    }
    @Override
    public synchronized void close() {
        closed = true;
        notifyAll();
    }
}