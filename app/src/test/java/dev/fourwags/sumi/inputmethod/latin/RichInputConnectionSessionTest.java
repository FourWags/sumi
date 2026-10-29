package dev.fourwags.sumi.inputmethod.latin;

import org.junit.Test;
import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import android.view.inputmethod.InputConnection;

public class RichInputConnectionSessionTest {
    private static Field field(String name) throws Exception {
        Field field = RichInputConnection.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    @Test public void endedSessionReleasesTextAndConnectionWithoutEditorCalls() throws Exception {
        RichInputConnection connection = new RichInputConnection(null);
        StringBuilder committed = (StringBuilder)field("mCommittedTextBeforeComposingText").get(connection);
        StringBuilder composing = (StringBuilder)field("mComposingText").get(connection);
        committed.append("SYNTHETIC_COMMITTED_TEXT");
        composing.append("SYNTHETIC_COMPOSING_TEXT");
        InputConnection editor = (InputConnection)Proxy.newProxyInstance(
                InputConnection.class.getClassLoader(), new Class<?>[]{InputConnection.class},
                (proxy, method, args) -> { throw new AssertionError("Ended editor must not be contacted: " + method.getName()); });
        field("mIC").set(connection, editor);
        field("mNestLevel").setInt(connection, 2);
        connection.mExpectedSelStart = 23;
        connection.mExpectedSelEnd = 27;
        assertTrue(connection.isConnected());
        connection.clearInputSession();
        assertFalse(connection.isConnected());
        assertNull(field("mIC").get(connection));
        assertEquals("", committed.toString());
        assertEquals("", composing.toString());
        assertEquals(0, committed.capacity());
        assertEquals(0, composing.capacity());
        assertEquals(-1, connection.mExpectedSelStart);
        assertEquals(-1, connection.mExpectedSelEnd);
        assertEquals(0, field("mNestLevel").getInt(connection));
        connection.clearInputSession(); // Repeated finish-view + finish callbacks are safe.
        assertFalse(connection.isConnected());
    }
    @Test public void clearingAnUnusedSessionIsSafe() {
        RichInputConnection connection = new RichInputConnection(null);
        connection.clearInputSession();
        assertFalse(connection.isConnected());
        assertEquals(-1, connection.mExpectedSelStart);
    }
}
