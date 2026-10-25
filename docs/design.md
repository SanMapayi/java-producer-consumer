# Concurrency design

## Invariants

1. Queue size stays between zero and the configured positive capacity.
2. All queue and closed-state access occurs under the same lock.
3. A producer cannot append after close.
4. Close does not discard queued work.
5. Every blocking operation rechecks its predicate after waking.

## Lifecycle

~~~text
open + full  -> producer waits
open + empty -> consumer waits
close        -> wake every waiter and reject new submissions
closed       -> consumers drain remaining items
closed+empty -> consumers receive null and finish
~~~

The demo owns its executor and closes the queue in a finally block. Worker failures propagate through Future.get; bounded waits detect failures to finish. Interruption is propagated by the queue and restored when a Runnable cannot rethrow it directly.

## Tradeoffs and next steps

This implementation is intended to make synchronization visible. In production Java applications, prefer a standard BlockingQueue unless custom lifecycle semantics justify maintaining a queue. A fair lock can reduce starvation but has throughput costs; a monitor makes no fairness promise.

The checks cover representative interleavings and lifecycle behavior, not a mathematical proof of every possible schedule. There is no persistence or crash recovery. Useful extensions are cancellation of individual orders, processing-failure handling, and JMH benchmarks before making performance claims.