package dev.fourwags.sumi.inputmethod.latin;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PetalButtonIconTest {
    private SharedPreferences prefs(Map<String,Object> values) {
        return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(proxy,method,args)->{
            if(method.getName().equals("getAll"))return new HashMap<>(values);
            if(method.getName().equals("edit"))return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(editor,m,a)->{
                if(m.getName().equals("putInt")){values.put((String)a[0],a[1]);return editor;}
                if(m.getName().equals("apply"))return null;
                throw new UnsupportedOperationException(m.getName());
            });
            throw new UnsupportedOperationException(method.getName());
        });
    }
    @Test public void defaultsAndMalformedValuesChooseBright() {
        Map<String,Object> values=new HashMap<>();SharedPreferences p=prefs(values);
        assertEquals(0,PetalButtonIcon.selected(p));
        for(Object value:new Object[]{-1,3,Integer.MAX_VALUE,"bad",true}){values.put(PetalButtonIcon.PREFERENCE,value);assertEquals(0,PetalButtonIcon.selected(p));}
    }
    @Test public void choicesChangeOnlyDedicatedPreference() {
        Map<String,Object> values=new HashMap<>();values.put("petal_theme",43);values.put("petal_large_keys",true);SharedPreferences p=prefs(values);
        for(int i=0;i<3;i++){PetalButtonIcon.select(p,i);assertEquals(i,PetalButtonIcon.selected(p));assertEquals(43,values.get("petal_theme"));assertEquals(true,values.get("petal_large_keys"));assertEquals(3,values.size());}
    }
    @Test public void rejectsUnknownChoiceBeforeWriting() {
        Map<String,Object> values=new HashMap<>();SharedPreferences p=prefs(values);
        try{PetalButtonIcon.select(p,3);fail();}catch(IllegalArgumentException expected){}
        assertTrue(values.isEmpty());
    }
}
