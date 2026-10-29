package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.inputmethod.latin.utils.AsyncResultHolder;
import org.junit.Test;
import static org.junit.Assert.*;

public class AsyncResultHolderTest {
    @Test public void interruptedWaitPreservesCancellation() {
        AsyncResultHolder<String> holder = new AsyncResultHolder<>("Synthetic");
        Thread.currentThread().interrupt();
        try {
            try {
                assertEquals("fallback", holder.get("fallback", 60000));
            } finally {
                assertTrue("Interrupted wait must preserve cancellation", Thread.currentThread().isInterrupted());
            }
        } finally {
            Thread.interrupted(); // Do not leak the synthetic interrupt to the test worker.
        }
    }
    @Test public void firstResultWinsIncludingNull() {
        AsyncResultHolder<String> holder = new AsyncResultHolder<>("Synthetic");
        holder.set(null);
        holder.set("late");
        assertNull(holder.get("fallback", 0));
    }
    @Test public void noResultReturnsFallback() {
        assertEquals("fallback", new AsyncResultHolder<String>("Synthetic").get("fallback", 0));
    }
}
