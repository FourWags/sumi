package dev.fourwags.sumi.inputmethod.latin;
import dev.fourwags.sumi.inputmethod.latin.common.BoundedCache;
import org.junit.Test;
import static org.junit.Assert.*;
public class BoundedCacheTest {
 @Test public void evictionBoundsKeysAndValues(){BoundedCache<Integer,String> c=new BoundedCache<>(32);for(int i=0;i<1000;i++)c.put(i,"synthetic");assertEquals(32,c.size());assertFalse(c.containsKey(967));assertTrue(c.containsKey(968));}
 @Test public void recentlyUsedEntrySurvives(){BoundedCache<Integer,String> c=new BoundedCache<>(2);c.put(1,"a");c.put(2,"b");assertEquals("a",c.get(1));c.put(3,"c");assertTrue(c.containsKey(1));assertFalse(c.containsKey(2));c.clear();assertTrue(c.isEmpty());}
 @Test(expected=IllegalArgumentException.class) public void zeroLimitRejected(){new BoundedCache<>(0);}
}
