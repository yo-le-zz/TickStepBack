package dev.yolezz.tickstepback.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A bounded FIFO buffer with a fixed capacity. When a new element is pushed
 * and the buffer is full, the oldest element is evicted.
 *
 * This class has zero dependency on Bukkit/Paper so it can be unit tested
 * on a plain JVM without spinning up a server.
 *
 * Not thread-safe: TickStepBack only ever touches this from the main
 * server thread (see TickTracker), so no synchronization is used here.
 *
 * @param <T> element type
 */
public final class RingBuffer<T> implements Iterable<T> {

    private final Deque<T> buffer = new ArrayDeque<>();
    private int capacity;
    private long evictedCount = 0;

    public RingBuffer(int capacity) {
        setCapacity(capacity);
    }

    public void setCapacity(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, got " + capacity);
        }
        this.capacity = capacity;
        while (buffer.size() > capacity) {
            buffer.pollFirst();
            evictedCount++;
        }
    }

    public int capacity() {
        return capacity;
    }

    /**
     * Pushes an element at the newest end of the buffer. If the buffer was
     * already at capacity, the oldest element is silently evicted and
     * returned; otherwise returns null.
     */
    public T pushNewest(T element) {
        T evicted = null;
        if (buffer.size() >= capacity) {
            evicted = buffer.pollFirst();
            evictedCount++;
        }
        buffer.addLast(element);
        return evicted;
    }

    /**
     * Removes and returns the newest element, or null if empty.
     * Used when undoing ticks: the most recent tick is popped first.
     */
    public T popNewest() {
        return buffer.pollLast();
    }

    public T peekNewest() {
        return buffer.peekLast();
    }

    public T peekOldest() {
        return buffer.peekFirst();
    }

    public int size() {
        return buffer.size();
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public long evictedCount() {
        return evictedCount;
    }

    public void clear() {
        buffer.clear();
    }

    /**
     * Iterates from newest to oldest (the order rollback consumes elements in).
     */
    @Override
    public Iterator<T> iterator() {
        Iterator<T> descending = buffer.descendingIterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return descending.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return descending.next();
            }
        };
    }
}
