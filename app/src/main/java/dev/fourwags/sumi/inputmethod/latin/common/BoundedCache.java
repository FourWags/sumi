package dev.fourwags.sumi.inputmethod.latin.common;
import java.util.LinkedHashMap;
import java.util.Map;
/** Small access-ordered cache; keys and values are evicted together. */
public final class BoundedCache<K,V> extends LinkedHashMap<K,V> {
    private static final long serialVersionUID=1L;
    private final int limit;
    public BoundedCache(int limit){super(16,0.75f,true);if(limit<1)throw new IllegalArgumentException("Positive cache limit required");this.limit=limit;}
    @Override protected boolean removeEldestEntry(Map.Entry<K,V> eldest){return size()>limit;}
}
