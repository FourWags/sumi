package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import android.widget.*;
import android.content.SharedPreferences;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;
public class PetalHeightTest {
 @Rule public ActivityTestRule<PetalHeightActivity> rule=new ActivityTestRule<>(PetalHeightActivity.class);
 @Test public void independentSlidersPreviewPersistenceAndReset(){
  PetalHeightActivity a=rule.getActivity();SharedPreferences p=DeviceProtectedUtils.getSharedPreferences(a);
  java.util.Map<String,?> before=new java.util.HashMap<>(p.getAll());String[] keys={SumiKeyboardHeight.PORTRAIT,SumiKeyboardHeight.LANDSCAPE};
  for(boolean large:new boolean[]{false,true}){
   InstrumentationRegistry.getInstrumentation().runOnMainSync(()->p.edit().putBoolean("petal_large_keys",large).remove(keys[0]).remove(keys[1]).apply());
   InstrumentationRegistry.getInstrumentation().waitForIdleSync();
   for(int i=0;i<2;i++){
    String key=keys[i],other=keys[1-i];SeekBar slider=a.findViewById(android.R.id.content).findViewWithTag(key);PetalHeightPreview preview=a.findViewById(android.R.id.content).findViewWithTag(key+"_preview");
    assertNotNull(slider);assertTrue(slider.isEnabled());assertEquals(100,p.getInt(key,100));int original=preview.getPreviewKeyboard().mOccupiedHeight;
    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->slider.setProgress(0));InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    assertEquals(90,p.getInt(key,0));assertEquals(100,p.getInt(other,100));assertTrue(preview.getPreviewKeyboard().mOccupiedHeight<original);
    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->slider.setProgress(40));InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(130,p.getInt(key,0));assertTrue(preview.getPreviewKeyboard().mOccupiedHeight>=original);
    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->p.edit().remove(key).apply());InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(original,preview.getPreviewKeyboard().mOccupiedHeight);
   }
  }
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->p.edit().putInt(keys[0],110).putInt(keys[1],125).apply());
  InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  // A new Activity reads the saved values; no preference reset is needed to preview them.
  InstrumentationRegistry.getInstrumentation().runOnMainSync(a::finish);
  PetalHeightActivity reopened=(PetalHeightActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new android.content.Intent(a,PetalHeightActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  SeekBar portrait=reopened.findViewById(android.R.id.content).findViewWithTag(keys[0]);SeekBar landscape=reopened.findViewById(android.R.id.content).findViewWithTag(keys[1]);assertEquals(20,portrait.getProgress());assertEquals(35,landscape.getProgress());
  Button reset=reopened.findViewById(android.R.id.content).findViewWithTag("petal_height_reset");InstrumentationRegistry.getInstrumentation().runOnMainSync(reset::performClick);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  assertFalse(p.contains(keys[0]));assertFalse(p.contains(keys[1]));assertEquals(10,portrait.getProgress());assertEquals(10,landscape.getProgress());
  java.util.Map<String,?> expected=new java.util.HashMap<>(before),after=new java.util.HashMap<>(p.getAll());for(String k:new String[]{keys[0],keys[1],"petal_large_keys"}){expected.remove(k);after.remove(k);}assertEquals(expected,after);
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{p.edit().putBoolean("petal_large_keys",false).apply();reopened.finish();});
 }
}
