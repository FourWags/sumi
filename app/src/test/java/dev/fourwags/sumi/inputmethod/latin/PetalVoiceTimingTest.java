package dev.fourwags.sumi.inputmethod.latin;
import org.junit.Test;
import static org.junit.Assert.*;
public class PetalVoiceTimingTest {
 @Test public void initialWaitIsSeparateAndBounded(){for(int n:new int[]{2000,4000,8000}){PetalVoiceTiming t=new PetalVoiceTiming(n,100);for(int x=0;x<10000;x+=100)t.level(9,100+x);assertFalse(t.expired(10099));assertTrue(t.expired(10100));}}
 @Test public void selectedPauseEndsAtExactBoundary(){for(int n:new int[]{2000,4000,8000}){PetalVoiceTiming t=new PetalVoiceTiming(n,0);t.speech(700);assertFalse(t.expired(699+n));assertTrue(t.expired(700+n));}}
 @Test public void activityExtendsPause(){PetalVoiceTiming t=new PetalVoiceTiming(2000,0);t.speech(0);t.speech(1900);assertFalse(t.expired(2000));assertTrue(t.expired(3900));}
 @Test public void backgroundNoiseAndSpikesDoNotKeepMicOpen(){PetalVoiceTiming t=new PetalVoiceTiming(2000,0);t.speech(0);for(int x=0;x<1900;x+=100)t.level(2,x);t.level(8,1950);assertTrue(t.expired(2000));PetalVoiceTiming loud=new PetalVoiceTiming(2000,0);loud.level(9,0);loud.speech(0);for(int x=0;x<2000;x+=100)loud.level(9,x);assertTrue(loud.expired(2000));}
 @Test public void sustainedSpeechLevelsExtendKnownSpeech(){PetalVoiceTiming t=new PetalVoiceTiming(2000,0);t.speech(0);t.level(0,0);t.level(6,1700);t.level(7,1900);assertFalse(t.expired(2000));t.level(Float.NaN,2100);t.level(Float.POSITIVE_INFINITY,2300);assertTrue(t.expired(3900));}
 @Test public void invalidChoicesUseShortDefault(){for(int n:new int[]{-1,0,1999,Integer.MAX_VALUE})assertEquals(2000,PetalVoiceTiming.silenceMillis(n));}
 @Test public void suggestionOptionsHaveSafeDistinctMetrics(){assertEquals(125,PetalTypingOptions.suggestionSize(-1));assertEquals(22,PetalTypingOptions.suggestionTextSp(125));assertEquals(60,PetalTypingOptions.suggestionHeightDp(125));assertEquals(18,PetalTypingOptions.suggestionTextSp(100));assertEquals(27,PetalTypingOptions.suggestionTextSp(150));assertEquals(72,PetalTypingOptions.suggestionHeightDp(150));}

 @Test public void continuousSpeechWithoutPreSpeechRmsIsNotCutOffAtTwoSeconds(){
  PetalVoiceTiming t=new PetalVoiceTiming(2000,0);t.speech(0);
  for(int now=100;now<=30000;now+=100){t.level(8,now);assertFalse("Continuous speech expired at "+now,t.expired(now));}
  t.level(0,30100);assertFalse(t.expired(31999));assertTrue(t.expired(32000));
 }
 @Test public void freshPartialProgressKeepsContinuousSpeechAlive(){
  PetalVoiceTiming t=new PetalVoiceTiming(2000,0);
  for(int now=0;now<=30000;now+=1000){t.speech(now);assertFalse(t.expired(now+1500));}
  assertTrue(t.expired(32000));
 }

}
