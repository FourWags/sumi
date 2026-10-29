package dev.fourwags.sumi.inputmethod.latin;
import org.junit.Test;
import static org.junit.Assert.*;
public class SumiKeyboardHeightTest {
 @Test public void defaultsRemainExactAndRangesClamp(){
  for(int base:new int[]{176,206,220,302,365})assertEquals(base,SumiKeyboardHeight.pixels(base,100,800,48,1));
  assertEquals(90,SumiKeyboardHeight.clamp(-1));assertEquals(130,SumiKeyboardHeight.clamp(999));
  assertEquals(180,SumiKeyboardHeight.pixels(200,90,800,48,1));assertEquals(260,SumiKeyboardHeight.pixels(200,130,800,48,1));
 }
 @Test public void insetsAndShortScreensBoundEnlargement(){
  assertEquals(224,SumiKeyboardHeight.pixels(220,130,400,48,1));
  assertEquals(220,SumiKeyboardHeight.pixels(220,130,400,96,1));
  assertTrue(SumiKeyboardHeight.pixels(200,130,800,48,1)<=800-48-40);
 }
}
