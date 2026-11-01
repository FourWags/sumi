package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.os.SystemClock;
import dev.fourwags.sumi.inputmethod.keyboard.*;
import dev.fourwags.sumi.inputmethod.latin.common.Constants;
import dev.fourwags.sumi.inputmethod.latin.settings.SettingsValues;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;
import java.lang.reflect.Field;

public class PetalIntegrationTest {
    @Rule public ActivityTestRule<PetalTestActivity> rule = PetalTestRunner.editorRule();
    @Rule public org.junit.rules.TestName testName=new org.junit.rules.TestName();
    private PetalTestActivity activity;
    private LatinIME ime;
    @Before public void setUp() throws Exception {
        activity=rule.getActivity();
        KeyboardSwitcher previous=KeyboardSwitcher.getInstance();
        Field oldOwner=KeyboardSwitcher.class.getDeclaredField("mLatinIME");oldOwner.setAccessible(true);
        shell("ime disable dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        for(int n=0;n<100&&oldOwner.get(previous)!=null;n++)SystemClock.sleep(100);
        assertNull("Previous service must finish unbinding before a fresh test",oldOwner.get(previous));
        shell("ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        shell("ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        field(1|0x20000|0x8000,0,null);
        awaitImeReady();
        field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,0,null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->DeviceProtectedUtils.getSharedPreferences(activity).edit().remove(PetalVoiceTiming.SILENCE).remove(PetalTypingOptions.SUGGESTION_SIZE).remove(SumiKeyboardHeight.PORTRAIT).remove(SumiKeyboardHeight.LANDSCAPE).remove(SettingsValues.PREF_LONG_PRESS_ALTERNATIVES).putBoolean("petal_large_keys",false).putBoolean("petal_literal",false).putBoolean("petal_personal_dictionary",false).putBoolean("pref_key_auto_correction",true).apply());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.loadKeyboard());
    }
    private void awaitImeReady() {
        try {
            Field owner=KeyboardSwitcher.class.getDeclaredField("mLatinIME");owner.setAccessible(true);
            Field destroyed=LatinIME.class.getDeclaredField("mDestroyed");destroyed.setAccessible(true);
            for(int n=0;n<100;n++) {
                KeyboardSwitcher current=KeyboardSwitcher.getInstance();LatinIME service=(LatinIME)owner.get(current);
                if(service!=null&&!destroyed.getBoolean(service)&&service.mKeyboardSwitcher==current
                        &&service.getCurrentInputConnection()!=null&&current.getMainKeyboardView()!=null) {
                    ime=service;return;
                }
                if(n%5==0)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    activity.editor.requestFocus();
                    ((InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE)).showSoftInput(activity.editor,InputMethodManager.SHOW_IMPLICIT);
                });SystemClock.sleep(100);
            }
            fail("The live service, its owned switcher, keyboard view and input connection must be ready");
        } catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
    }
    private void shell(String command) throws Exception {
        try(android.os.ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
            java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){
            byte[] buffer=new byte[512];while(in.read(buffer)!=-1){}
        }
    }
    private void field(int type,int options,String privateOptions) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            activity.options=options;activity.privateOptions=privateOptions;activity.editor.setInputType(type);activity.editor.setText("");activity.editor.requestFocus();
            ((InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE)).restartInput(activity.editor);
            ((InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE)).showSoftInput(activity.editor,0);
        });SystemClock.sleep(700);
        awaitImeReady();
    }
    private void key(int code) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{ime.onPressKey(code,0,true);ime.onCodeInput(code,Constants.NOT_A_COORDINATE,Constants.NOT_A_COORDINATE,false);ime.onReleaseKey(code,false);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(120);
    }
    private void type(String value){for(char c:value.toCharArray())key(c);SystemClock.sleep(600);}
    private String text(){final String[] result={null};InstrumentationRegistry.getInstrumentation().runOnMainSync(()->result[0]=activity.editor.getText().toString());return result[0];}
    @Test public void testNativeDictionaryLoadsAndSpells() {
        DictionaryCollection dict=DictionaryFactory.createMainDictionaryFromManager(activity,java.util.Locale.US);
        assertTrue("English dictionary must know keyboard",dict.isInDictionary("keyboard"));assertFalse(dict.isInDictionary("zzqvtestnonsense"));dict.close();
    }
    @Test public void testAutocorrectAndBackspaceUndo(){type("teh");key(' ');assertEquals("the ",text().toLowerCase());key(Constants.CODE_DELETE);assertEquals("Undo result (synthetic editor)","teh",text().toLowerCase());}
    @Test public void testVisibleRevert(){type("teh");key(' ');assertEquals("the ",text().toLowerCase());InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
        try {
            Field stripField=LatinIME.class.getDeclaredField("mSuggestionStripView");stripField.setAccessible(true);Object strip=stripField.get(ime);
            Field button=strip.getClass().getDeclaredField("mRevertKey");button.setAccessible(true);android.widget.TextView revert=(android.widget.TextView)button.get(strip);
            assertEquals(android.view.View.VISIBLE,revert.getVisibility());assertTrue(revert.getText().toString().contains("teh"));revert.performClick();
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        });assertEquals("teh",text().toLowerCase());}
    @Test public void testPasswordLiteral(){field(0x81,0,null);type("teh ");assertEquals("teh ",text());assertFalse(ime.petalMediaAllowed());}
    @Test public void testUrlLiteral(){field(0x11|0x8000,0,null);type("teh ");assertEquals("teh ",text());assertFalse(ime.petalMediaAllowed());}
    @Test public void testNameLiteral(){field(0x61|0x8000,0,null);type("teh ");assertEquals("teh ",text());assertFalse(ime.petalMediaAllowed());}
    @Test public void testNoLearningLiteral(){field(1|0x8000,0x1000000,null);type("teh ");assertEquals("teh ",text());assertFalse(ime.petalMediaAllowed());}
    @Test public void testNoSuggestionsLiteral(){field(1|0x80000|0x8000,0,null);type("teh ");assertEquals("teh ",text());assertFalse(ime.petalMediaAllowed());}
    @Test public void testCodePrivateLiteral(){field(1|0x8000,0,"code");type("teh ");assertEquals("teh ",text());assertFalse(ime.petalMediaAllowed());}
    @Test public void testPrimarySymbolsIncludeUnderscore(){key(Constants.CODE_SWITCH_ALPHA_SYMBOL);assertNotNull(KeyboardSwitcher.getInstance().getKeyboard().getKey(95));}

    private dev.fourwags.sumi.inputmethod.latin.inputlogic.InputLogic logic() throws Exception {
        Field field=LatinIME.class.getDeclaredField("mInputLogic");field.setAccessible(true);
        return (dev.fourwags.sumi.inputmethod.latin.inputlogic.InputLogic)field.get(ime);
    }
    @Test public void testNextWordPrediction() throws Exception {
        type("thank");key(' ');SystemClock.sleep(1000);
        SuggestedWords words=logic().mSuggestedWords;
        assertFalse("Bundled English next-word suggestions must exist",words.isEmpty());
        boolean found=false;for(int i=0;i<words.size();i++)if("you".equals(words.getWord(i).toLowerCase()))found=true;
        assertTrue("thank should suggest you",found);
    }
    @Test public void testNoHistoryDictionaryOrTypedFiles() throws Exception {
        type("zzqvsyntheticmarker ");
        Field field=LatinIME.class.getDeclaredField("mDictionaryFacilitator");field.setAccessible(true);
        DictionaryFacilitatorImpl dictionaries=(DictionaryFacilitatorImpl)field.get(ime);
        assertNull(dictionaries.getSubDictForTesting(Dictionary.TYPE_USER_HISTORY));
        assertNull(dictionaries.getSubDictForTesting(Dictionary.TYPE_CONTACTS));
        assertNull(dictionaries.getSubDictForTesting(Dictionary.TYPE_USER));
        for(java.io.File dir:new java.io.File[]{activity.getFilesDir(),activity.getCacheDir()})scan(dir);
    }
    private void scan(java.io.File dir) throws Exception {
        java.io.File[] files=dir.listFiles();if(files==null)return;
        for(java.io.File file:files)if(file.isDirectory())scan(file);else {
            byte[] data=java.nio.file.Files.readAllBytes(file.toPath());
            assertFalse("Typed marker found in "+file.getName(),new String(data,java.nio.charset.StandardCharsets.UTF_8).contains("zzqvsyntheticmarker"));
            assertFalse("History file found",file.getName().contains("UserHistory"));
        }
    }
    private void popupTouch(MainKeyboardView view,long down,int action,float x,float y) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
            view.dispatchTouchEvent(event);event.recycle();
        });
    }
    private long holdKey(MainKeyboardView view,Key key) {
        long down=SystemClock.uptimeMillis();
        popupTouch(view,down,android.view.MotionEvent.ACTION_DOWN,key.getX()+key.getWidth()/2f,key.getY()+key.getHeight()/2f);
        SystemClock.sleep(dev.fourwags.sumi.inputmethod.latin.settings.Settings.getInstance().getCurrent().mKeyLongpressTimeout+200);
        return down;
    }
    private MoreKeysKeyboardView shownPanel(MainKeyboardView view) throws Exception {
        Field f=MainKeyboardView.class.getDeclaredField("mMoreKeysPanel");f.setAccessible(true);
        return (MoreKeysKeyboardView)f.get(view);
    }
    private void chooseHeldAlternative(int base,int alternative,boolean capture) throws Exception {
        MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key key=view.getKeyboard().getKey(base);assertNotNull("Base key "+base,key);
        long down=holdKey(view,key);assertTrue("Expected popup for "+base,view.isShowingMoreKeysPanel());
        MoreKeysKeyboardView panel=shownPanel(view);Key target=panel.getKeyboard().getKey(alternative);assertNotNull("Alternative "+alternative,target);
        if(capture)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->saveToolsView(panel,"longpress-"+base));
        int[] parentLocation=new int[2],panelLocation=new int[2];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{view.getLocationOnScreen(parentLocation);panel.getLocationOnScreen(panelLocation);});
        float x=panelLocation[0]-parentLocation[0]+panel.getPaddingLeft()+target.getX()+target.getWidth()/2f;
        float y=panelLocation[1]-parentLocation[1]+panel.getPaddingTop()+target.getY()+target.getHeight()/2f;
        popupTouch(view,down,android.view.MotionEvent.ACTION_MOVE,x,y);
        Field current=MoreKeysKeyboardView.class.getDeclaredField("mCurrentKey");current.setAccessible(true);
        Key hit=(Key)current.get(panel);
        assertNotNull("Visible choice must hit a key",hit);
        assertEquals("Visible center hit: raw="+x+","+y+" translated="+panel.translateX((int)x)+","+panel.translateY((int)y)+" padding="+panel.getPaddingLeft()+","+panel.getPaddingTop()+" target="+target.getX()+","+target.getY(),alternative,hit.getCode());
        popupTouch(view,down,android.view.MotionEvent.ACTION_UP,x,y);
        SystemClock.sleep(150);assertFalse(view.isShowingMoreKeysPanel());
    }
    private void alternatives(boolean enabled) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean(SettingsValues.PREF_LONG_PRESS_ALTERNATIVES,enabled).apply());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void literalLayout(boolean large) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean("petal_large_keys",large).putBoolean("petal_literal",true).apply();ime.loadKeyboard();
        });SystemClock.sleep(200);
    }
    private android.view.View visiblePreview(android.view.View root) {
        if(root instanceof dev.fourwags.sumi.inputmethod.keyboard.internal.KeyPreviewView && root.getVisibility()==android.view.View.VISIBLE)return root;
        if(root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){android.view.View found=visiblePreview(group.getChildAt(i));if(found!=null)return found;}
        }
        return null;
    }
    @Test public void testLongPressPanelsReadableAndCompactInEveryTheme() throws Exception {
        android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);
        field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);
        for(boolean large:new boolean[]{false,true})for(int theme=0;theme<PetalThemeCatalog.IDS.length;theme++) {
            final int index=theme;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->prefs.edit().putInt("petal_theme",PetalThemeCatalog.LEGACY_INDICES[index]).putBoolean("petal_large_keys",large).putBoolean("petal_literal",true).putBoolean("popup_on",true).putInt(PetalTypingOptions.PREVIEW_SIZE,100).putBoolean(SettingsValues.PREF_LONG_PRESS_ALTERNATIVES,true).apply());SystemClock.sleep(250);
            for(int base:new int[]{'f','a'}) {
                MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key key=view.getKeyboard().getKey(base);long down=holdKey(view,key);
                assertTrue("Long-press panel must open",view.isShowingMoreKeysPanel());MoreKeysKeyboardView panel=shownPanel(view);Key target=panel.getKeyboard().getKey(base=='f'?'%':'@');assertNotNull(target);
                try {InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    android.graphics.Bitmap background=android.graphics.Bitmap.createBitmap(panel.getWidth(),panel.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Bitmap rendered=android.graphics.Bitmap.createBitmap(panel.getWidth(),panel.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                    try {
                        panel.getBackground().setBounds(0,0,panel.getWidth(),panel.getHeight());panel.getBackground().draw(new android.graphics.Canvas(background));
                        int fill=background.getPixel(background.getWidth()/2,background.getHeight()/2);android.util.TypedValue ink=new android.util.TypedValue();panel.getContext().getTheme().resolveAttribute(R.attr.sumiPreviewInk,ink,true);
                        assertTrue("Held popup must not use a bright platform background",androidx.core.graphics.ColorUtils.calculateLuminance(fill)<=.12);
                        assertTrue("Held symbol contrast",androidx.core.graphics.ColorUtils.calculateContrast(ink.data,fill)>=7);
                        panel.draw(new android.graphics.Canvas(rendered));int painted=0;int inset=Math.round(3*panel.getResources().getDisplayMetrics().density);
                        for(int y=panel.getPaddingTop()+target.getY()+inset;y<Math.min(rendered.getHeight(),panel.getPaddingTop()+target.getY()+target.getHeight()-inset);y++)for(int x=panel.getPaddingLeft()+target.getX()+inset;x<Math.min(rendered.getWidth(),panel.getPaddingLeft()+target.getX()+target.getWidth()-inset);x++)if(rendered.getPixel(x,y)==ink.data)painted++;
                        assertTrue("Held symbol must be painted clearly",painted>10);
                        if(index==19&&!large)try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"synthetic-held-fix-"+base+".png"))){rendered.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException error){throw new RuntimeException(error);}
                        int[] parentXY=new int[2],popupXY=new int[2];view.getLocationOnScreen(parentXY);panel.getLocationOnScreen(popupXY);
                        assertTrue("Popup stays inside keyboard's left edge",popupXY[0]>=parentXY[0]);assertTrue("Popup stays inside keyboard's right edge",popupXY[0]+panel.getWidth()<=parentXY[0]+view.getWidth());
                        assertTrue("Popup stays above its parent key",popupXY[1]+panel.getHeight()<=parentXY[1]+key.getY()+inset*3);
                        if(base=='f')assertTrue("Single-symbol panel must remain compact",panel.getHeight()<=Math.round(80*panel.getResources().getDisplayMetrics().density));
                    } finally {background.recycle();rendered.recycle();}
                });} finally {popupTouch(view,down,android.view.MotionEvent.ACTION_CANCEL,0,0);}
            }
        }
    }
    @Test public void testPopupLetterContrastAcrossAllThemesAndBothLayouts() throws Exception {
        android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);
        Object oldTheme=prefs.getAll().get("petal_theme");
        try {
            field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);
            for(boolean large:new boolean[]{false,true})for(int theme=0;theme<PetalThemeCatalog.IDS.length;theme++) {
                final int index=theme;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->prefs.edit().putInt("petal_theme",PetalThemeCatalog.LEGACY_INDICES[index]).putBoolean("petal_large_keys",large).putBoolean("petal_literal",true).putBoolean("popup_on",true).apply());
                SystemClock.sleep(250);
                MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key key=view.getKeyboard().getKey('e');
                long down=SystemClock.uptimeMillis();popupTouch(view,down,0,key.getX()+key.getWidth()/2f,key.getY()+key.getHeight()/2f);SystemClock.sleep(80);
                try { InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    android.widget.TextView preview=(android.widget.TextView)visiblePreview(ime.getWindow().getWindow().getDecorView());assertNotNull("Popup visible in theme "+index,preview);
                    assertTrue("Default preview height must be compact",preview.getHeight()<=Math.round(64*preview.getResources().getDisplayMetrics().density));
                    assertEquals("e",preview.getText().toString().toLowerCase(java.util.Locale.US));
                    android.graphics.Bitmap background=android.graphics.Bitmap.createBitmap(preview.getWidth(),preview.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Bitmap rendered=android.graphics.Bitmap.createBitmap(preview.getWidth(),preview.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                    try {
                        preview.getBackground().setBounds(0,0,preview.getWidth(),preview.getHeight());
                        preview.getBackground().draw(new android.graphics.Canvas(background));
                        int surface=background.getPixel(background.getWidth()/2,background.getHeight()/2),ink=preview.getCurrentTextColor();
                        assertTrue("Popup background must be subdued",androidx.core.graphics.ColorUtils.calculateLuminance(surface)<=.12);
                        assertTrue("Popup letter contrast",androidx.core.graphics.ColorUtils.calculateContrast(ink,surface)>=7);
                        preview.draw(new android.graphics.Canvas(rendered));int letters=0;
                        for(int y=0;y<rendered.getHeight();y++)for(int x=0;x<rendered.getWidth();x++)if(rendered.getPixel(x,y)==ink)letters++;
                        assertTrue("Letter must actually be painted above its background",letters>12);
                        if(index==19&&!large)try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"synthetic-keypress-fix.png"))){rendered.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException error){throw new RuntimeException(error);}
                        assertTrue("Letter layout must fit vertically",preview.getLayout().getLineBottom(0)<=preview.getHeight()-preview.getPaddingTop()-preview.getPaddingBottom());
                    } finally {background.recycle();rendered.recycle();}
                }); } finally {popupTouch(view,down,android.view.MotionEvent.ACTION_CANCEL,0,0);}
            }
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{android.content.SharedPreferences.Editor edit=prefs.edit();if(oldTheme instanceof Integer)edit.putInt("petal_theme",(Integer)oldTheme);else edit.remove("petal_theme");edit.apply();});}
    }
    @Test public void testKeypressPreviewPreferenceAndProtectedFields() throws Exception {
        String preference=dev.fourwags.sumi.inputmethod.latin.settings.Settings.PREF_POPUP_ON;
        try {
            for(boolean large:new boolean[]{false,true})for(boolean enabled:new boolean[]{false,true}) {
                field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);
                literalLayout(large);
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean(preference,enabled).apply();ime.loadKeyboard();
                });
                MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key e=view.getKeyboard().getKey('e');
                long down=SystemClock.uptimeMillis();popupTouch(view,down,0,e.getX()+e.getWidth()/2f,e.getY()+e.getHeight()/2f);
                SystemClock.sleep(80);
                assertEquals("Ordinary preview must follow preference in both layouts",enabled,visiblePreview(ime.getWindow().getWindow().getDecorView())!=null);
                popupTouch(view,down,android.view.MotionEvent.ACTION_CANCEL,0,0);
            }
            for(int[] input:new int[][]{{0x81,0},{1,0x1000000}}) {
                field(input[0],input[1],null);
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.loadKeyboard());
                assertFalse("Protected fields suppress previews even when enabled",dev.fourwags.sumi.inputmethod.latin.settings.Settings.getInstance().getCurrent().mKeyPreviewPopupOn);
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->DeviceProtectedUtils.getSharedPreferences(activity).edit().remove(preference).apply());
        }
    }
    @Test public void testOptionalNumberRowBothQwertySizes() throws Exception {
        String pref=dev.fourwags.sumi.inputmethod.latin.settings.Settings.PREF_SHOW_NUMBER_ROW;
        try{for(boolean large:new boolean[]{false,true}){
            field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);literalLayout(large);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean(pref,false).apply();ime.loadKeyboard();});
            int baseline=KeyboardSwitcher.getInstance().getMainKeyboardView().getKeyboard().mOccupiedHeight;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean(pref,true).apply();ime.loadKeyboard();});
            Keyboard keyboard=KeyboardSwitcher.getInstance().getMainKeyboardView().getKeyboard();
            for(char digit:"1234567890".toCharArray()){Key k=keyboard.getKey(digit);assertNotNull(k);assertTrue(k.getY()<keyboard.getKey('q').getY());}
            assertTrue("Optional row must fit selected keyboard height",keyboard.mOccupiedHeight<=baseline+2);
            key('1');key('0');assertEquals("10",text());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean(pref,false).apply();ime.loadKeyboard();});
            assertNull("Cache must return layout without number row",KeyboardSwitcher.getInstance().getMainKeyboardView().getKeyboard().getKey('1'));
        }}finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->DeviceProtectedUtils.getSharedPreferences(activity).edit().remove(pref).apply());}
    }
    @Test public void testEditingPanelNavigationAndProtection() throws Exception {
        field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);type("alpha beta");
        final android.view.View[] panel={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
            ime.petalShowTools(KeyboardSwitcher.getInstance().getMainKeyboardView());Field tools=LatinIME.class.getDeclaredField("petalToolsPopup");tools.setAccessible(true);Object popup=tools.get(ime);Field active=PetalToolsPopup.class.getDeclaredField("active");active.setAccessible(true);panel[0]=((android.widget.PopupWindow)active.get(popup)).getContentView();
            labelled(panel[0],"Editing panel").performClick();labelled(panel[0],"Cursor left").performClick();
        }catch(Exception e){throw new RuntimeException(e);}});
        for(int n=0;n<100&&activity.editor.getSelectionStart()!=9;n++)SystemClock.sleep(20);
        assertEquals(9,activity.editor.getSelectionStart());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{android.view.View select=labelled(panel[0],"Select left");assertNotNull("Editing panel must stay open during navigation",select);select.performClick();});
        for(int n=0;n<100&&Math.abs(activity.editor.getSelectionEnd()-activity.editor.getSelectionStart())!=1;n++)SystemClock.sleep(20);
        assertEquals(1,Math.abs(activity.editor.getSelectionEnd()-activity.editor.getSelectionStart()));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertTrue(ime.onKeyDown(android.view.KeyEvent.KEYCODE_BACK,new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK))));
        assertTrue("Closing tools must leave keyboard available",ime.isInputViewShown());
        field(0x81,0,null);assertFalse(ime.petalNavigate(android.view.KeyEvent.KEYCODE_DPAD_LEFT,0));
    }
    @Test public void testPopupSizeAndDurationBothLayouts() throws Exception {
        try{for(boolean large:new boolean[]{false,true}){
            field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);literalLayout(large);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean("popup_on",true).putInt(PetalTypingOptions.PREVIEW_SIZE,100).putInt(PetalTypingOptions.PREVIEW_DURATION,0).apply();ime.loadKeyboard();});
            MainKeyboardView baseView=KeyboardSwitcher.getInstance().getMainKeyboardView();Key baseKey=baseView.getKeyboard().getKey('e');long baselineDown=SystemClock.uptimeMillis();
            popupTouch(baseView,baselineDown,0,baseKey.getX()+baseKey.getWidth()/2f,baseKey.getY()+baseKey.getHeight()/2f);SystemClock.sleep(50);
            int normalHeight=visiblePreview(ime.getWindow().getWindow().getDecorView()).getHeight();popupTouch(baseView,baselineDown,android.view.MotionEvent.ACTION_CANCEL,0,0);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{DeviceProtectedUtils.getSharedPreferences(activity).edit().putInt(PetalTypingOptions.PREVIEW_SIZE,150).putInt(PetalTypingOptions.PREVIEW_DURATION,250).apply();ime.loadKeyboard();});
            MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key e=view.getKeyboard().getKey('e');float x=e.getX()+e.getWidth()/2f,y=e.getY()+e.getHeight()/2f;long down=SystemClock.uptimeMillis();
            popupTouch(view,down,0,x,y);SystemClock.sleep(50);assertNotNull(visiblePreview(ime.getWindow().getWindow().getDecorView()));assertTrue("Largest preview grows visibly",visiblePreview(ime.getWindow().getWindow().getDecorView()).getHeight()>=normalHeight*1.4f);
            popupTouch(view,down,1,x,y);SystemClock.sleep(70);assertNotNull("Preview lingers after release",visiblePreview(ime.getWindow().getWindow().getDecorView()));
            SystemClock.sleep(300);assertNull("Preview must retire after bounded delay",visiblePreview(ime.getWindow().getWindow().getDecorView()));
        }}finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->DeviceProtectedUtils.getSharedPreferences(activity).edit().remove(PetalTypingOptions.PREVIEW_SIZE).remove(PetalTypingOptions.PREVIEW_DURATION).remove("popup_on").apply());}
    }
    @Test public void testMicrophoneTouchAndAccessibilityActionsWithoutAudio() throws Exception {
        android.view.View root=ime.getWindow().getWindow().getDecorView();android.view.View mic=root.findViewById(R.id.suggestions_strip_voice_key);
        android.view.View strip=root.findViewById(R.id.suggestion_strip_view);
        java.lang.reflect.Field listener=strip.getClass().getDeclaredField("mListener");listener.setAccessible(true);Object previous=listener.get(strip);int[] calls=new int[4];
        Class<?> contract=dev.fourwags.sumi.inputmethod.latin.suggestions.SuggestionStripView.Listener.class;
        Object fake=java.lang.reflect.Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(object,method,args)->{
            switch(method.getName()){
                case "petalStartHeldVoice":calls[0]++;return null;case "petalStopHeldVoice":calls[1]++;return null;
                case "petalCancelHeldVoice":calls[2]++;return null;case "petalToggleVoice":calls[3]++;return null;
                default:return method.invoke(previous,args);
            }
        });
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{listener.set(strip,fake);}catch(Exception e){throw new RuntimeException(e);}});
            float density=activity.getResources().getDisplayMetrics().density;assertTrue(mic.getWidth()>=Math.round(64*density));assertTrue(mic.getHeight()>=Math.round(56*density));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                long t=SystemClock.uptimeMillis();for(int action:new int[]{0,1}){android.view.MotionEvent e=android.view.MotionEvent.obtain(t,t,action,mic.getWidth()/2f,mic.getHeight()/2f,0);mic.dispatchTouchEvent(e);e.recycle();}
            });
            assertArrayEquals(new int[]{0,0,0,1},calls);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                long t=SystemClock.uptimeMillis();for(int action:new int[]{0,2,1}){android.view.MotionEvent e=android.view.MotionEvent.obtain(t,t,action,action==0?mic.getWidth()/2f:-10,mic.getHeight()/2f,0);mic.dispatchTouchEvent(e);e.recycle();}
                mic.performClick();
            });
            assertArrayEquals("Sliding away does not activate; accessibility click toggles once",new int[]{0,0,0,2},calls);
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{listener.set(strip,previous);}catch(Exception e){throw new RuntimeException(e);}});}
    }
    @Test public void testMicrophoneHoldCannotBeStolenBySuggestionSwipe() throws Exception {
        android.view.View root=ime.getWindow().getWindow().getDecorView();android.view.View mic=root.findViewById(R.id.suggestions_strip_voice_key);android.view.ViewGroup strip=root.findViewById(R.id.suggestion_strip_view);
        Field listener=strip.getClass().getDeclaredField("mListener"),detector=strip.getClass().getDeclaredField("mMoreSuggestionsSlidingDetector");listener.setAccessible(true);detector.setAccessible(true);
        Object previous=listener.get(strip),oldDetector=detector.get(strip);int[] calls=new int[4];Class<?> contract=dev.fourwags.sumi.inputmethod.latin.suggestions.SuggestionStripView.Listener.class;
        Object fake=java.lang.reflect.Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(object,method,args)->{
            if(method.getName().equals("petalToggleVoice")){calls[3]++;return null;}if(method.getName().equals("petalStartHeldVoice")){calls[0]++;return null;}if(method.getName().equals("petalStopHeldVoice")){calls[1]++;return null;}if(method.getName().equals("petalCancelHeldVoice")){calls[2]++;return null;}return method.invoke(previous,args);
        });
        try{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
            listener.set(strip,fake);detector.set(strip,new android.view.GestureDetector(activity,new android.view.GestureDetector.SimpleOnGestureListener(){@Override public boolean onScroll(android.view.MotionEvent down,android.view.MotionEvent move,float dx,float dy){return true;}}));
            android.graphics.Rect bounds=new android.graphics.Rect();mic.getDrawingRect(bounds);strip.offsetDescendantRectToMyCoords(mic,bounds);long time=SystemClock.uptimeMillis();
            int[] actions={0,2,1};for(int n=0;n<actions.length;n++){
                float y=bounds.centerY()-(n==0?0:mic.getHeight()*.35f);
                android.view.MotionEvent e=android.view.MotionEvent.obtain(time,time+n*60,actions[n],bounds.centerX(),y,0);strip.dispatchTouchEvent(e);e.recycle();
            }
        }catch(Exception e){throw new RuntimeException(e);}});
            assertArrayEquals("Suggestion swipes must not steal a voice tap",new int[]{0,0,0,1},calls);
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{listener.set(strip,previous);detector.set(strip,oldDetector);}catch(Exception e){throw new RuntimeException(e);}});}
    }
    @Test public void testUnavailableVoiceShowsNoticeWithoutOpeningMicrophone() throws Exception {
        PetalVoiceInput.Factory unavailable=new PetalVoiceInput.Factory(){
            public boolean available(android.content.Context context){return false;}
            public boolean permitted(android.content.Context context){throw new AssertionError("Unavailable service must not request permission");}
            public PetalVoiceInput.Engine create(android.content.Context context){throw new AssertionError("Unavailable service must not create a microphone engine");}
        };
        PetalVoiceInput voice=new PetalVoiceInput(ime,unavailable);
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->voice.startHold(KeyboardSwitcher.getInstance().getMainKeyboardView()));
            Field popup=PetalVoiceInput.class.getDeclaredField("popup");popup.setAccessible(true);assertTrue(((android.widget.PopupWindow)popup.get(voice)).isShowing());
            Field status=PetalVoiceInput.class.getDeclaredField("status");status.setAccessible(true);assertEquals(activity.getString(R.string.petal_voice_unavailable),((android.widget.TextView)status.get(voice)).getText().toString());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::stop);assertTrue(((android.widget.PopupWindow)popup.get(voice)).isShowing());
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }
    @Test public void testMicrophoneScreenHoldShowsNoticeWithoutAudio() throws Exception {
        PetalVoiceInput.Factory unavailable=new PetalVoiceInput.Factory(){
            public boolean available(android.content.Context c){return false;}
            public boolean permitted(android.content.Context c){throw new AssertionError("No microphone permission access expected");}
            public PetalVoiceInput.Engine create(android.content.Context c){throw new AssertionError("No recording allowed");}
        };
        screenHoldVoice(new PetalVoiceInput(ime,unavailable),null);
    }
    @Test public void testMicrophoneScreenHoldStartsAndStopsFakeEngine() throws Exception {
        final int[] calls=new int[3];
        class Fake implements PetalVoiceInput.Factory,PetalVoiceInput.Engine {
            public boolean available(android.content.Context c){return true;}public boolean permitted(android.content.Context c){return true;}
            public PetalVoiceInput.Engine create(android.content.Context c){return this;}
            public void start(android.content.Intent request,android.speech.RecognitionListener listener){calls[0]++;}
            public void stop(){calls[1]++;}public void close(){calls[2]++;}
        }
        screenHoldVoice(new PetalVoiceInput(ime,new Fake()),calls);
    }
    private void screenHoldVoice(PetalVoiceInput voice,int[] calls) throws Exception {
        Field active=LatinIME.class.getDeclaredField("petalVoice");active.setAccessible(true);Object original=active.get(ime);
        Field popup=PetalVoiceInput.class.getDeclaredField("popup");popup.setAccessible(true);
        long down=0;float x=0,y=0;boolean pressed=false;
        try{
            for(boolean large:new boolean[]{false,true}){
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean("petal_large_keys",large).apply();ime.loadKeyboard();});SystemClock.sleep(300);
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{active.set(ime,voice);}catch(Exception e){throw new RuntimeException(e);}});
                android.view.View mic=ime.getWindow().getWindow().getDecorView().findViewById(R.id.suggestions_strip_voice_key);assertTrue(mic.isShown());assertTrue(mic.isEnabled());
                int[] xy=new int[2];InstrumentationRegistry.getInstrumentation().runOnMainSync(()->mic.getLocationOnScreen(xy));x=xy[0]+mic.getWidth()/2f;y=xy[1]+mic.getHeight()/2f;down=SystemClock.uptimeMillis();
                android.view.MotionEvent event=android.view.MotionEvent.obtain(down,down,0,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,true));event.recycle();pressed=true;SystemClock.sleep(650);
                assertNull("Touch down must not open the microphone",popup.get(voice));
                event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),1,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,true));event.recycle();pressed=false;SystemClock.sleep(150);
                if(calls!=null){assertEquals("First tap starts once",large?2:1,calls[0]);assertEquals("First tap does not stop",large?1:0,calls[1]);}
                // Second tap must stop the same engine, without starting another session.
                for(int action:new int[]{0,1}){event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,true));event.recycle();}
                SystemClock.sleep(100);if(calls!=null){assertEquals(large?2:1,calls[0]);assertEquals("Second tap stops exactly once",large?2:1,calls[1]);}
                assertTrue("Status remains visible after release",((android.widget.PopupWindow)popup.get(voice)).isShowing());
                InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);
            }
        }finally{
            if(pressed){android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),3,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,true);event.recycle();}
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.cancel();try{active.set(ime,original);}catch(Exception e){throw new RuntimeException(e);}});
        }
    }
    @Test public void testVoiceWaveformLifecycleWithoutAudio() throws Exception {
        class Fake implements PetalVoiceInput.Engine,PetalVoiceInput.Factory {
            android.speech.RecognitionListener listener;
            public boolean available(android.content.Context c){return true;}
            public boolean permitted(android.content.Context c){return true;}
            public PetalVoiceInput.Engine create(android.content.Context c){return this;}
            public void start(android.content.Intent i,android.speech.RecognitionListener l){listener=l;}
            public void stop(){} public void close(){}
        }
        Fake fake=new Fake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->voice.startHold(KeyboardSwitcher.getInstance().getMainKeyboardView()));
            Field field=PetalVoiceInput.class.getDeclaredField("waveform");field.setAccessible(true);
            PetalVoiceWaveform wave=(PetalVoiceWaveform)field.get(voice);assertNotNull("Visible microphone level indicator",wave);
            Field level=PetalVoiceWaveform.class.getDeclaredField("level");level.setAccessible(true);
            Field active=PetalVoiceWaveform.class.getDeclaredField("listening");active.setAccessible(true);
            Field status=PetalVoiceInput.class.getDeclaredField("status");status.setAccessible(true);
            assertEquals(activity.getString(R.string.petal_voice_connecting),((android.widget.TextView)status.get(voice)).getText().toString());
            assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO,wave.getImportantForAccessibility());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onRmsChanged(10));
            assertEquals(0f,level.getFloat(wave),0f);assertFalse(active.getBoolean(wave));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onReadyForSpeech(new android.os.Bundle()));
            assertTrue(active.getBoolean(wave));
            assertEquals(activity.getString(R.string.petal_voice_listening),((android.widget.TextView)status.get(voice)).getText().toString());
            for(float value:new float[]{-Float.MAX_VALUE,-2,4,10,Float.MAX_VALUE,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onRmsChanged(value));
                float expected=Float.isNaN(value)||Float.isInfinite(value)?0:Math.max(0,Math.min(1,(value+2f)/12f));
                assertEquals(expected,level.getFloat(wave),0f);
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{fake.listener.onBeginningOfSpeech();fake.listener.onRmsChanged(10);});
            assertEquals(activity.getString(R.string.petal_voice_speech),((android.widget.TextView)status.get(voice)).getText().toString());
            assertEquals(1f,level.getFloat(wave),0f);
            // A real draw verifies bounded finite geometry at the maximum level.
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(320,96,android.graphics.Bitmap.Config.ARGB_8888);try{wave.layout(0,0,320,96);wave.draw(new android.graphics.Canvas(image));}finally{image.recycle();}});
            android.speech.RecognitionListener retired=fake.listener;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{fake.listener.onEndOfSpeech();fake.listener.onReadyForSpeech(new android.os.Bundle());fake.listener.onRmsChanged(10);});
            assertFalse(active.getBoolean(wave));assertEquals(0f,level.getFloat(wave),0f);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);
            assertNull(field.get(voice));assertEquals(0f,level.getFloat(wave),0f);
            // A fresh session must ignore every callback from the retired session.
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->voice.startHold(KeyboardSwitcher.getInstance().getMainKeyboardView()));
            PetalVoiceWaveform next=(PetalVoiceWaveform)field.get(voice);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{retired.onReadyForSpeech(new android.os.Bundle());retired.onBeginningOfSpeech();retired.onRmsChanged(10);retired.onEndOfSpeech();});
            assertFalse(active.getBoolean(next));assertEquals(0f,level.getFloat(next),0f);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{fake.listener.onReadyForSpeech(new android.os.Bundle());fake.listener.onRmsChanged(10);voice.stop();fake.listener.onBeginningOfSpeech();fake.listener.onRmsChanged(10);});
            assertFalse(active.getBoolean(next));assertEquals(0f,level.getFloat(next),0f);
            // Retrying after an error must dismiss the old notice, not orphan it.
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onError(android.speech.SpeechRecognizer.ERROR_CLIENT));
            Field popup=PetalVoiceInput.class.getDeclaredField("popup");popup.setAccessible(true);
            android.widget.PopupWindow old=(android.widget.PopupWindow)popup.get(voice);assertTrue(old.isShowing());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->voice.startHold(KeyboardSwitcher.getInstance().getMainKeyboardView()));
            assertFalse(old.isShowing());assertNotNull(field.get(voice));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);
            for(boolean large:new boolean[]{false,true}) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean("petal_large_keys",large).apply();ime.loadKeyboard();});SystemClock.sleep(250);
                for(int cycle=0;cycle<12;cycle++) {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.startHold(KeyboardSwitcher.getInstance().getMainKeyboardView());fake.listener.onReadyForSpeech(new android.os.Bundle());fake.listener.onBeginningOfSpeech();fake.listener.onRmsChanged(8);});
                    if(cycle==0) {
                        SystemClock.sleep(100);
                        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{android.view.View panel=((android.widget.PopupWindow)popup.get(voice)).getContentView();PetalAccessibilityChecks.check(panel,"voice-wave");saveToolsView(panel,"voice-wave-"+(large?"large":"regular"));}catch(Exception e){throw new RuntimeException(e);}});
                    }
                    PetalVoiceWaveform retiredWave=(PetalVoiceWaveform)field.get(voice);
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);
                    assertNull(field.get(voice));assertFalse(active.getBoolean(retiredWave));assertEquals(0f,level.getFloat(retiredWave),0f);assertNull(status.get(voice));assertNull(popup.get(voice));
                }
            }
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }
    @Test public void testVoicePauseCanContinueWithinSelectedLongTimeoutWithoutAudio() throws Exception {
        class Fake implements PetalVoiceInput.Factory,PetalVoiceInput.Engine {
            int starts,stops,closes;android.speech.RecognitionListener listener;
            public boolean available(android.content.Context c){return true;}public boolean permitted(android.content.Context c){return true;}
            public PetalVoiceInput.Engine create(android.content.Context c){return this;}
            public void start(android.content.Intent request,android.speech.RecognitionListener next){starts++;listener=next;}
            public void stop(){stops++;}public void close(){closes++;}
        }
        Fake fake=new Fake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->DeviceProtectedUtils.getSharedPreferences(activity).edit().putInt(PetalVoiceTiming.SILENCE,8000).apply());
        android.os.Bundle first=new android.os.Bundle();first.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,new java.util.ArrayList<>(java.util.Arrays.asList("Sumi")));
        android.os.Bundle second=new android.os.Bundle();second.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,new java.util.ArrayList<>(java.util.Arrays.asList("is ready")));
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView()));
            android.speech.RecognitionListener retired=fake.listener;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{retired.onEndOfSpeech();retired.onResults(first);retired.onResults(first);});
            assertEquals("Pause must not commit or end user dictation", "",text());
            SystemClock.sleep(1000);assertEquals("Normal final result resumes listening",2,fake.starts);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{retired.onError(android.speech.SpeechRecognizer.ERROR_CLIENT);fake.listener.onError(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT);});
            SystemClock.sleep(1000);assertEquals("Silence is not a fatal error",3,fake.starts);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());voice.stop();fake.listener.onResults(second);});
            assertEquals("Segments committed once only after Stop","Sumi is ready",text());assertEquals(1,fake.stops);
            SystemClock.sleep(1000);assertEquals("Stop must prevent restart",3,fake.starts);
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }
    @Test public void testVoicePauseCancelAndStopBetweenSegmentsWithoutAudio() throws Exception {
        class Fake implements PetalVoiceInput.Factory,PetalVoiceInput.Engine {
            int starts;android.speech.RecognitionListener listener;
            public boolean available(android.content.Context c){return true;}public boolean permitted(android.content.Context c){return true;}
            public PetalVoiceInput.Engine create(android.content.Context c){return this;}
            public void start(android.content.Intent request,android.speech.RecognitionListener next){starts++;listener=next;}
            public void stop(){}public void close(){}
        }
        Fake fake=new Fake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);android.os.Bundle result=new android.os.Bundle();result.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,new java.util.ArrayList<>(java.util.Arrays.asList("synthetic pause")));
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());fake.listener.onResults(result);voice.cancel();});
            SystemClock.sleep(1000);assertEquals(1,fake.starts);assertEquals("Cancel discards buffered text","",text());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());fake.listener.onResults(result);voice.stop();});
            assertEquals("Stop during pause commits completed phrase","synthetic pause",text());SystemClock.sleep(1000);assertEquals(2,fake.starts);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());fake.listener.onError(android.speech.SpeechRecognizer.ERROR_NO_MATCH);voice.cancel();});
            SystemClock.sleep(1000);assertEquals("Cancel removes pending silence retry",3,fake.starts);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());fake.listener.onResults(result);});
            android.speech.RecognitionListener old=fake.listener;
            field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,0,null);SystemClock.sleep(1000);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->old.onResults(result));
            assertEquals("Protected editor must block pending restart",4,fake.starts);assertEquals("",text());
            Field pending=PetalVoiceInput.class.getDeclaredField("completed");pending.setAccessible(true);assertEquals(0,((StringBuilder)pending.get(voice)).length());
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }
    @Test public void testVoicePauseLimitsAndPendingCleanupWithoutAudio() throws Exception {
        class Fake implements PetalVoiceInput.Factory,PetalVoiceInput.Engine {
            int starts;android.speech.RecognitionListener listener;
            public boolean available(android.content.Context c){return true;}public boolean permitted(android.content.Context c){return true;}
            public PetalVoiceInput.Engine create(android.content.Context c){return this;}
            public void start(android.content.Intent i,android.speech.RecognitionListener l){starts++;listener=l;assertEquals((long)PetalVoiceTiming.silenceMillis(DeviceProtectedUtils.getSharedPreferences(activity).getInt(PetalVoiceTiming.SILENCE,2000)),i.getLongExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,0));}
            public void stop(){}public void close(){}
        }
        Fake fake=new Fake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
        Field buffer=PetalVoiceInput.class.getDeclaredField("completed"),resume=PetalVoiceInput.class.getDeclaredField("resume"),handler=PetalVoiceInput.class.getDeclaredField("handler"),deadline=PetalVoiceInput.class.getDeclaredField("finalizeTimeout"),timeout=PetalVoiceInput.class.getDeclaredField("timeout");
        for(Field f:new Field[]{buffer,resume,handler,deadline,timeout})f.setAccessible(true);
        android.os.Bundle result=new android.os.Bundle();result.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,new java.util.ArrayList<>(java.util.Arrays.asList("synthetic")));
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView()));
            android.speech.RecognitionListener retired=fake.listener;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{retired.onResults(result);voice.stop();});
            assertEquals("synthetic",text());assertEquals(0,((StringBuilder)buffer.get(voice)).length());
            android.os.Handler h=(android.os.Handler)handler.get(voice);assertFalse(h.hasCallbacks((Runnable)resume.get(voice)));assertFalse(h.hasCallbacks((Runnable)deadline.get(voice)));assertFalse(h.hasCallbacks((Runnable)timeout.get(voice)));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());fake.listener.onResults(result);});SystemClock.sleep(1000);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{((Runnable)timeout.get(voice)).run();((Runnable)deadline.get(voice)).run();retired.onResults(result);}catch(IllegalAccessException e){throw new RuntimeException(e);}});
            assertEquals("Provider missing final callback keeps only completed phrases","syntheticsynthetic",text());assertEquals(0,((StringBuilder)buffer.get(voice)).length());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());fake.listener.onResults(result);});SystemClock.sleep(1000);
            android.os.Bundle tooLarge=new android.os.Bundle();tooLarge.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,new java.util.ArrayList<>(java.util.Arrays.asList(new String(new char[10000]).replace('\0','x'))));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onResults(tooLarge));assertEquals("Oversize aggregate must never paste","syntheticsynthetic",text());assertEquals(0,((StringBuilder)buffer.get(voice)).length());assertFalse(h.hasCallbacks((Runnable)resume.get(voice)));
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }
    @Test public void testHeldVoiceStopsAndRejectsLateResultsWithoutAudio() throws Exception {
        class Fake implements PetalVoiceInput.Engine,PetalVoiceInput.Factory {
            int starts,stops,closes;android.speech.RecognitionListener listener;android.content.Intent request;
            public boolean available(android.content.Context c){return true;}public boolean permitted(android.content.Context c){return true;}
            public PetalVoiceInput.Engine create(android.content.Context c){return this;}
            public void start(android.content.Intent i,android.speech.RecognitionListener l){starts++;request=i;listener=l;}
            public void stop(){stops++;}public void close(){closes++;}
        }
        Fake fake=new Fake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.startHold(KeyboardSwitcher.getInstance().getMainKeyboardView());voice.stop();voice.stop();});
            assertEquals(1,fake.starts);assertEquals(1,fake.stops);assertTrue(fake.request.getBooleanExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE,false));assertEquals("en-US",fake.request.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);String before=text();
            android.os.Bundle result=new android.os.Bundle();result.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,new java.util.ArrayList<>(java.util.Arrays.asList("synthetic voice")));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onResults(result));assertEquals(before,text());assertEquals(1,fake.closes);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.startHold(KeyboardSwitcher.getInstance().getMainKeyboardView());voice.stop();fake.listener.onResults(result);});
            assertEquals("synthetic voice",text());assertEquals(2,fake.closes);
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }
    @Test public void testRealScreenHoldWithAssistance() throws Exception {
        alternatives(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean("petal_large_keys",true).putBoolean("petal_literal",false).putInt("petal_theme",0).apply();ime.loadKeyboard();
        });SystemClock.sleep(400);
        MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key e=view.getKeyboard().getKey('e');assertNotNull(e);
        int[] xy=new int[2];InstrumentationRegistry.getInstrumentation().runOnMainSync(()->view.getLocationOnScreen(xy));
        float x=xy[0]+e.getX()+e.getWidth()/2f,y=xy[1]+e.getY()+e.getHeight()/2f;
        long down=SystemClock.uptimeMillis();
        android.view.MotionEvent event=android.view.MotionEvent.obtain(down,down,0,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,true));event.recycle();
        SystemClock.sleep(80);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.view.View preview=visiblePreview(ime.getWindow().getWindow().getDecorView());assertNotNull("Visible key preview",preview);
            int[] location=new int[2];preview.getLocationOnScreen(location);
            assertTrue("Entire preview must sit above parent key",location[1]+preview.getHeight()<=xy[1]+e.getY());
        });
        for(int move=0;move<20;move++) {
            SystemClock.sleep(50);
            event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),2,x+(move%2),y+(move%2),0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,true);event.recycle();
        }
        android.graphics.Bitmap capture=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"real-screen-hold.png"))){capture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        boolean showing=view.isShowingMoreKeysPanel();
        event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),1,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,true);event.recycle();
        assertTrue("Real screen hold must show alternatives with assistance enabled",showing);
        assertEquals("3",text());
    }
    @Test public void testHeightBothLayoutsAndTouchBounds() throws Exception {
        int actualOrientation=activity.getResources().getConfiguration().orientation;
        String requestedOrientation=InstrumentationRegistry.getArguments().getString("expected_orientation");
        if(requestedOrientation!=null)assertEquals("Actual test Activity orientation",Integer.parseInt(requestedOrientation),actualOrientation);
        java.io.File orientationEvidence=new java.io.File(activity.getExternalFilesDir(null),"height-actual-orientation.txt");
        try(java.io.FileWriter out=new java.io.FileWriter(orientationEvidence)){out.write(Integer.toString(actualOrientation));}
        boolean landscape=actualOrientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        String active=landscape?SumiKeyboardHeight.LANDSCAPE:SumiKeyboardHeight.PORTRAIT,other=landscape?SumiKeyboardHeight.PORTRAIT:SumiKeyboardHeight.LANDSCAPE;
        for(boolean large:new boolean[]{false,true}) {
            field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);literalLayout(large);
            int baseline=KeyboardSwitcher.getInstance().getMainKeyboardView().getKeyboard().mOccupiedHeight;
            for(int percent:new int[]{90,100,130,100}) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->DeviceProtectedUtils.getSharedPreferences(activity).edit().putInt(active,percent).putInt(other,115).apply());SystemClock.sleep(400);
                MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Keyboard keyboard=view.getKeyboard();assertQwertyRows(keyboard);
                if(percent==100)assertEquals(baseline,keyboard.mOccupiedHeight);if(percent==90)assertTrue(keyboard.mOccupiedHeight<baseline);if(percent==130)assertTrue(keyboard.mOccupiedHeight>=baseline);
                android.graphics.Rect visible=new android.graphics.Rect();view.getGlobalVisibleRect(visible);assertTrue("Keyboard must fit screen/insets",visible.height()>=view.getHeight()-1);
                for(Key k:keyboard.getSortedKeys())if(!k.isSpacer()&&k.isEnabled()) {assertTrue(k.getX()>=0);assertTrue(k.getY()>=0);assertTrue(k.getX()+k.getWidth()<=keyboard.mOccupiedWidth+1);assertTrue(k.getY()+k.getHeight()<=keyboard.mOccupiedHeight+1);assertTrue(k.getHitBox().width()>0&&k.getHitBox().height()>0);}
                Key e=keyboard.getKey('e');float x=e.getX()+e.getWidth()/2f,y=e.getY()+e.getHeight()/2f;long tap=android.os.SystemClock.uptimeMillis();String before=text();popupTouch(view,tap,0,x,y);popupTouch(view,tap,1,x,y);assertEquals(before+"e",text());
                long held=holdKey(view,e);assertFalse(view.isShowingMoreKeysPanel());popupTouch(view,held,1,x,y);assertEquals(before+"ee",text());
                Key enter=keyboard.getKey(Constants.CODE_ENTER);assertNotNull(enter);assertNull(enter.getMoreKeys());
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->DeviceProtectedUtils.getSharedPreferences(activity).edit().remove(active).remove(other).apply());SystemClock.sleep(400);assertEquals(baseline,KeyboardSwitcher.getInstance().getMainKeyboardView().getKeyboard().mOccupiedHeight);
        }
    }
    @Test public void testAlternativesDefaultOffAndToggleBothLayouts() throws Exception {
        assertFalse(DeviceProtectedUtils.getSharedPreferences(activity).contains(SettingsValues.PREF_LONG_PRESS_ALTERNATIVES));
        assertFalse(dev.fourwags.sumi.inputmethod.latin.settings.Settings.getInstance().getCurrent().mLongPressAlternatives);
        for(boolean large:new boolean[]{false,true})for(boolean enabled:new boolean[]{false,true,false}) {
            field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);literalLayout(large);alternatives(enabled);
            MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key e=view.getKeyboard().getKey('e');
            float x=e.getX()+e.getWidth()/2f,y=e.getY()+e.getHeight()/2f;
            long tap=SystemClock.uptimeMillis();popupTouch(view,tap,0,x,y);SystemClock.sleep(80);
            assertNotNull("Ordinary preview remains independent",visiblePreview(ime.getWindow().getWindow().getDecorView()));
            popupTouch(view,tap,1,x,y);assertEquals("e",text());
            if(enabled){chooseHeldAlternative('e','é',false);assertEquals("eé",text());}
            else {long down=holdKey(view,e);assertFalse("Disabled hold must not show alternatives",view.isShowingMoreKeysPanel());popupTouch(view,down,1,x,y);assertEquals("ee",text());}
            // The single-alternative digit and symbol paths must obey the same preference.
            Key q=view.getKeyboard().getKey('q');long down=holdKey(view,q);assertEquals(enabled,view.isShowingMoreKeysPanel());popupTouch(view,down,android.view.MotionEvent.ACTION_CANCEL,0,0);
            key(Constants.CODE_SWITCH_ALPHA_SYMBOL);view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key dollar=view.getKeyboard().getKey('$');down=holdKey(view,dollar);assertEquals(enabled,view.isShowingMoreKeysPanel());popupTouch(view,down,android.view.MotionEvent.ACTION_CANCEL,0,0);
        }
    }
    @Test public void testCharacterHoldsBothQwertySizes() throws Exception {
        alternatives(true);
        for(boolean large:new boolean[]{false,true}) {
            field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);literalLayout(large);
            chooseHeldAlternative('e','é',!large);assertEquals("é",text());
            chooseHeldAlternative('n','ñ',false);assertEquals("éñ",text());
            MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key q=view.getKeyboard().getKey('q');
            long down=holdKey(view,q);assertTrue(view.isShowingMoreKeysPanel());
            popupTouch(view,down,android.view.MotionEvent.ACTION_UP,q.getX()+q.getWidth()/2f,q.getY()+q.getHeight()/2f);
            SystemClock.sleep(150);assertEquals("éñ1",text());
            Key a=view.getKeyboard().getKey('a');down=holdKey(view,a);assertTrue(view.isShowingMoreKeysPanel());
            popupTouch(view,down,android.view.MotionEvent.ACTION_CANCEL,a.getX(),a.getY());SystemClock.sleep(100);
            assertFalse(view.isShowingMoreKeysPanel());assertEquals("éñ1",text());
            down=holdKey(view,a);popupTouch(view,down,android.view.MotionEvent.ACTION_MOVE,-1000,-1000);
            popupTouch(view,down,android.view.MotionEvent.ACTION_UP,-1000,-1000);
            assertFalse(view.isShowingMoreKeysPanel());assertEquals("éñ1",text());
            // Repeated short touches must not inherit a cancelled timer or commit alternatives.
            for(int i=0;i<3;i++) {long tap=SystemClock.uptimeMillis();popupTouch(view,tap,0,q.getX()+q.getWidth()/2f,q.getY()+q.getHeight()/2f);popupTouch(view,tap,1,q.getX()+q.getWidth()/2f,q.getY()+q.getHeight()/2f);}
            assertEquals("éñ1qqq",text());
            key(Constants.CODE_SHIFT);chooseHeldAlternative('E','É',false);assertEquals("éñ1qqqÉ",text());
            key(Constants.CODE_SWITCH_ALPHA_SYMBOL);chooseHeldAlternative('$','€',false);assertEquals("éñ1qqqÉ€",text());
            chooseHeldAlternative('!','¡',false);assertEquals("éñ1qqqÉ€¡",text());
            // Audit every alternative on the live alphabet and both symbol pages.
            for(int page=0;page<3;page++) {
                MainKeyboardView live=KeyboardSwitcher.getInstance().getMainKeyboardView();
                for(Key k:live.getKeyboard().getSortedKeys())if(k.getMoreKeys()!=null)for(dev.fourwags.sumi.inputmethod.keyboard.internal.MoreKeySpec alt:k.getMoreKeys()) {
                    assertTrue("No media/action alternate",alt.mCode>32 || alt.mCode==Constants.CODE_OUTPUT_TEXT);
                    String text=alt.mOutputText!=null?alt.mOutputText:new String(Character.toChars(alt.mCode));
                    for(int cp:text.codePoints().toArray())assertTrue("Only reviewed text alternatives",cp>32&&cp<0x2500);
                }
                key(page==0?Constants.CODE_SYMBOL_SHIFT:Constants.CODE_SWITCH_ALPHA_SYMBOL);
            }
        }
    }
    @Test public void testEnterActionModesNeverOpenPopup() throws Exception {
        int[] modes={android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED,android.view.inputmethod.EditorInfo.IME_ACTION_NONE,android.view.inputmethod.EditorInfo.IME_ACTION_GO,android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH,android.view.inputmethod.EditorInfo.IME_ACTION_SEND,android.view.inputmethod.EditorInfo.IME_ACTION_NEXT,android.view.inputmethod.EditorInfo.IME_ACTION_DONE,android.view.inputmethod.EditorInfo.IME_ACTION_PREVIOUS};
        for(boolean enabled:new boolean[]{false,true})for(boolean large:new boolean[]{false,true})for(int mode:modes) {
            alternatives(enabled);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{activity.editor.setImeOptions(mode);activity.actionOverride=mode;});
            field(InputType.TYPE_CLASS_TEXT,mode,null);literalLayout(large);
            assertEquals(mode,ime.getCurrentInputEditorInfo().imeOptions & android.view.inputmethod.EditorInfo.IME_MASK_ACTION);
            MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key enter=view.getKeyboard().getKey(Constants.CODE_ENTER);
            if(enter==null)enter=view.getKeyboard().getKey(Constants.CODE_ACTION_NEXT);
            if(enter==null)enter=view.getKeyboard().getKey(Constants.CODE_ACTION_PREVIOUS);
            assertNotNull("Enter/action key "+mode,enter);assertNull(enter.getMoreKeys());
            long down=holdKey(view,enter);assertFalse("No Enter popup in mode "+mode,view.isShowingMoreKeysPanel());
            popupTouch(view,down,android.view.MotionEvent.ACTION_CANCEL,enter.getX(),enter.getY());
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->activity.actionOverride=null);
        field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);literalLayout(false);key(Constants.CODE_SHIFT);
        MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key enter=view.getKeyboard().getKey(Constants.CODE_SHIFT_ENTER);assertNotNull(enter);
        long down=holdKey(view,enter);assertFalse(view.isShowingMoreKeysPanel());popupTouch(view,down,android.view.MotionEvent.ACTION_CANCEL,enter.getX(),enter.getY());
    }
    @Test public void testHoldEnterHasNoPopup() throws Exception {
        MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();
        Key enter=view.getKeyboard().getKey(10);assertNotNull(enter);
        int[] screen=new int[2];InstrumentationRegistry.getInstrumentation().runOnMainSync(()->view.getLocationOnScreen(screen));
        float x=enter.getX()+enter.getWidth()/2f,y=enter.getY()+enter.getHeight()/2f;
        long down=SystemClock.uptimeMillis();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->view.dispatchTouchEvent(android.view.MotionEvent.obtain(down,down,0,x,y,0)));
        SystemClock.sleep(1000);
        assertFalse("Holding Enter exposed a more-keys popup",view.isShowingMoreKeysPanel());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->view.dispatchTouchEvent(android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),1,x,y,0)));
    }
    @Test public void testStaleRevertDoesNotReplaceOtherText() {
        type("teh");key(' ');
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{activity.editor.setText("synthetic safe");activity.editor.setSelection(2);ime.petalRevertCorrection();});
        assertEquals("synthetic safe",text());
    }

    @Test public void testKeyboardPreview() {
        type("hello");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.view.View root=ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame);
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            root.draw(new android.graphics.Canvas(image));
            try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"keyboard-preview.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new RuntimeException(e);}
        });
    }

    @Test public void testLegacyOfflineGifComponent() throws Exception {
        activity.acceptsGif=true;field(1|0x8000|0x20000,0,null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {java.lang.reflect.Method send=PetalMediaPopup.class.getDeclaredMethod("sendGif",String.class);send.setAccessible(true);send.invoke(new PetalMediaPopup(ime),"bloom");}
            catch(Exception e){throw new RuntimeException(e);}
        });SystemClock.sleep(500);assertEquals("image/gif",activity.receivedMime);
    }
    @Test public void testLegacyEmojiComponent() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.onCodeInput(Constants.CODE_EMOJI,Constants.SUGGESTION_STRIP_COORDINATE,Constants.SUGGESTION_STRIP_COORDINATE,false));
        SystemClock.sleep(400);assertEquals(android.view.View.VISIBLE,ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_palettes_view).getVisibility());
    }

    @Test public void testKeyboardClearsNavigationBar() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.view.View root=ime.getWindow().getWindow().getDecorView();
            android.view.View keyboard=root.findViewById(R.id.main_keyboard_frame);
            android.view.WindowInsets insets=root.getRootWindowInsets();assertNotNull(insets);
            int bottom=android.os.Build.VERSION.SDK_INT>=30?insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            int[] screen=new int[2],rootScreen=new int[2];keyboard.getLocationOnScreen(screen);root.getLocationOnScreen(rootScreen);
            assertTrue("Keyboard overlaps navigation bar: keyboardBottom="+(screen[1]+keyboard.getHeight())+", rootTop="+rootScreen[1]+", rootHeight="+root.getHeight()+", nav="+bottom,
                screen[1]+keyboard.getHeight()<=rootScreen[1]+root.getHeight()-bottom+2);
        });
    }

    @Test public void testCorrectWordsArePreserved() {
        type("hello");key(' ');assertEquals("hello ",text().toLowerCase());
        type("the");key(' ');assertEquals("hello the ",text().toLowerCase());
        type("keyboard");key(' ');assertEquals("hello the keyboard ",text().toLowerCase());
    }

    @Test public void testNumericFieldNeverEnablesCorrection() {
        field(android.text.InputType.TYPE_CLASS_NUMBER,0,null);
        assertFalse(dev.fourwags.sumi.inputmethod.latin.settings.Settings.getInstance().getCurrent().mAutoCorrectionEnabledPerUserSettings);
        type("123");key(Constants.CODE_DELETE);assertEquals("12",text());
        assertFalse(ime.petalMediaAllowed());
    }

    private android.widget.PopupWindow toolsWindow() throws Exception {
        Field owner=LatinIME.class.getDeclaredField("petalToolsPopup");owner.setAccessible(true);
        Object tools=owner.get(ime);assertNotNull(tools);
        Field active=PetalToolsPopup.class.getDeclaredField("active");active.setAccessible(true);
        return (android.widget.PopupWindow)active.get(tools);
    }
    private android.view.View labelled(android.view.View root,String label) {
        if(root instanceof android.widget.TextView&&label.contentEquals(((android.widget.TextView)root).getText()))return root;
        if(root instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)root).getChildCount();i++) {
            android.view.View found=labelled(((android.view.ViewGroup)root).getChildAt(i),label);if(found!=null)return found;
        }return null;
    }
    private void openTools() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.getWindow().getWindow().getDecorView().findViewById(R.id.suggestions_strip_other_key).performClick());SystemClock.sleep(400);
    }
    @Test public void testToolsMenuReplacesMediaAndStaysWithinKeyboard() throws Exception {
        openTools();android.widget.PopupWindow popup=toolsWindow();assertTrue(popup.isShowing());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.view.View box=popup.getContentView();assertNotNull(labelled(box,"Settings"));assertNotNull(labelled(box,"Clipboard"));
            assertNotNull(labelled(box,"Select all"));assertNotNull(labelled(box,"Themes"));assertNull(labelled(box,"Cut"));assertNull(labelled(box,"Emoji"));assertNull(labelled(box,"Offline GIFs"));
            android.view.View frame=ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame);
            int[] menu=new int[2],keys=new int[2];box.getLocationOnScreen(menu);frame.getLocationOnScreen(keys);
            assertTrue("Tools must not cover composer above keyboard",menu[1]>=keys[1]);
            assertTrue("Tools must fit above navigation",menu[1]+box.getHeight()<=keys[1]+frame.getHeight()+2);
            int inset=Math.round(20*box.getResources().getDisplayMetrics().density);
            assertTrue("Tools should use the available keyboard width",box.getWidth()>=frame.getWidth()-inset);
            android.widget.ScrollView scroll=(android.widget.ScrollView)((android.view.ViewGroup)box).getChildAt(1);
            android.view.View close=labelled(box,"Close tools");
            assertTrue("The pinned close button must not overlap scrolling tools",scroll.getBottom()<=close.getTop());
            assertEquals("Clipboard should fit without a mid-word break",1,((android.widget.TextView)labelled(box,"Clipboard")).getLineCount());
            assertEquals("Settings should fit on one line",1,((android.widget.TextView)labelled(box,"Settings")).getLineCount());
            android.view.ViewGroup content=(android.view.ViewGroup)scroll.getChildAt(0);
            scroll.scrollTo(0,content.getHeight());
            android.view.View status=content.getChildAt(content.getChildCount()-1);
            android.graphics.Rect visible=new android.graphics.Rect();status.getDrawingRect(visible);scroll.offsetDescendantRectToMyCoords(status,visible);
            assertTrue("The final tool status must scroll above the fixed footer: bottom="+visible.bottom+" viewport="+scroll.getHeight()+" scrollY="+scroll.getScrollY(),visible.bottom-scroll.getScrollY()<=scroll.getHeight()+1);
            close.performClick();
        });assertFalse(popup.isShowing());
    }
    private void saveToolsView(android.view.View view, String name) {
        // Hardware draws clear the main dirty flag; a subsequent software bitmap
        // capture must repaint instead of reusing an older-size offscreen buffer.
        android.view.View keys=view.findViewById(R.id.keyboard_view);
        if(keys instanceof MainKeyboardView)((MainKeyboardView)keys).invalidateAllKeys();
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(view.getWidth(),view.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(bitmap));
        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),name+".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        } catch(java.io.IOException e) {throw new RuntimeException(e);}
    }
    @Test public void testToolsThemesPreserveCompositionAndPreferences() throws Exception {
        android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);
        int before=PetalThemeCatalog.selected(prefs);
        type("teh");
        openTools();android.widget.PopupWindow popup=toolsWindow();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(String label:new String[]{"Settings","Clipboard","Select all","Copy","Themes","Paste","Large keys","Literal typing"})assertNotNull(label,labelled(popup.getContentView(),label));
            assertNull(labelled(popup.getContentView(),"Cut"));
            saveToolsView(popup.getContentView(),"tools-menu");
            labelled(popup.getContentView(),"Themes").performClick();
        });SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(String name:PetalSettingsActivity.ICONS)assertNotNull(name,labelled(popup.getContentView(),name));
            assertTrue(labelled(popup.getContentView(),PetalSettingsActivity.ICONS[before]).isSelected());
            PetalAccessibilityChecks.check(popup.getContentView(),"tools-themes");
            saveToolsView(popup.getContentView(),"tools-themes");
            labelled(popup.getContentView(),"Harbor Jade").performClick();
        });SystemClock.sleep(600);
        assertFalse(popup.isShowing());assertEquals(8,prefs.getInt("petal_theme",-1));assertEquals(8,prefs.getInt("petal_icon",-1));
        assertFalse(prefs.getBoolean("petal_large_keys",false));assertFalse(prefs.getBoolean("petal_literal",false));
        assertEquals("teh",text());type("x");assertEquals("tehx",text());
        assertEquals(102,KeyboardTheme.getKeyboardTheme(activity).mThemeId);
        openTools();android.widget.PopupWindow cancel=toolsWindow();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{labelled(cancel.getContentView(),"Themes").performClick();labelled(cancel.getContentView(),"Close tools").performClick();});
        assertFalse(cancel.isShowing());assertEquals(8,prefs.getInt("petal_theme",-1));assertEquals("tehx",text());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->PetalSettingsActivity.selectTheme(activity,before));
    }
    /** Direct callback injection must wait for the editor to regain its active connection. */
    private void awaitSyntheticEditorConnection() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        for(int n=0;n<250;n++) {
            final boolean[] ready={false};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.view.inputmethod.InputConnection connection=ime.getCurrentInputConnection();
                InputMethodManager manager=(InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE);
                ready[0]=activity.editor.hasWindowFocus() && manager.isActive(activity.editor)
                        && connection!=null && connection.getTextBeforeCursor(0,0)!=null;
            });
            if(ready[0])return;
            SystemClock.sleep(20);
        }
        fail("Synthetic editor must regain focus and an active connection before direct key injection");
    }
    @Test public void testLiteralToolsToggleSuppressesAssistance() throws Exception {
        openTools();android.widget.PopupWindow popup=toolsWindow();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->labelled(popup.getContentView(),"Literal typing").performClick());
        SettingsValues settings=dev.fourwags.sumi.inputmethod.latin.settings.Settings.getInstance().getCurrent();
        assertTrue(settings.mLiteralMode);assertFalse(settings.mAutoCap);assertFalse(settings.mAutoCorrectionEnabledPerUserSettings);
        assertFalse(settings.isSuggestionsEnabledPerUserSettings());assertFalse(settings.shouldInsertSpacesAutomatically());
        assertFalse("Tools must close before input resumes",popup.isShowing());
        awaitSyntheticEditorConnection();
        type("teh ");assertEquals("teh ",text());
        field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,0,null);
        assertTrue(dev.fourwags.sumi.inputmethod.latin.settings.Settings.getInstance().getCurrent().mLiteralMode);
        openTools();android.widget.PopupWindow second=toolsWindow();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->labelled(second.getContentView(),"Literal typing").performClick());
        assertFalse(DeviceProtectedUtils.getSharedPreferences(activity).getBoolean("petal_literal",false));
    }
    @Test public void testProtectedToolsDisableClipboardAndEditing() throws Exception {
        field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,0,null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.petalShowTools(ime.getWindow().getWindow().getDecorView()));SystemClock.sleep(400);
        android.widget.PopupWindow popup=toolsWindow();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(String label:new String[]{"Clipboard","Copy","Paste","Select all","My media"})assertFalse(label, labelled(popup.getContentView(),label).isEnabled());
            assertTrue(labelled(popup.getContentView(),"Settings").isEnabled());ime.petalPaste("synthetic blocked");assertFalse(ime.petalEdit(android.R.id.selectAll));assertEquals("",activity.editor.getText().toString());popup.dismiss();
        });
    }
    @Test public void testEditorAndSendStayAboveKeyboard() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.view.View root=ime.getWindow().getWindow().getDecorView();
            
            android.view.View frame=root.findViewById(R.id.main_keyboard_frame);
            int[] painted=new int[2];frame.getLocationOnScreen(painted);
            for(android.view.View view:new android.view.View[]{activity.editor,activity.send}) {
                int[] xy=new int[2];view.getLocationOnScreen(xy);
                assertTrue("Composer/Send overlaps IME: "+xy[1]+"+"+view.getHeight()+">"+painted[1],xy[1]+view.getHeight()<=painted[1]+2);
            }
            android.inputmethodservice.InputMethodService.Insets reported=new android.inputmethodservice.InputMethodService.Insets();ime.onComputeInsets(reported);
            int[] window=new int[2];frame.getLocationInWindow(window);
            assertTrue("IME must report the whole painted keyboard",reported.visibleTopInsets<=window[1]);
        });
    }
    @Test public void testAllLiveThemesAndVoiceIcons() throws Exception {
        android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);
        int previous=prefs.getInt("petal_theme",prefs.getInt("petal_icon",0));int previousIcon=prefs.getInt("petal_icon",0);
        java.util.Set<Integer> colors=new java.util.HashSet<>();
        try {for(int selected=0;selected<PetalThemeCatalog.IDS.length;selected++) {
            final int index=selected;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->prefs.edit().putInt("petal_theme",PetalThemeCatalog.LEGACY_INDICES[index]).putInt("petal_icon",PetalThemeCatalog.LEGACY_INDICES[index]).apply());SystemClock.sleep(500);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                MainKeyboardView keyboard=KeyboardSwitcher.getInstance().getMainKeyboardView();
                android.util.TypedValue color=new android.util.TypedValue();keyboard.getContext().getTheme().resolveAttribute(R.attr.sumiKey,color,true);assertTrue(colors.add(color.data));
                assertEquals(100+index,dev.fourwags.sumi.inputmethod.keyboard.KeyboardTheme.getKeyboardTheme(activity).mThemeId);
                android.view.View decor=ime.getWindow().getWindow().getDecorView();
                android.view.View hiddenClipboard=decor.findViewById(R.id.suggestions_strip_clipboard_key);assertEquals(android.view.View.GONE,hiddenClipboard.getVisibility());assertFalse(hiddenClipboard.isClickable());assertFalse(hiddenClipboard.isLongClickable());android.view.View mic=decor.findViewById(R.id.suggestions_strip_voice_key);assertEquals(android.view.View.VISIBLE,mic.getVisibility());assertTrue(mic.isClickable());assertFalse(mic.isLongClickable());
                android.view.View frame=decor.findViewById(R.id.main_keyboard_frame);
                keyboard.invalidateAllKeys();android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(frame.getWidth(),frame.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);frame.draw(new android.graphics.Canvas(bitmap));
                android.util.TypedValue surface=new android.util.TypedValue();keyboard.getContext().getTheme().resolveAttribute(R.attr.petalSurface,surface,true);
                int keyPixels=0,surfacePixels=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++){int pixel=bitmap.getPixel(x,y);if(pixel==color.data)keyPixels++;if(pixel==surface.data)surfacePixels++;}
                assertTrue("Theme keycaps must actually be painted",keyPixels>bitmap.getWidth()*bitmap.getHeight()/5);
                assertTrue("Theme gaps/background must actually be painted",surfacePixels>bitmap.getWidth()*bitmap.getHeight()/10);
                int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
                for(int attr:new int[]{R.attr.sumiKeyOutline,R.attr.petalFunctionOutline,R.attr.sumiKeyShadow,R.attr.petalFunctionShadow}) {
                    android.util.TypedValue edge=new android.util.TypedValue();keyboard.getContext().getTheme().resolveAttribute(attr,edge,true);
                    int count=0;for(int pixel:pixels)if(pixel==edge.data)count++;
                    if(count<=20) {
                        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"synthetic-theme-edge-failure.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException failure){throw new RuntimeException(failure);}
                    }
                    assertTrue("Approved key definition must be painted: "+attr+" theme="+index+" color="+Integer.toHexString(edge.data)+" pixels="+count+" density="+activity.getResources().getDisplayMetrics().density,count>20);
                }
                assertVoiceBubbleMatchesTheme();PetalAccessibilityChecks.checkQwerty(frame,"theme-"+index+"-regular");
                try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"theme-"+index+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new RuntimeException(e);}
                android.graphics.drawable.Drawable icon=activity.getDrawable(PetalSettingsActivity.ICON_DRAWABLES[index]);
                android.graphics.Bitmap iconImage=android.graphics.Bitmap.createBitmap(108,108,android.graphics.Bitmap.Config.ARGB_8888);icon.setBounds(0,0,108,108);icon.draw(new android.graphics.Canvas(iconImage));
                // Shiba palettes have independent launcher artwork, unlike the legacy sprout palettes.
                if(PetalThemeCatalog.LEGACY_INDICES[index]<42) {
                    assertEquals("Legacy launcher background matches keyboard",surface.data,iconImage.getPixel(8,54));
                    assertEquals("Legacy launcher keycap matches keyboard",color.data,iconImage.getPixel(54,67));
                }
                try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"icon-"+index+".png"))){iconImage.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new RuntimeException(e);}

            });
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{prefs.edit().putBoolean("petal_large_keys",true).apply();ime.loadKeyboard();});SystemClock.sleep(400);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{assertVoiceBubbleMatchesTheme();PetalAccessibilityChecks.checkQwerty(ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame),"theme-"+index+"-large");});
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{prefs.edit().putBoolean("petal_large_keys",false).apply();ime.loadKeyboard();});SystemClock.sleep(400);
        }} finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(()->prefs.edit().putInt("petal_theme",previous).putInt("petal_icon",previousIcon).putBoolean("petal_large_keys",false).apply());}
    }
    private void assertVoiceBubbleMatchesTheme() {
        android.widget.ImageButton button=ime.getWindow().getWindow().getDecorView().findViewById(R.id.suggestions_strip_voice_key);
        android.util.TypedValue ink=new android.util.TypedValue();KeyboardSwitcher.getInstance().getMainKeyboardView().getContext().getTheme().resolveAttribute(R.attr.petalStripInk,ink,true);
        assertEquals(ink.data,button.getImageTintList().getDefaultColor());
        assertEquals(activity.getString(R.string.petal_voice_start),button.getContentDescription());
        android.graphics.drawable.Drawable actual=button.getDrawable(),expected=activity.getDrawable(R.drawable.petal_voice_bubble).mutate();expected.setTint(ink.data);
        android.graphics.Rect previous=new android.graphics.Rect(actual.getBounds());
        android.graphics.Bitmap a=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888),b=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
        try{
            actual.setBounds(0,0,64,64);expected.setBounds(0,0,64,64);actual.draw(new android.graphics.Canvas(a));expected.draw(new android.graphics.Canvas(b));
            // ImageView's scaled vector cache has different antialias edge pixels.
            // Compare the silhouette and opaque theme ink, not byte-identical edges.
            int differences=0,inkPixels=0;
            for(int y=0;y<64;y++)for(int x=0;x<64;x++) {
                int actualPixel=a.getPixel(x,y),expectedPixel=b.getPixel(x,y);
                if((android.graphics.Color.alpha(actualPixel)>127)!=(android.graphics.Color.alpha(expectedPixel)>127))differences++;
                if(actualPixel==ink.data)inkPixels++;
            }
            assertTrue("Actual bubble/three-bar silhouette; edge differences="+differences,differences<=16);
            assertTrue("Voice icon must actually paint opaque theme ink",inkPixels>200);
        }
        finally{actual.setBounds(previous);a.recycle();b.recycle();}
    }
    @Test public void testVoicePanelTargetsAndImeBoundsWithoutAudio() throws Exception {
        PetalVoiceInput voice=new PetalVoiceInput(ime);final android.widget.PopupWindow[] window={null};
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try {
                java.lang.reflect.Method show=PetalVoiceInput.class.getDeclaredMethod("showPanel",android.view.View.class);show.setAccessible(true);
                show.invoke(voice,ime.getWindow().getWindow().getDecorView().findViewById(R.id.suggestions_strip_voice_key));
                Field popup=PetalVoiceInput.class.getDeclaredField("popup");popup.setAccessible(true);window[0]=(android.widget.PopupWindow)popup.get(voice);
            }catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(200);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.view.View panel=window[0].getContentView(),frame=ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame);
                int[] pxy=new int[2],fxy=new int[2];panel.getLocationOnScreen(pxy);frame.getLocationOnScreen(fxy);
                android.graphics.Rect bounds=new android.graphics.Rect(fxy[0],fxy[1],fxy[0]+frame.getWidth(),fxy[1]+frame.getHeight());
                assertTrue("Voice controls stay inside painted IME: "+bounds+" panel="+pxy[1],bounds.contains(pxy[0],pxy[1],pxy[0]+panel.getWidth(),pxy[1]+panel.getHeight()));
                PetalAccessibilityChecks.check(panel,"voice-panel");saveToolsView(panel,"voice-panel");voice.cancel();assertFalse(window[0].isShowing());
            });assertEquals("",text());
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }
    @Test public void testVoiceControlBoundsAndProtectedFields() {
        android.view.View decor=ime.getWindow().getWindow().getDecorView();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.view.View mic=decor.findViewById(R.id.suggestions_strip_voice_key);assertEquals(android.view.View.VISIBLE,mic.getVisibility());assertTrue(mic.isClickable());assertFalse(mic.isLongClickable());
            float density=activity.getResources().getDisplayMetrics().density;assertTrue(mic.getWidth()/density>=47.9f);assertTrue(mic.getHeight()/density>=47.9f);
        });
        for(int type:new int[]{0x81,0x11|0x8000,0x61|0x8000,1|0x80000|0x8000}){
            field(type,0,null);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{android.view.View mic=decor.findViewById(R.id.suggestions_strip_voice_key);assertFalse(ime.petalVoiceAllowed());assertEquals(android.view.View.GONE,mic.getVisibility());ime.petalToggleVoice(mic);});
            assertEquals("",text());
        }
        field(1|0x8000,0x1000000,null);assertFalse(ime.petalVoiceAllowed());
        field(1|0x8000,0,"code");assertFalse(ime.petalVoiceAllowed());
    }
    private android.view.accessibility.AccessibilityNodeProvider keyNodes(MainKeyboardView view) {
        android.view.accessibility.AccessibilityNodeProvider provider=view.getAccessibilityNodeProvider();
        assertNotNull("Keyboard must expose virtual keys",provider);return provider;
    }
    private int keyId(MainKeyboardView view,int code) {
        java.util.List<Key> keys=view.getKeyboard().getSortedKeys();
        for(int i=0;i<keys.size();i++)if(keys.get(i).getCode()==code)return i;
        throw new AssertionError("Missing synthetic test key: "+code);
    }
    @Test public void testAccessibilityKeyLabelsActionsAndFocus() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();
            android.view.accessibility.AccessibilityNodeProvider provider=keyNodes(view);
            android.view.accessibility.AccessibilityNodeInfo root=provider.createAccessibilityNodeInfo(-1);
            assertTrue(root.getChildCount()>=26);
            java.util.List<Key> keys=view.getKeyboard().getSortedKeys();
            for(int i=0;i<keys.size();i++)if(!keys.get(i).isSpacer() && keys.get(i).isEnabled()) {
                android.view.accessibility.AccessibilityNodeInfo node=provider.createAccessibilityNodeInfo(i);
                assertNotNull(node.getContentDescription());assertTrue(node.getContentDescription().length()>0);
                assertTrue(node.isClickable());assertTrue(node.isFocusable());
                android.graphics.Rect clip=new android.graphics.Rect(), diagnostic=new android.graphics.Rect();view.getGlobalVisibleRect(clip);node.getBoundsInScreen(diagnostic);
                assertTrue("key="+keys.get(i).getCode()+", bounds="+diagnostic+", viewClip="+clip+", hit="+keys.get(i).getHitBox(),node.isVisibleToUser());
                assertEquals("android.widget.Button",node.getClassName().toString());
                assertEquals(0,node.getActions()&android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK);
                android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);assertFalse(bounds.isEmpty());
            }
            int id=keyId(view,'q');
            assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null));
            assertTrue(keyNodes(view).createAccessibilityNodeInfo(id).isAccessibilityFocused());
            assertNotNull(keyNodes(view).findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY));
            assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,null));
            assertFalse(keyNodes(view).createAccessibilityNodeInfo(id).isAccessibilityFocused());
            assertFalse(provider.performAction(keyId(view,10),android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK,null));
            assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null));
        });SystemClock.sleep(300);assertEquals("q",text().toLowerCase(java.util.Locale.US));
    }
    @Test public void testAccessibilityClickSymbolsAndBackspace() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();
            assertTrue(keyNodes(view).performAction(keyId(view,Constants.CODE_SWITCH_ALPHA_SYMBOL),16,null));
        });SystemClock.sleep(300);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();
            assertTrue(keyNodes(view).performAction(keyId(view,95),16,null));
        });SystemClock.sleep(300);assertEquals("_",text());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();
            assertTrue(keyNodes(view).performAction(keyId(view,Constants.CODE_DELETE),16,null));
        });SystemClock.sleep(300);assertEquals("",text());
    }
    @Test public void testAccessibilityToolsTargetsAndFramework() throws Exception {
        final android.view.View[] anchor={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            anchor[0]=ime.getWindow().getWindow().getDecorView().findViewById(R.id.suggestions_strip_other_key);
            float density=activity.getResources().getDisplayMetrics().density;
            String dimensions="Media target: px="+anchor[0].getWidth()+"x"+anchor[0].getHeight()+", activityDensity="+density+", viewDensity="+anchor[0].getResources().getDisplayMetrics().density;
            // Android rounds dp dimensions to integer pixels (48dp at density 2.55 is 122px).
            int minimum=Math.round(48*anchor[0].getResources().getDisplayMetrics().density);
            assertTrue(dimensions+" (width)",anchor[0].getWidth()>=minimum);assertTrue(dimensions+" (height)",anchor[0].getHeight()>=minimum);
            assertEquals("Keyboard tools",anchor[0].getContentDescription().toString());
            anchor[0].performAccessibilityAction(16,null);
        });SystemClock.sleep(400);
        Field owner=LatinIME.class.getDeclaredField("petalToolsPopup");owner.setAccessible(true);Object media=owner.get(ime);
        Field active=PetalToolsPopup.class.getDeclaredField("active");active.setAccessible(true);android.widget.PopupWindow popup=(android.widget.PopupWindow)active.get(media);
        assertTrue(popup.isShowing());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.widget.LinearLayout box=(android.widget.LinearLayout)popup.getContentView();
            PetalAccessibilityChecks.check(box,"tools");
            android.view.View close=box.getChildAt(box.getChildCount()-1);
            assertTrue(close.performAccessibilityAction(16,null));assertFalse(popup.isShowing());
        });
    }
    @Test public void testAccessibilityEmojiDeleteAndReturn() {
        type("synthetic");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.onCodeInput(Constants.CODE_EMOJI,Constants.NOT_A_COORDINATE,Constants.NOT_A_COORDINATE,false));SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.view.View root=ime.getWindow().getWindow().getDecorView();
            android.view.View delete=root.findViewById(R.id.emoji_keyboard_delete);assertTrue(delete.isClickable());
            assertTrue(delete.performAccessibilityAction(16,null));
        });SystemClock.sleep(300);assertEquals("syntheti",text().toLowerCase(java.util.Locale.US));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            assertTrue(ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_keyboard_alphabet_left).performAccessibilityAction(16,null));
        });SystemClock.sleep(300);assertTrue(KeyboardSwitcher.getInstance().getMainKeyboardView().isShown());
    }
    @Test public void testAccessibilityVirtualEmojiCanBeInserted() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.onCodeInput(Constants.CODE_EMOJI,Constants.NOT_A_COORDINATE,Constants.NOT_A_COORDINATE,false));SystemClock.sleep(500);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            androidx.recyclerview.widget.RecyclerView list=ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_keyboard_list);
            KeyboardView page=(KeyboardView)list.getChildAt(0);
            android.view.accessibility.AccessibilityNodeProvider provider=page.getAccessibilityNodeProvider();assertNotNull(provider);
            java.util.List<Key> keys=page.getKeyboard().getSortedKeys();
            for(int i=0;i<keys.size();i++)if(keys.get(i).isEnabled()&&!keys.get(i).isSpacer()) {
                android.view.accessibility.AccessibilityNodeInfo node=provider.createAccessibilityNodeInfo(i);assertNotNull(node.getContentDescription());
                assertEquals(0,node.getActions()&32);assertFalse(provider.performAction(i,32,null));
                assertTrue(provider.performAction(i,16,null));return;
            }
            fail("No accessible emoji on the visible page");
        });SystemClock.sleep(500);assertFalse("Accessible emoji action must insert text",text().isEmpty());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->KeyboardSwitcher.getInstance().saveKeyboardState());
        field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,0,null);
        assertFalse("A protected editor must not restore a saved emoji page",KeyboardSwitcher.getInstance().isShowingEmojiPalettes());
    }
    private void openEmoji() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> ime.onCodeInput(
                Constants.CODE_EMOJI, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false));
        SystemClock.sleep(500);
    }
    private void assertLargeEmojiTargets() {
        android.view.View root = ime.getWindow().getWindow().getDecorView();
        float density = activity.getResources().getDisplayMetrics().density;
        androidx.recyclerview.widget.RecyclerView list = root.findViewById(R.id.emoji_keyboard_list);
        assertTrue(list.isShown());assertTrue(list.getChildCount() > 0);
        KeyboardView page = (KeyboardView) list.getChildAt(0);
        for (Key key : page.getKeyboard().getSortedKeys()) if (key.isEnabled() && !key.isSpacer()) {
            assertTrue("Narrow emoji " + key.getCode(), key.getHitBox().width() / density >= 48);
            assertTrue("Short emoji " + key.getCode(), key.getHitBox().height() / density >= 48);
            assertTrue("Emoji visual width must follow its target", key.getWidth() / density >= 48);
            assertTrue("Emoji visual height must follow its target", key.getHeight() / density >= 48);
        }
        android.widget.TabHost host = root.findViewById(R.id.emoji_category_tabhost);
        java.util.Set<String> labels = new java.util.HashSet<>();
        for (int i = 0; i < host.getTabWidget().getChildCount(); i++) {
            android.view.View tab = host.getTabWidget().getChildAt(i);
            assertTrue("Narrow emoji category", tab.getWidth() / density >= 48);
            assertTrue("Short emoji category", tab.getHeight() / density >= 48);
            assertTrue("Duplicate category description", labels.add(tab.getContentDescription().toString()));
        }
        for (int id : new int[]{R.id.emoji_keyboard_alphabet_left, R.id.emoji_keyboard_space, R.id.emoji_keyboard_delete}) {
            android.view.View key = root.findViewById(id);
            android.graphics.Rect clip = new android.graphics.Rect();assertTrue(key.getLocalVisibleRect(clip));
            assertTrue("Clipped or narrow emoji action: " + id, clip.width() / density >= 48);
            assertTrue("Clipped or short emoji action: " + id, clip.height() / density >= 48);
        }
        android.graphics.Rect clip = new android.graphics.Rect();assertTrue(list.getLocalVisibleRect(clip));
        assertEquals("Emoji viewport must fit on screen", list.getHeight(), clip.height());
        assertTrue("At least one full emoji row must be visible",clip.height()/density>=56);
    }
    @Test public void testLargeEmojiTargetsCategoriesScrollAndActions() {
        largeKeys(true);openEmoji();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertLargeEmojiTargets();
            android.view.View root = ime.getWindow().getWindow().getDecorView();
            PetalAccessibilityChecks.check(root.findViewById(R.id.emoji_palettes_view), "large-emoji");
            android.view.View preview = root.findViewById(R.id.emoji_palettes_view);
            android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(preview.getWidth(),preview.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            preview.draw(new android.graphics.Canvas(image));
            try (java.io.OutputStream out = new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null), "large-emoji-preview.png"))) {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
            } catch (Exception e) {throw new RuntimeException(e);}
            android.widget.TabHost host = root.findViewById(R.id.emoji_category_tabhost);
            android.view.View last = host.getTabWidget().getChildAt(host.getTabWidget().getChildCount()-1);
            last.requestRectangleOnScreen(new android.graphics.Rect(0,0,last.getWidth(),last.getHeight()),true);
            androidx.recyclerview.widget.RecyclerView list = root.findViewById(R.id.emoji_keyboard_list);
            list.scrollBy(0, Math.round(3 * 56 * activity.getResources().getDisplayMetrics().density));
        });SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertLargeEmojiTargets();
            android.view.View root = ime.getWindow().getWindow().getDecorView();
            PetalAccessibilityChecks.check(root.findViewById(R.id.emoji_palettes_view), "large-emoji-scrolled");
            android.widget.TabHost host = root.findViewById(R.id.emoji_category_tabhost);
            android.view.View last = host.getTabWidget().getChildAt(host.getTabWidget().getChildCount()-1);
            android.graphics.Rect clip = new android.graphics.Rect();assertTrue(last.getLocalVisibleRect(clip));
            assertEquals(last.getWidth(),clip.width());assertTrue(last.performAccessibilityAction(16,null));
        });SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertLargeEmojiTargets();
            android.view.View root = ime.getWindow().getWindow().getDecorView();
            PetalAccessibilityChecks.check(root.findViewById(R.id.emoji_palettes_view), "large-emoji-last-category");
            root.findViewById(R.id.emoji_keyboard_space).performAccessibilityAction(16,null);
        });SystemClock.sleep(250);assertEquals(" ",text());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_keyboard_delete).performAccessibilityAction(16,null));
        SystemClock.sleep(250);assertEquals("",text());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_keyboard_alphabet_left).performAccessibilityAction(16,null));
        SystemClock.sleep(250);assertTrue(KeyboardSwitcher.getInstance().getMainKeyboardView().isShown());
    }
    @Test public void testLargeEmojiReflowsAfterModeChange() {
        largeKeys(true);openEmoji();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(this::assertLargeEmojiTargets);
        key(Constants.CODE_ALPHA_FROM_EMOJI);largeKeys(false);openEmoji();
        final int[] standardSize={0,0};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            androidx.recyclerview.widget.RecyclerView list=ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_keyboard_list);
            android.graphics.Rect bounds=((KeyboardView)list.getChildAt(0)).getKeyboard().getSortedKeys().get(0).getHitBox();
            standardSize[0]=bounds.width();standardSize[1]=bounds.height();
        });
        key(Constants.CODE_ALPHA_FROM_EMOJI);largeKeys(true);openEmoji();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertLargeEmojiTargets();
            androidx.recyclerview.widget.RecyclerView list=ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_keyboard_list);
            android.graphics.Rect bounds=((KeyboardView)list.getChildAt(0)).getKeyboard().getSortedKeys().get(0).getHitBox();
            assertTrue("Emoji layout cache did not reflow",bounds.width()!=standardSize[0]||bounds.height()!=standardSize[1]);
        });
    }
    @Test public void testAccessibilityLargeKeyboardFrameworkAndRevertLabel() {
        largeKeys(true);
        type("teh");key(' ');
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.view.View root=ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame);
            PetalAccessibilityChecks.checkQwerty(root,"keyboard");
        });
    }

    private void largeKeys(boolean enabled) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            DeviceProtectedUtils.getSharedPreferences(activity).edit().putBoolean("petal_large_keys",enabled).apply();
            ime.loadKeyboard();
        });SystemClock.sleep(500);
    }
    @After public void restoreLargeKeyPreference() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->DeviceProtectedUtils.getSharedPreferences(activity).edit().remove(PetalVoiceTiming.SILENCE).remove(PetalTypingOptions.SUGGESTION_SIZE).putBoolean("petal_large_keys",false).apply());
    }
    private void assertLargeTargets() {
        MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();
        assertTrue(view.getKeyboard().mId.mPetalLargeKeys);
        float density=activity.getResources().getDisplayMetrics().density;
        // Enlarged QWERTY intentionally retains ten letters across. Letter widths
        // can be below 48dp on phones; do not misreport the retired six-column guarantee.
        if(view.getKeyboard().getKey('q')!=null) assertQwertyRows(view.getKeyboard());
        for(Key key:view.getKeyboard().getSortedKeys())if(!key.isSpacer()&&key.isEnabled()) {
            android.graphics.Rect bounds=key.getHitBox();
            assertTrue("Key must remain usable and visible",bounds.width()>0&&bounds.height()>0);
        }
        android.graphics.Rect visible=new android.graphics.Rect();view.getGlobalVisibleRect(visible);
        assertTrue("Large keyboard is vertically clipped",visible.height()>=view.getHeight()-1);
    }
    @Test public void testLargeKeysAlphabetSymbolsAndReturn() {
        largeKeys(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{assertLargeTargets();PetalAccessibilityChecks.checkQwerty(ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame),"large-alphabet");});
        key(Constants.CODE_SWITCH_ALPHA_SYMBOL);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{assertLargeTargets();assertNotNull(KeyboardSwitcher.getInstance().getKeyboard().getKey(95));PetalAccessibilityChecks.checkQwerty(ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame),"large-symbols");});
        key(Constants.CODE_SHIFT);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{assertLargeTargets();assertNotNull(KeyboardSwitcher.getInstance().getKeyboard().getKey('_'));PetalAccessibilityChecks.checkQwerty(ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame),"large-symbols-second");});
        key(Constants.CODE_SWITCH_ALPHA_SYMBOL);largeKeys(false);
        assertFalse(KeyboardSwitcher.getInstance().getKeyboard().mId.mPetalLargeKeys);
    }
    @Test public void testLargeKeysNativeCorrectionAndProtectedInput() {
        largeKeys(true);type("teh");key(' ');assertEquals("the ",text().toLowerCase(java.util.Locale.US));key(Constants.CODE_DELETE);assertEquals("teh",text().toLowerCase(java.util.Locale.US));
        field(0x81,0,null);largeKeys(true);type("teh ");assertEquals("teh ",text());assertFalse(ime.petalMediaAllowed());
    }
    @Test public void testLargeKeyPreview() {
        largeKeys(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            assertLargeTargets();
            android.view.View root=ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame);
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            root.draw(new android.graphics.Canvas(image));
            try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"large-keyboard-preview.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new RuntimeException(e);}
        });
    }

    @Test public void testEmptySuggestionsAreNotAccessibilityStops() throws Exception {
        Field f=LatinIME.class.getDeclaredField("mSuggestionStripView");f.setAccessible(true);Object strip=f.get(ime);
        Field words=strip.getClass().getDeclaredField("mWordViews");words.setAccessible(true);
        java.util.List<android.widget.TextView> views=(java.util.List<android.widget.TextView>)words.get(strip);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(android.widget.TextView word:views)if(word.isShown()&&android.text.TextUtils.isEmpty(word.getText())) {
                assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO,word.getImportantForAccessibility());
                assertFalse(word.isClickable());assertFalse(word.isFocusable());assertFalse(word.isEnabled());
            }
        });type("thank");key(' ');SystemClock.sleep(700);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            boolean found=false;
            for(android.widget.TextView word:views)if(word.isShown()&&!android.text.TextUtils.isEmpty(word.getText())) {
                found=true;assertTrue(word.isEnabled());assertTrue(word.isClickable());assertTrue(word.isFocusable());
                assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES,word.getImportantForAccessibility());
            }
            assertTrue("Populated suggestions must remain available",found);
        });
    }
    @Test public void testRevertExpandsForLargerText() throws Exception {
        largeKeys(true);type("teh");key(' ');
        Field f=LatinIME.class.getDeclaredField("mSuggestionStripView");f.setAccessible(true);Object strip=f.get(ime);
        Field key=strip.getClass().getDeclaredField("mRevertKey");key.setAccessible(true);android.widget.TextView revert=(android.widget.TextView)key.get(strip);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{revert.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,36);revert.requestLayout();});SystemClock.sleep(300);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.text.Layout layout=revert.getLayout();assertNotNull(layout);
            assertTrue("Revert text is clipped",layout.getLineBottom(layout.getLineCount()-1)<=revert.getHeight()-revert.getCompoundPaddingTop()-revert.getCompoundPaddingBottom());
            PetalAccessibilityChecks.checkQwerty(ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame),"revert-large-font");
            revert.performClick();
        });assertEquals("teh",text().toLowerCase(java.util.Locale.US));
    }

    @Test public void testSpokenResourcesHaveCompiledReferences() throws Exception {
        Class<?> registry=Class.forName("dev.fourwags.sumi.inputmethod.accessibility.SpokenKeyResources");
        Field ids=registry.getDeclaredField("IDS");ids.setAccessible(true);
        java.util.Map<String,Integer> values=(java.util.Map<String,Integer>)ids.get(null);
        assertEquals(1468,values.size());
        for(java.util.Map.Entry<String,Integer> entry:values.entrySet()) {
            assertEquals(entry.getKey(),activity.getResources().getResourceEntryName(entry.getValue()));
            assertNotNull(activity.getString(entry.getValue()));
        }
        java.lang.reflect.Method find=registry.getDeclaredMethod("find",String.class);find.setAccessible(true);
        assertEquals(R.string.spoken_emoji_1F600,find.invoke(null,"spoken_emoji_1F600"));
        assertEquals(0,find.invoke(null,"spoken_emoji_not_bundled"));
    }
    @Test public void testManagersUseApplicationContext() throws Exception {
        Field f=RichInputMethodManager.class.getDeclaredField("mApplication");f.setAccessible(true);
        assertSame(activity.getApplication(),f.get(RichInputMethodManager.getInstance()));
        f=dev.fourwags.sumi.inputmethod.accessibility.AccessibilityUtils.class.getDeclaredField("mApplication");f.setAccessible(true);
        assertSame(activity.getApplication(),f.get(dev.fourwags.sumi.inputmethod.accessibility.AccessibilityUtils.Companion.getInstance()));
        f=dev.fourwags.sumi.inputmethod.latin.settings.Settings.class.getDeclaredField("mContext");f.setAccessible(true);
        assertSame("Settings reload must retain only Application",activity.getApplication(),f.get(dev.fourwags.sumi.inputmethod.latin.settings.Settings.getInstance()));
    }
    @Test public void testSubtypeSavedStateParcelRoundTrip() throws Exception {
        Class<?> type=Class.forName("dev.fourwags.sumi.inputmethod.latin.settings.CustomInputStylePreference$SavedState");
        java.lang.reflect.Constructor<?> ctor=type.getDeclaredConstructor(android.os.Parcelable.class);ctor.setAccessible(true);
        Object state=ctor.newInstance(android.view.AbsSavedState.EMPTY_STATE);
        Field subtype=type.getDeclaredField("mSubtype");subtype.setAccessible(true);
        android.view.inputmethod.InputMethodSubtype original=new android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeLocale("en_US").setSubtypeMode("keyboard").build();subtype.set(state,original);
        android.os.Parcel parcel=android.os.Parcel.obtain();
        try {
            ((android.os.Parcelable)state).writeToParcel(parcel,0);parcel.setDataPosition(0);
            Field creator=type.getDeclaredField("CREATOR");creator.setAccessible(true);
            Object restored=((android.os.Parcelable.Creator<?>)creator.get(null)).createFromParcel(parcel);
            assertEquals(original,subtype.get(restored));
        } finally {parcel.recycle();}
    }
    private KeyboardView emojiPage() {
        androidx.recyclerview.widget.RecyclerView list=ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_keyboard_list);
        return (KeyboardView)list.getChildAt(0);
    }
    private void emojiTouch(KeyboardView page,int action,Key key) {
        android.view.MotionEvent event=android.view.MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,
                key.getHitBox().centerX(),key.getHitBox().centerY(),0);
        try {page.onTouchEvent(event);} finally {event.recycle();}
    }
    @Test public void testCancelledEmojiCannotCommitLater() {
        openEmoji();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            KeyboardView page=emojiPage();Key first=page.getKeyboard().getSortedKeys().get(0);
            emojiTouch(page,0,first);emojiTouch(page,3,first);assertFalse(page.performClick());
        });SystemClock.sleep(200);assertEquals("",text());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            KeyboardView page=emojiPage();Key first=page.getKeyboard().getSortedKeys().get(0);
            emojiTouch(page,0,first);emojiTouch(page,1,first);
            ime.onCodeInput(Constants.CODE_ALPHA_FROM_EMOJI,Constants.NOT_A_COORDINATE,Constants.NOT_A_COORDINATE,false);
        });SystemClock.sleep(200);assertEquals("A hidden page must cancel its delayed insertion","",text());
        field(0x81,0,null);SystemClock.sleep(200);assertEquals("",text());
    }
    @Test public void testEmojiTouchAndAccessibleClickInsertExactlyOnce() {
        openEmoji();final String[] expected={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            KeyboardView page=emojiPage();Key first=page.getKeyboard().getSortedKeys().get(0);
            expected[0]=first.getOutputText()!=null?first.getOutputText():new String(Character.toChars(first.getCode()));
            emojiTouch(page,0,first);emojiTouch(page,1,first);
        });SystemClock.sleep(200);assertEquals(expected[0],text());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertTrue(emojiPage().getAccessibilityNodeProvider().performAction(0,16,null)));
        SystemClock.sleep(200);assertEquals(expected[0]+expected[0],text());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertFalse(emojiPage().performClick()));
        assertEquals(expected[0]+expected[0],text());
    }
    @Test public void testPanelClickCommitsOnceAndCancelClearsSelection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            final int[] commits={0};
            MoreKeysKeyboardView panel=new MoreKeysKeyboardView(ime,null) {
                @Override protected void onKeyInput(Key key,int x,int y){commits[0]++;}
            };
            Keyboard keyboard=KeyboardSwitcher.getInstance().getKeyboard();panel.setKeyboard(keyboard);
            Key q=keyboard.getKey('q');if(q==null)q=keyboard.getKey('Q');assertNotNull(q);
            int x=q.getHitBox().centerX(),y=q.getHitBox().centerY();
            panel.onDownEvent(x,y,0,SystemClock.uptimeMillis());
            android.view.MotionEvent cancel=android.view.MotionEvent.obtain(0,SystemClock.uptimeMillis(),3,x,y,0);
            panel.onTouchEvent(cancel);cancel.recycle();assertFalse(panel.performClick());assertEquals(0,commits[0]);
            panel.onDownEvent(x,y,0,SystemClock.uptimeMillis());panel.onUpEvent(x,y,0,SystemClock.uptimeMillis());
            assertEquals(1,commits[0]);assertFalse(panel.performClick());assertEquals(1,commits[0]);
        });
    }
    @Test public void testServiceTeardownReleasesViewsAndPointerState() throws Exception {
        KeyboardSwitcher old=KeyboardSwitcher.getInstance();
        Field owner=KeyboardSwitcher.class.getDeclaredField("mLatinIME");owner.setAccessible(true);
        Field input=KeyboardSwitcher.class.getDeclaredField("mCurrentInputView");input.setAccessible(true);
        assertNotNull(input.get(old));
        shell("ime disable dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        for(int n=0;n<100&&owner.get(old)!=null;n++)SystemClock.sleep(100);
        assertNull("Destroyed service retained by switcher",owner.get(old));assertNull(input.get(old));
        Field proxy=PointerTracker.class.getDeclaredField("sDrawingProxy");proxy.setAccessible(true);assertNull(proxy.get(null));
        shell("ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");shell("ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        for(int n=0;n<100;n++) {
            ime=(LatinIME)owner.get(KeyboardSwitcher.getInstance());
            if(ime!=null&&ime.getCurrentInputConnection()!=null&&KeyboardSwitcher.getInstance().getMainKeyboardView()!=null)break;
            SystemClock.sleep(100);
        }
        assertNotSame(old,KeyboardSwitcher.getInstance());assertNotNull(ime);
        field(1|0x20000|0x8000,0,null);type("q");assertEquals("q",text().toLowerCase(java.util.Locale.US));
        for(int cycle=0;cycle<3;cycle++) {
            // Deliberately re-enable immediately, exercising delayed framework unbind/rebind.
            shell("ime disable dev.fourwags.sumi.inputmethod.latin/.LatinIME");
            shell("ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");shell("ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");
            field(1|0x20000|0x8000,0,null);assertSame(ime.mKeyboardSwitcher,KeyboardSwitcher.getInstance());
            type("q");assertEquals("Rapid rebind lost or duplicated input", "q",text().toLowerCase(java.util.Locale.US));
        }
    }
    @Test public void testDestroyedServiceIgnoresLateInputCallbacks() throws Exception {
        LatinIME retired=ime;KeyboardSwitcher switcher=retired.mKeyboardSwitcher;
        Field owner=KeyboardSwitcher.class.getDeclaredField("mLatinIME");owner.setAccessible(true);
        shell("ime disable dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        for(int n=0;n<100&&owner.get(switcher)!=null;n++)SystemClock.sleep(100);assertNull(owner.get(switcher));
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;
                retired.onStartInput(info,false);retired.onStartInputView(info,false);
                retired.onStartInputInternal(info,false);retired.onStartInputViewInternal(info,false);
                retired.onFinishInputView(false);retired.onFinishInput();retired.onFinishInputViewInternal(false);retired.onFinishInputInternal();
                assertNull(retired.onCreateInputView());retired.onConfigurationChanged(new android.content.res.Configuration(activity.getResources().getConfiguration()));
            });
            assertNull("Late callbacks must not recreate a released owner",owner.get(switcher));
            assertNull("UI handler must reject retired owner",retired.mHandler.getOwnerInstance());
        }finally{shell("ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");shell("ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");awaitImeReady();}
    }
    @Test public void testUninitializedWorkerTeardownIsSafe() throws Exception {
        Class<?> type=Class.forName("dev.fourwags.sumi.inputmethod.latin.inputlogic.InputLogicHandler");
        Field nullHandler=type.getDeclaredField("NULL_HANDLER");nullHandler.setAccessible(true);
        java.lang.reflect.Method destroy=type.getDeclaredMethod("destroy");destroy.setAccessible(true);
        destroy.invoke(nullHandler.get(null));destroy.invoke(nullHandler.get(null));
        type("q");assertEquals("q",text().toLowerCase(java.util.Locale.US));
    }

    private void assertQwertyRows(Keyboard keyboard) {
        String[] rows={"qwertyuiop","asdfghjkl","zxcvbnm"};
        int previous=-1;
        for(String row:rows) {
            int top=keyboard.getKey(row.charAt(0)).getHitBox().top;
            assertTrue("QWERTY rows must remain separate",top>previous);
            int left=-1;
            for(char letter:row.toCharArray()) {
                Key key=keyboard.getKey(letter);assertNotNull("Missing QWERTY letter: "+letter,key);
                assertEquals("Keep familiar QWERTY row for "+letter,top,key.getHitBox().top);
                assertTrue("Keep QWERTY letter order",key.getHitBox().left>left);
                left=key.getHitBox().left;
            }
            previous=top;
        }
    }
    @Test public void testBothQwertySizesRenderAndToolsSwitchWithoutTextLoss() throws Exception {
        android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);
        boolean hadResize=prefs.contains("pref_resize_keyboard"), resize=prefs.getBoolean("pref_resize_keyboard",false);
        boolean hadScale=prefs.contains("pref_keyboard_height_scale");float scale=prefs.getFloat("pref_keyboard_height_scale",1f);
        int[] standard=new int[4];
        try {
            // A saved enlargement must not leak into the regular size.
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->prefs.edit().putBoolean("pref_resize_keyboard",true).putFloat("pref_keyboard_height_scale",1.2f).apply());
            largeKeys(false);type("petal");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();
                assertFalse(view.getKeyboard().mId.mPetalLargeKeys);assertQwertyRows(view.getKeyboard());
                float density=activity.getResources().getDisplayMetrics().density;
                if(activity.getResources().getConfiguration().smallestScreenWidthDp<600) {
                    float limit=activity.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?177:207;
                    assertTrue("Regular QWERTY must stay compact",view.getKeyboard().mId.mHeight/density<=limit);
                }
                standard[0]=view.getHeight();standard[1]=view.getKeyboard().getKey('q').getHitBox().height();standard[2]=view.getKeyboard().getKey('q').getHitBox().width();standard[3]=view.getKeyboard().getKey('q').getHeight();
                saveToolsView(ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame),"qwerty-regular");
            });
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.content.SharedPreferences.Editor edit=prefs.edit();
                if(hadResize)edit.putBoolean("pref_resize_keyboard",resize);else edit.remove("pref_resize_keyboard");
                if(hadScale)edit.putFloat("pref_keyboard_height_scale",scale);else edit.remove("pref_keyboard_height_scale");
                edit.apply();
            });
            openTools();android.widget.PopupWindow popup=toolsWindow();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->labelled(popup.getContentView(),"Large keys").performClick());SystemClock.sleep(600);
            assertEquals("petal",text());assertTrue(prefs.getBoolean("petal_large_keys",false));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();
                assertTrue(view.getKeyboard().mId.mPetalLargeKeys);assertQwertyRows(view.getKeyboard());
                assertTrue("Large QWERTY must render taller",view.getHeight()>=standard[0]*1.24f);
                assertTrue("Large QWERTY letters must have taller targets",view.getKeyboard().getKey('q').getHitBox().height()>standard[1]);
                assertTrue("Painted keycaps must enlarge too",view.getKeyboard().getKey('q').getHeight()>standard[3]);
                assertEquals("Both sizes retain ten-letter QWERTY rows",standard[2],view.getKeyboard().getKey('q').getHitBox().width());
                saveToolsView(ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame),"qwerty-large");
                try(java.io.FileWriter out=new java.io.FileWriter(new java.io.File(activity.getExternalFilesDir(null),"qwerty-size-metrics.txt"))) {
                    float d=activity.getResources().getDisplayMetrics().density;
                    out.write("regular_body_dp="+standard[0]/d+"\nlarge_body_dp="+view.getHeight()/d+"\nregular_q_height_dp="+standard[1]/d+"\nlarge_q_height_dp="+view.getKeyboard().getKey('q').getHitBox().height()/d+"\nq_width_dp="+standard[2]/d+"\nqwerty_rows_verified=true\n");
                }catch(java.io.IOException e){throw new RuntimeException(e);}
            });
            openTools();android.widget.PopupWindow second=toolsWindow();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->labelled(second.getContentView(),"Large keys").performClick());SystemClock.sleep(600);
            assertEquals("petal",text());assertFalse(prefs.getBoolean("petal_large_keys",false));
            assertEquals(standard[0],KeyboardSwitcher.getInstance().getMainKeyboardView().getHeight());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.content.SharedPreferences.Editor edit=prefs.edit();
                if(hadResize)edit.putBoolean("pref_resize_keyboard",resize);else edit.remove("pref_resize_keyboard");
                if(hadScale)edit.putFloat("pref_keyboard_height_scale",scale);else edit.remove("pref_keyboard_height_scale");
                edit.putBoolean("petal_large_keys",false).apply();ime.loadKeyboard();
            });
        }
    }
    @Test public void testSavedSizeRefreshesLiveKeyboardAndSurvivesEditorRestart() {
        android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);
        int before=KeyboardSwitcher.getInstance().getMainKeyboardView().getHeight();
        // Simulate the shared Settings selector, without a manual keyboard reload.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->prefs.edit().putBoolean("petal_large_keys",true).apply());SystemClock.sleep(600);
        assertTrue(KeyboardSwitcher.getInstance().getKeyboard().mId.mPetalLargeKeys);
        assertTrue(KeyboardSwitcher.getInstance().getMainKeyboardView().getHeight()>before);
        field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,0,null);
        assertTrue(KeyboardSwitcher.getInstance().getKeyboard().mId.mPetalLargeKeys);
        assertQwertyRows(KeyboardSwitcher.getInstance().getKeyboard());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->prefs.edit().putBoolean("petal_large_keys",false).apply());SystemClock.sleep(600);
        assertFalse(KeyboardSwitcher.getInstance().getKeyboard().mId.mPetalLargeKeys);
        assertEquals(before,KeyboardSwitcher.getInstance().getMainKeyboardView().getHeight());
    }

    @Test public void testSensitiveClipboardPreviewAndToolsBackNavigation() throws Exception {
        field(android.text.InputType.TYPE_CLASS_TEXT,0,null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.content.ClipData clip=android.content.ClipData.newPlainText("synthetic","synthetic private token");
            android.os.PersistableBundle extras=new android.os.PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);
            ((android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)).setPrimaryClip(clip);
        });
        openTools();android.widget.PopupWindow popup=toolsWindow();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            labelled(popup.getContentView(),"Clipboard").performClick();
            assertNotNull(labelled(popup.getContentView(),activity.getString(R.string.petal_clipboard_sensitive)));
            assertNull(labelled(popup.getContentView(),"synthetic private token"));
            PetalAccessibilityChecks.check(popup.getContentView(),"sensitive-clipboard");
            labelled(popup.getContentView(),"Back to tools").performClick();assertNotNull(labelled(popup.getContentView(),"Settings"));
            labelled(popup.getContentView(),"Themes").performClick();labelled(popup.getContentView(),"Back to tools").performClick();assertNotNull(labelled(popup.getContentView(),"Clipboard"));popup.dismiss();
        });
    }

    private static void collectGarbage() {
        for(int n=0;n<3;n++){Runtime.getRuntime().gc();System.runFinalization();SystemClock.sleep(150);}
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private java.lang.ref.WeakReference<android.view.View> dismissedToolsRoot() throws Exception {
        openTools();android.widget.PopupWindow shown=toolsWindow();
        java.lang.ref.WeakReference<android.view.View> reference=new java.lang.ref.WeakReference<>(shown.getContentView());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(shown::dismiss);
        Field f=LatinIME.class.getDeclaredField("petalToolsPopup");f.setAccessible(true);Object controller=f.get(ime);
        Field active=controller.getClass().getDeclaredField("active");active.setAccessible(true);assertNull("Outside/Back dismissal must release popup",active.get(controller));
        return reference;
    }
    private java.lang.ref.WeakReference<android.view.View> cancelledVoiceRoot() throws Exception {
        PetalVoiceInput voice=new PetalVoiceInput(ime);
        java.lang.reflect.Method show=PetalVoiceInput.class.getDeclaredMethod("showPanel",android.view.View.class);show.setAccessible(true);
        Field popup=PetalVoiceInput.class.getDeclaredField("popup");popup.setAccessible(true);
        final java.lang.ref.WeakReference<android.view.View>[] ref=new java.lang.ref.WeakReference[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{show.invoke(voice,KeyboardSwitcher.getInstance().getMainKeyboardView());ref[0]=new java.lang.ref.WeakReference<>(((android.widget.PopupWindow)popup.get(voice)).getContentView());voice.cancel();assertNull(popup.get(voice));}catch(Exception e){throw new RuntimeException(e);}});
        return ref[0];
    }
    @Test public void testPopupMemoryStressWithoutAudio() throws Exception {
        java.util.List<java.lang.ref.WeakReference<android.view.View>> retired=new java.util.ArrayList<>();
        StringBuilder evidence=new StringBuilder("cycles,java_used_bytes,native_allocated_bytes,total_pss_kb\n");
        int maximum=Integer.parseInt(InstrumentationRegistry.getArguments().getString("petalMemoryCycles","40"));assertTrue(maximum>=40 && maximum<=500);
        for(int cycle=0;cycle<=maximum;cycle++){
            retired.add(dismissedToolsRoot());retired.add(cancelledVoiceRoot());
            if(cycle%10==0){collectGarbage();evidence.append(cycle).append(',').append(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()).append(',').append(android.os.Debug.getNativeHeapAllocatedSize()).append(',').append(android.os.Debug.getPss()).append('\n');}
        }
        for(int tries=0;tries<12;tries++){collectGarbage();boolean alive=false;for(java.lang.ref.WeakReference<android.view.View> ref:retired)if(ref.get()!=null)alive=true;if(!alive)break;}
        int alive=0;for(java.lang.ref.WeakReference<android.view.View> ref:retired)if(ref.get()!=null)alive++;
        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"memory-popup-stress.csv"))){out.write(evidence.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        assertEquals("Dismissed tools and cancelled voice roots retained after settling",0,alive);
    }
    @Test public void testKeyboardCacheMetadataAndMemoryPressure() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                KeyboardSwitcher switcher=KeyboardSwitcher.getInstance();Field sets=KeyboardSwitcher.class.getDeclaredField("mKeyboardLayoutSet");sets.setAccessible(true);
                Object set=sets.get(switcher);Field paramsField=KeyboardLayoutSet.class.getDeclaredField("mParams");paramsField.setAccessible(true);KeyboardLayoutSet.Params params=(KeyboardLayoutSet.Params)paramsField.get(set);
                Field editor=KeyboardLayoutSet.Params.class.getDeclaredField("mEditorInfo");editor.setAccessible(true);Object previous=editor.get(params);
                android.view.inputmethod.EditorInfo synthetic=new android.view.inputmethod.EditorInfo();synthetic.inputType=android.text.InputType.TYPE_CLASS_TEXT;synthetic.imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEND;synthetic.actionLabel=new android.text.SpannableString("Synthetic action");synthetic.extras=new android.os.Bundle();synthetic.extras.putString("synthetic","not for cache");synthetic.packageName="synthetic.package";synthetic.initialSelStart=10;synthetic.initialSelEnd=10;if(android.os.Build.VERSION.SDK_INT>=30)synthetic.setInitialSurroundingText("synthetic surrounding text");
                KeyboardId id;
                try{editor.set(params,synthetic);id=new KeyboardId(KeyboardId.ELEMENT_ALPHABET,params);}finally{editor.set(params,previous);}
                assertNotSame(synthetic,id.mEditorInfo);assertNull(id.mEditorInfo.extras);assertNull(id.mEditorInfo.packageName);assertEquals("Synthetic action",id.mEditorInfo.actionLabel);assertTrue(id.mEditorInfo.actionLabel instanceof String);assertEquals(synthetic.imeOptions,id.mEditorInfo.imeOptions);assertEquals(synthetic.inputType,id.mEditorInfo.inputType);
                if(android.os.Build.VERSION.SDK_INT>=30)assertNull(id.mEditorInfo.getInitialTextBeforeCursor(100,0));
                Field cache=KeyboardLayoutSet.class.getDeclaredField("sKeyboardCache");cache.setAccessible(true);Field forced=KeyboardLayoutSet.class.getDeclaredField("sForcibleKeyboardCache");forced.setAccessible(true);
                assertFalse(((java.util.Map<?,?>)cache.get(null)).isEmpty());ime.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);assertTrue(((java.util.Map<?,?>)cache.get(null)).isEmpty());for(Object value:(Object[])forced.get(null))assertNull(value);
                ime.loadKeyboard();
            }catch(Exception e){throw new RuntimeException(e);}
        });type("safe");assertEquals("safe",text());
    }
    @Test public void testLegacyHistoryCannotRetainOrReadClipboard() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            ClipboardHistoryManager manager=new ClipboardHistoryManager(ime);manager.onCreate();manager.onPrimaryClipChanged();manager.prepareClipboardHistory();manager.clearHistory();manager.toggleClipPinned(123);assertEquals(0,manager.getHistorySize());assertEquals("",manager.retrieveClipboardContent());manager.onDestroy();
            for(Field f:ClipboardHistoryManager.class.getDeclaredFields())assertFalse("Legacy manager must not retain owner/listeners/history",!java.lang.reflect.Modifier.isStatic(f.getModifiers()));
        });
    }

    private java.util.List<java.lang.ref.WeakReference<Object>> retireServiceForMemoryTest() throws Exception {
        java.util.List<java.lang.ref.WeakReference<Object>> retired=new java.util.ArrayList<>();
        LatinIME old=ime;retired.add(new java.lang.ref.WeakReference<>(old));retired.add(new java.lang.ref.WeakReference<>(KeyboardSwitcher.getInstance().getMainKeyboardView()));
        shell("ime disable dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        Field owner=KeyboardSwitcher.class.getDeclaredField("mLatinIME");owner.setAccessible(true);
        for(int n=0;n<100&&owner.get(old.mKeyboardSwitcher)!=null;n++)SystemClock.sleep(100);
        assertNull(owner.get(old.mKeyboardSwitcher));ime=null;return retired;
    }
    @Test public void testDestroyedServiceAndViewCanBeCollected() throws Exception {
        java.util.List<java.lang.ref.WeakReference<Object>> retired=retireServiceForMemoryTest();
        shell("ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");shell("ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");awaitImeReady();
        for(int n=0;n<20;n++){collectGarbage();if(retired.get(0).get()==null&&retired.get(1).get()==null)break;}
        assertNull("Destroyed IME retained",retired.get(0).get());assertNull("Destroyed keyboard view retained",retired.get(1).get());
    }

    private static void collectAnimatedDrawables(android.view.View view,java.util.List<android.graphics.drawable.Animatable> images){
        if(view instanceof android.widget.ImageView){android.graphics.drawable.Drawable d=((android.widget.ImageView)view).getDrawable();if(d instanceof android.graphics.drawable.Animatable)images.add((android.graphics.drawable.Animatable)d);}
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectAnimatedDrawables(group.getChildAt(i),images);}
    }
    private java.lang.ref.WeakReference<android.view.View> dismissedMediaRoot() throws Exception {
        PetalMediaPopup media=new PetalMediaPopup(ime);Field active=PetalMediaPopup.class.getDeclaredField("active");active.setAccessible(true);
        final java.lang.ref.WeakReference<android.view.View>[] root=new java.lang.ref.WeakReference[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
            media.show(KeyboardSwitcher.getInstance().getMainKeyboardView());}catch(Exception e){throw new RuntimeException(e);}});
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{android.widget.PopupWindow shown=(android.widget.PopupWindow)active.get(media);root[0]=new java.lang.ref.WeakReference<>(shown.getContentView());
            java.util.List<android.graphics.drawable.Animatable> images=new java.util.ArrayList<>();collectAnimatedDrawables(shown.getContentView(),images);assertEquals("Both bundled animated previews decode",2,images.size());shown.dismiss();assertNull(active.get(media));for(android.graphics.drawable.Animatable image:images)assertFalse("Dismissed GIF still animating",image.isRunning());
        }catch(Exception e){throw new RuntimeException(e);}});return root[0];
    }
    @Test public void testLegacyMediaDismissReleasesAnimationsAndViews() throws Exception {
        java.lang.ref.WeakReference<android.view.View> root=dismissedMediaRoot();
        for(int n=0;n<6;n++)collectGarbage();assertNull("Dismissed legacy media root retained",root.get());
    }

    /** Rotation coalescing must never defer revoking a voice session. No audio is opened. */
    private void voiceMustCancelBeforeDeferredInput(String event) throws Exception {
        Field voiceField=LatinIME.class.getDeclaredField("petalVoice");voiceField.setAccessible(true);
        Field gateField=PetalVoiceInput.class.getDeclaredField("gate");gateField.setAccessible(true);
        Field popupField=PetalVoiceInput.class.getDeclaredField("popup");popupField.setAccessible(true);
        Field handlerField=PetalVoiceInput.class.getDeclaredField("handler");handlerField.setAccessible(true);
        Field timeoutField=PetalVoiceInput.class.getDeclaredField("timeout");timeoutField.setAccessible(true);
        Field pendingField=LatinIME.UIHandler.class.getDeclaredField("MSG_PENDING_IMS_CALLBACK");pendingField.setAccessible(true);int pending=pendingField.getInt(null);
        java.lang.reflect.Method show=PetalVoiceInput.class.getDeclaredMethod("showPanel",android.view.View.class);show.setAccessible(true);
        PetalVoiceInput voice=new PetalVoiceInput(ime);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                Object previous=voiceField.get(ime);voiceField.set(ime,voice);
                PetalDictationSession gate=(PetalDictationSession)gateField.get(voice);
                android.os.Handler voiceHandler=(android.os.Handler)handlerField.get(voice);Runnable timeout=(Runnable)timeoutField.get(voice);
                try {
                    show.invoke(voice,KeyboardSwitcher.getInstance().getMainKeyboardView());
                    gate.begin(ime.getCurrentInputConnection(),true);assertTrue(gate.active());voiceHandler.postDelayed(timeout,60000);
                    ime.mHandler.sendEmptyMessageDelayed(pending,60000);
                    android.view.inputmethod.EditorInfo info=ime.getCurrentInputEditorInfo();
                    switch(event){
                        case "start":ime.onStartInput(info,true);break;
                        case "startView":ime.onStartInputView(info,true);break;
                        case "finish":ime.onFinishInput();break;
                        case "finishView":ime.onFinishInputView(false);break;
                        default:throw new AssertionError(event);
                    }
                    assertFalse(event+" must revoke voice before deferred keyboard work",gate.active());
                    assertNull(event+" must release the voice popup immediately",popupField.get(voice));
                    assertFalse(event+" must remove the voice timeout",voiceHandler.hasCallbacks(timeout));
                } finally {voice.cancel();ime.mHandler.removeMessages(pending);voiceField.set(ime,previous);}
            }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        });
    }
    @Test public void testVoiceCancelledBeforeDeferredStartInput() throws Exception {voiceMustCancelBeforeDeferredInput("start");}
    @Test public void testVoiceCancelledBeforeDeferredStartInputView() throws Exception {voiceMustCancelBeforeDeferredInput("startView");}
    @Test public void testVoiceCancelledBeforeDeferredFinishInput() throws Exception {voiceMustCancelBeforeDeferredInput("finish");}
    @Test public void testVoiceCancelledBeforeDeferredFinishInputView() throws Exception {voiceMustCancelBeforeDeferredInput("finishView");}

    private void unavailableSurroundingTextMustNotMoveCursor(int direction) throws Exception {
        dev.fourwags.sumi.inputmethod.latin.inputlogic.InputLogic input=logic();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{activity.editor.setText("synthetic");activity.editor.setSelection(4);});SystemClock.sleep(300);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            activity.refusesSurroundingText=true;
            try {
                input.finishInput();
                // Simulate a cache miss against an editor that cannot supply context.
                Field cache=RichInputConnection.class.getDeclaredField("mCommittedTextBeforeComposingText");cache.setAccessible(true);((StringBuilder)cache.get(input.mConnection)).setLength(0);
                Field composing=RichInputConnection.class.getDeclaredField("mComposingText");composing.setAccessible(true);((StringBuilder)composing.get(input.mConnection)).setLength(0);
                input.mConnection.mExpectedSelStart=4;
                assertNull(direction<0?input.mConnection.getTextBeforeCursor(64,0):input.mConnection.getTextAfterCursor(64,0));
                ime.onMovePointer(direction);assertEquals(4,activity.editor.getSelectionStart());assertEquals("synthetic",activity.editor.getText().toString());
            }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            finally {activity.refusesSurroundingText=false;}
        });
    }
    @Test public void testCursorSwipeWithUnavailableBeforeText() throws Exception {unavailableSurroundingTextMustNotMoveCursor(-1);}
    @Test public void testCursorSwipeWithUnavailableAfterText() throws Exception {unavailableSurroundingTextMustNotMoveCursor(1);}
    @Test public void testCursorSwipeStillMovesInBothDirections() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{activity.editor.setText("synthetic");activity.editor.setSelection(4);});SystemClock.sleep(300);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.onMovePointer(-2));SystemClock.sleep(200);
        assertEquals(2,activity.editor.getSelectionStart());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.onMovePointer(2));SystemClock.sleep(200);
        assertEquals(4,activity.editor.getSelectionStart());assertEquals("synthetic",text());
    }

    private void selectionFailureMustPreserveExpectedCursor(boolean disconnected) throws Exception {
        dev.fourwags.sumi.inputmethod.latin.inputlogic.InputLogic input=logic();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{activity.editor.setText("synthetic");activity.editor.setSelection(4);});SystemClock.sleep(300);
        Field ic=RichInputConnection.class.getDeclaredField("mIC");ic.setAccessible(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                input.finishInput();android.view.inputmethod.InputConnection original=(android.view.inputmethod.InputConnection)ic.get(input.mConnection);
                input.mConnection.mExpectedSelStart=4;input.mConnection.mExpectedSelEnd=4;
                android.view.inputmethod.InputConnection reject=new android.view.inputmethod.InputConnectionWrapper(ime.getCurrentInputConnection(),false){@Override public boolean setSelection(int start,int end){return false;}};
                try {
                    ic.set(input.mConnection,disconnected?null:reject);
                    assertFalse("Unaccepted selection must report failure",input.mConnection.setSelection(2,4));
                    assertEquals("No phantom selection start",4,input.mConnection.getExpectedSelectionStart());
                    assertEquals("No phantom selection end",4,input.mConnection.getExpectedSelectionEnd());
                    assertFalse(input.mConnection.hasSelection());assertEquals(4,activity.editor.getSelectionStart());assertEquals("synthetic",activity.editor.getText().toString());
                }finally {ic.set(input.mConnection,original);input.mConnection.mExpectedSelStart=4;input.mConnection.mExpectedSelEnd=4;}
            }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        });
    }
    @Test public void testRejectedSelectionDoesNotCreatePhantomRange() throws Exception {selectionFailureMustPreserveExpectedCursor(false);}
    @Test public void testDisconnectedSelectionDoesNotClaimSuccess() throws Exception {selectionFailureMustPreserveExpectedCursor(true);}

    private android.graphics.Bitmap drawHeightPreview(PetalHeightPreview preview) throws Exception {
        int width=600;preview.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(400,android.view.View.MeasureSpec.AT_MOST));
        preview.layout(0,0,width,preview.getMeasuredHeight());
        android.graphics.Bitmap snapshot=android.graphics.Bitmap.createBitmap(width,Math.max(1,preview.getMeasuredHeight()),android.graphics.Bitmap.Config.ARGB_8888);
        try{preview.draw(new android.graphics.Canvas(snapshot));}finally{snapshot.recycle();}
        Field renderer=PetalHeightPreview.class.getDeclaredField("renderer");renderer.setAccessible(true);
        Field buffer=KeyboardView.class.getDeclaredField("mOffscreenBuffer");buffer.setAccessible(true);
        android.graphics.Bitmap bitmap=(android.graphics.Bitmap)buffer.get(renderer.get(preview));assertNotNull("Software preview must allocate a buffer",bitmap);assertFalse(bitmap.isRecycled());return bitmap;
    }
    private void heightPreviewMustReleaseBuffer(boolean detach) throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            PetalHeightPreview preview=new PetalHeightPreview(activity,android.content.res.Configuration.ORIENTATION_PORTRAIT);
            android.widget.FrameLayout container=(android.widget.FrameLayout)activity.findViewById(android.R.id.content);
            try {
                container.addView(preview,new android.widget.FrameLayout.LayoutParams(600,400));preview.update(100);
                android.graphics.Bitmap old=drawHeightPreview(preview);
                if(detach)container.removeView(preview);else preview.update(110);
                assertTrue(detach?"Detached preview must release its offscreen bitmap":"Replaced preview must release its old offscreen bitmap",old.isRecycled());
                if(detach){
                    Field renderer=PetalHeightPreview.class.getDeclaredField("renderer");renderer.setAccessible(true);assertNull(renderer.get(preview));assertNull(preview.getPreviewKeyboard());
                    container.addView(preview,new android.widget.FrameLayout.LayoutParams(600,400));assertNotNull("Reattached preview must rebuild",drawHeightPreview(preview));
                }else assertNotNull(drawHeightPreview(preview));
            }catch(ReflectiveOperationException e){throw new RuntimeException(e);}catch(Exception e){throw new RuntimeException(e);}
            finally {container.removeView(preview);}
        });
    }
    @Test public void testHeightPreviewRebuildReleasesOldBitmap() throws Exception {heightPreviewMustReleaseBuffer(false);}
    @Test public void testHeightPreviewDetachReleasesAndReattachRebuilds() throws Exception {heightPreviewMustReleaseBuffer(true);}
    @Test public void testHeightPreviewRepeatedResizeReleasesEveryRetiredBuffer() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            PetalHeightPreview preview=new PetalHeightPreview(activity,android.content.res.Configuration.ORIENTATION_PORTRAIT);
            android.widget.FrameLayout container=(android.widget.FrameLayout)activity.findViewById(android.R.id.content);long releasedBytes=0;int retired=0;
            try {
                container.addView(preview,new android.widget.FrameLayout.LayoutParams(600,400));preview.update(100);android.graphics.Bitmap previous=drawHeightPreview(preview);
                for(int n=0;n<60;n++){
                    int allocation=previous.getAllocationByteCount();preview.update(SumiKeyboardHeight.MIN+n%(SumiKeyboardHeight.MAX-SumiKeyboardHeight.MIN+1));
                    assertTrue("Retired bitmap must be recycled at resize "+n,previous.isRecycled());releasedBytes+=allocation;retired++;previous=drawHeightPreview(preview);
                }
                int allocation=previous.getAllocationByteCount();container.removeView(preview);assertTrue(previous.isRecycled());releasedBytes+=allocation;retired++;
                try(java.io.FileWriter log=new java.io.FileWriter(new java.io.File(activity.getExternalFilesDir(null),"height-preview-buffer-stress.txt"))){log.write("retired_buffers="+retired+"\nreleased_bitmap_bytes="+releasedBytes+"\n");}
            }catch(Exception e){throw new RuntimeException(e);}finally{container.removeView(preview);}
        });
    }

    private static String syntheticRepeat(String value,int count){StringBuilder result=new StringBuilder();for(int n=0;n<count;n++)result.append(value);return result.toString();}
    private RichInputConnection isolatedConnection(java.util.List<CharSequence> sent) throws Exception {
        RichInputConnection connection=new RichInputConnection(ime);connection.mExpectedSelStart=0;connection.mExpectedSelEnd=0;
        Field field=RichInputConnection.class.getDeclaredField("mIC");field.setAccessible(true);
        field.set(connection,new android.view.inputmethod.BaseInputConnection(activity.editor,true){
            @Override public boolean commitText(CharSequence text,int cursor){if(sent!=null)sent.add(text);return true;}
            @Override public boolean finishComposingText(){return true;}
            @Override public boolean setComposingText(CharSequence text,int cursor){return true;}
            @Override public boolean commitCompletion(android.view.inputmethod.CompletionInfo value){return true;}
            @Override public boolean sendKeyEvent(android.view.KeyEvent event){return true;}
            @Override public boolean deleteSurroundingText(int before,int after){return true;}
        });return connection;
    }
    private StringBuilder committedCache(RichInputConnection connection) throws Exception {
        Field field=RichInputConnection.class.getDeclaredField("mCommittedTextBeforeComposingText");field.setAccessible(true);return (StringBuilder)field.get(connection);
    }
    @Test public void testPunctuationUndoRejectsShortContext() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                RichInputConnection connection=isolatedConnection(null);committedCache(connection).append("x");connection.mExpectedSelStart=1;connection.mExpectedSelEnd=1;
                assertFalse(connection.revertSwapPunctuation());assertEquals("x",committedCache(connection).toString());assertEquals(1,connection.getExpectedSelectionStart());
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    @Test public void testPunctuationUndoStillRevertsValidContext() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                java.util.List<CharSequence> sent=new java.util.ArrayList<>();RichInputConnection connection=isolatedConnection(sent);
                committedCache(connection).append(", ");connection.mExpectedSelStart=2;connection.mExpectedSelEnd=2;
                assertTrue(connection.revertSwapPunctuation());assertEquals(" ,",sent.get(0).toString());assertEquals(" ,",committedCache(connection).toString());assertEquals(2,connection.getExpectedSelectionStart());
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    @Test public void testCommittedContextCacheBoundsLargeCommitsAndUnicode() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                RichInputConnection connection=isolatedConnection(null);StringBuilder model=new StringBuilder();
                String chunk=syntheticRepeat("synthetic ",500);
                for(int n=0;n<20;n++){connection.commitText(chunk,1);model.append(chunk);}
                StringBuilder cache=committedCache(connection);assertTrue("Committed cache must stay bounded",cache.length()<=Constants.EDITOR_CONTENTS_CACHE_SIZE);
                assertTrue("Large paste must not leave a large cache allocation",cache.capacity()<=Constants.EDITOR_CONTENTS_CACHE_SIZE*2);
                assertEquals(model.substring(model.length()-64),connection.getTextBeforeCursor(64,0).toString());assertEquals(model.length(),connection.getExpectedSelectionStart());
                String boundary=syntheticRepeat("x",65536)+"\uD83C\uDF31"+syntheticRepeat("z",Constants.EDITOR_CONTENTS_CACHE_SIZE-1);
                connection.commitText(boundary,1);assertEquals(syntheticRepeat("z",Constants.EDITOR_CONTENTS_CACHE_SIZE-1),cache.toString());assertFalse(Character.isLowSurrogate(cache.charAt(0)));
                assertEquals(model.length()+boundary.length(),connection.getExpectedSelectionStart());
                try(java.io.FileWriter log=new java.io.FileWriter(new java.io.File(activity.getExternalFilesDir(null),"committed-cache-stress.txt"))){log.write("committed_chars="+connection.getExpectedSelectionStart()+"\ncache_chars="+cache.length()+"\ncache_capacity="+cache.capacity()+"\n");}
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    @Test public void testCommittedContextCacheBoundsCompositionCompletionAndKeys() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                RichInputConnection connection=isolatedConnection(null);StringBuilder model=new StringBuilder();String word=syntheticRepeat("synthetic ",8);
                for(int n=0;n<40;n++){connection.setComposingText(word,1);connection.finishComposingText();model.append(word);assertTrue("Composition cache bound",committedCache(connection).length()<=Constants.EDITOR_CONTENTS_CACHE_SIZE);}
                String completion=syntheticRepeat("completion ",500);connection.commitCompletion(new android.view.inputmethod.CompletionInfo(1,0,completion));model.append(completion);assertTrue("Completion cache bound",committedCache(connection).length()<=Constants.EDITOR_CONTENTS_CACHE_SIZE);
                for(int n=0;n<1200;n++){connection.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_ENTER));model.append('\n');assertTrue("Key-event cache bound",committedCache(connection).length()<=Constants.EDITOR_CONTENTS_CACHE_SIZE);}
                assertEquals(model.substring(model.length()-64),connection.getTextBeforeCursor(64,0).toString());assertEquals(model.length(),connection.getExpectedSelectionStart());
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    @Test public void testCommittedStyledPayloadRemainsStableAcrossCommits() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                java.util.List<CharSequence> sent=new java.util.ArrayList<>();RichInputConnection connection=isolatedConnection(sent);
                android.text.SpannableStringBuilder text=new android.text.SpannableStringBuilder("a\uD83C\uDF31b");android.text.style.StyleSpan span=new android.text.style.StyleSpan(android.graphics.Typeface.BOLD);text.setSpan(span,0,2,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                connection.commitText(text,1);CharSequence first=sent.get(0);assertEquals(3,((android.text.Spanned)first).getSpanEnd(span));
                connection.commitText(" next",1);assertEquals("An earlier payload must not be mutated by a later commit","a\uD83C\uDF31b",first.toString());assertEquals(3,((android.text.Spanned)first).getSpanEnd(span));assertEquals(" next",sent.get(1).toString());
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private java.lang.ref.WeakReference<Object> commitTemporaryStyledPayload(RichInputConnection connection){
        android.text.SpannableStringBuilder text=new android.text.SpannableStringBuilder("synthetic");Object span=new android.text.style.StyleSpan(android.graphics.Typeface.BOLD);text.setSpan(span,0,text.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);connection.commitText(text,1);return new java.lang.ref.WeakReference<>(span);
    }
    @Test public void testConnectionDoesNotRetainCommittedStyleObject() throws Exception {
        final RichInputConnection[] connection=new RichInputConnection[1];final java.lang.ref.WeakReference<?>[] retired=new java.lang.ref.WeakReference<?>[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{connection[0]=isolatedConnection(null);retired[0]=commitTemporaryStyledPayload(connection[0]);}catch(Exception e){throw new RuntimeException(e);}});
        for(int n=0;n<6;n++)collectGarbage();
        assertEquals("synthetic",committedCache(connection[0]).toString());assertNull("The connection must not retain a span from the last commit",retired[0].get());
    }


    private Object previewField(Object target,String name) throws ReflectiveOperationException {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private void previewLifecycleCase(int mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            MainKeyboardView view=ime.mKeyboardSwitcher.getMainKeyboardView();
            dev.fourwags.sumi.inputmethod.keyboard.internal.DrawingPreviewPlacerView placer=null;
            dev.fourwags.sumi.inputmethod.keyboard.internal.KeyPreviewChoreographer choreographer=null;
            java.lang.reflect.Method install=null;
            try {
                placer=(dev.fourwags.sumi.inputmethod.keyboard.internal.DrawingPreviewPlacerView)previewField(view,"mDrawingPreviewPlacerView");
                choreographer=(dev.fourwags.sumi.inputmethod.keyboard.internal.KeyPreviewChoreographer)previewField(view,"mKeyPreviewChoreographer");
                install=MainKeyboardView.class.getDeclaredMethod("installPreviewPlacerView");install.setAccessible(true);
                java.lang.reflect.Method detach=MainKeyboardView.class.getDeclaredMethod("onDetachedFromWindow");detach.setAccessible(true);
                Key a=view.getKeyboard().getKey('a'),b=view.getKeyboard().getKey('b');assertNotNull(a);assertNotNull(b);
                android.view.View first=choreographer.getKeyPreviewView(a,placer);
                java.lang.reflect.Method show=choreographer.getClass().getDeclaredMethod("showKeyPreview",Key.class,dev.fourwags.sumi.inputmethod.keyboard.internal.KeyPreviewView.class);show.setAccessible(true);show.invoke(choreographer,a,first);
                android.view.View showing=choreographer.getKeyPreviewView(b,placer);show.invoke(choreographer,b,showing);
                choreographer.dismissKeyPreview(a);
                android.view.View free=first;
                if(mode==0){
                    detach.invoke(view);
                    assertTrue("Detached keyboard must drop shown previews",((java.util.Map<?,?>)previewField(choreographer,"mShowingKeyPreviewViews")).isEmpty());
                    assertTrue("Detached keyboard must drop pooled previews",((java.util.Collection<?>)previewField(choreographer,"mFreeKeyPreviewViews")).isEmpty());
                    assertNull("Detached overlay must leave its old root",placer.getParent());
                }else if(mode==1){
                    // Framework can reattach the same keyboard instance to the same root.
                    detach.invoke(view);install.invoke(view);install.invoke(view);
                    assertNotNull(placer.getParent());
                }else{
                    choreographer.dismissKeyPreview(b);detach.invoke(view);
                    android.view.View replacement=choreographer.getKeyPreviewView(a,placer);
                    assertSame("Reused keyboard must attach fresh preview to overlay",placer,replacement.getParent());
                    assertNotSame("Detached preview must not survive in pool",free,replacement);
                }
            }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            finally {
                // Restore the fixture even when testing the unfixed lifecycle throws.
                try {
                    if(choreographer!=null){
                        ((java.util.Map<?,?>)previewField(choreographer,"mShowingKeyPreviewViews")).clear();
                        ((java.util.Collection<?>)previewField(choreographer,"mFreeKeyPreviewViews")).clear();
                    }
                    if(placer!=null){placer.removeAllViews();if(placer.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)placer.getParent()).removeView(placer);}
                    if(install!=null)install.invoke(view);
                }catch(Exception e){throw new RuntimeException(e);}
            }
        });
    }
    @Test public void testDetachedKeyboardDropsPreviewReferences(){previewLifecycleCase(0);}
    @Test public void testPreviewOverlayReinstallIsIdempotent(){previewLifecycleCase(1);}
    @Test public void testPreviewAfterDetachBelongsToOverlay(){previewLifecycleCase(2);}

    private void assertNoRecapitalizationText(dev.fourwags.sumi.inputmethod.latin.utils.RecapitalizeStatus status) throws Exception {
        assertFalse("Finished capitalization must not remain active",status.isStarted());
        assertTrue("Original selection reference must be cleared",((String)previewField(status,"mStringBefore")).isEmpty());
        assertTrue("Transformed selection reference must be cleared",((String)previewField(status,"mStringAfter")).isEmpty());
    }
    @Test public void testStoppedCapitalizationReleasesLargeSelection() throws Exception {
        dev.fourwags.sumi.inputmethod.latin.utils.RecapitalizeStatus status=new dev.fourwags.sumi.inputmethod.latin.utils.RecapitalizeStatus();
        String selected=syntheticRepeat("synthetic selection ",25000);
        status.start(0,selected.length(),selected,java.util.Locale.US,new int[]{32});status.rotate();
        assertTrue(status.isStarted());assertFalse(status.getRecapitalizedString().isEmpty());
        status.stop();assertNoRecapitalizationText(status);
    }
    @Test public void testDisabledCapitalizationClearsSelectionAndRestarts() throws Exception {
        dev.fourwags.sumi.inputmethod.latin.utils.RecapitalizeStatus status=new dev.fourwags.sumi.inputmethod.latin.utils.RecapitalizeStatus();
        status.start(2,11,"synthetic",java.util.Locale.US,new int[]{32});status.rotate();
        status.disable();assertFalse(status.mIsEnabled());assertNoRecapitalizationText(status);
        status.enable();status.start(4,12,"new word",java.util.Locale.US,new int[]{32});status.rotate();
        assertEquals("New Word",status.getRecapitalizedString());assertEquals(4,status.getNewCursorStart());assertEquals(12,status.getNewCursorEnd());
        status.stop();assertNoRecapitalizationText(status);
    }
    @Test public void testFinishInputReleasesCapitalizationSelection() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{
                dev.fourwags.sumi.inputmethod.latin.inputlogic.InputLogic input=logic();
                Field field=input.getClass().getDeclaredField("mRecapitalizeStatus");field.setAccessible(true);
                dev.fourwags.sumi.inputmethod.latin.utils.RecapitalizeStatus status=(dev.fourwags.sumi.inputmethod.latin.utils.RecapitalizeStatus)field.get(input);
                status.enable();status.start(0,9,"synthetic",java.util.Locale.US,new int[]{32});status.rotate();
                input.finishInput();assertNoRecapitalizationText(status);
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    @Test public void testActiveCapitalizationCycleAndTrimStillWork() throws Exception {
        dev.fourwags.sumi.inputmethod.latin.utils.RecapitalizeStatus status=new dev.fourwags.sumi.inputmethod.latin.utils.RecapitalizeStatus();
        status.start(3,17,"  alpha beta  ",java.util.Locale.US,new int[]{32});status.trim();
        assertEquals(5,status.getNewCursorStart());assertEquals(15,status.getNewCursorEnd());
        status.rotate();assertEquals("Alpha Beta",status.getRecapitalizedString());
        status.rotate();assertEquals("ALPHA BETA",status.getRecapitalizedString());
        status.rotate();assertEquals("alpha beta",status.getRecapitalizedString());
    }

    @Test public void testSyntheticTypingDispatchTiming() throws Exception {
        type("keyboard ");
        field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,0,null);
        String sample=syntheticRepeat("keyboard ",12);
        long[] dispatch=new long[sample.length()],roundTrip=new long[sample.length()];
        for(int n=0;n<sample.length();n++){
            final int index=n,code=sample.charAt(n);long start=SystemClock.elapsedRealtimeNanos();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                long before=SystemClock.elapsedRealtimeNanos();
                ime.onPressKey(code,0,true);ime.onCodeInput(code,Constants.NOT_A_COORDINATE,Constants.NOT_A_COORDINATE,false);ime.onReleaseKey(code,false);
                dispatch[index]=SystemClock.elapsedRealtimeNanos()-before;
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            roundTrip[n]=SystemClock.elapsedRealtimeNanos()-start;
        }
        assertEquals("Synthetic characters must remain ordered and complete",sample,text().toLowerCase(java.util.Locale.US));
        java.util.Arrays.sort(dispatch);java.util.Arrays.sort(roundTrip);
        String metrics="samples="+sample.length()+"\n";
        for(int percentile:new int[]{50,95,99,100}){
            int index=(int)Math.ceil(sample.length()*percentile/100.0)-1;
            metrics+="dispatch_p"+percentile+"_ns="+dispatch[index]+"\n";
            metrics+="harness_round_trip_p"+percentile+"_ns="+roundTrip[index]+"\n";
        }
        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"synthetic-typing-timing.txt"))){out.write(metrics.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    /** Synthetic preview must be revoked even when Android coalesces input callbacks. */
    private void toolsMustDismissBeforeDeferredInput(String event) throws Exception {
        Field toolsField=LatinIME.class.getDeclaredField("petalToolsPopup");toolsField.setAccessible(true);
        Field popupField=PetalToolsPopup.class.getDeclaredField("active");popupField.setAccessible(true);
        Field pendingField=LatinIME.UIHandler.class.getDeclaredField("MSG_PENDING_IMS_CALLBACK");pendingField.setAccessible(true);
        int pending=pendingField.getInt(null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                ime.petalShowTools(ime.mKeyboardSwitcher.getMainKeyboardView());
                PetalToolsPopup tools=(PetalToolsPopup)toolsField.get(ime);
                android.widget.PopupWindow window=(android.widget.PopupWindow)popupField.get(tools);
                assertNotNull(window);assertTrue(window.isShowing());
                // Model a private preview without reading or overwriting the system clipboard.
                android.view.ViewGroup content=(android.view.ViewGroup)((android.widget.ScrollView)
                        ((android.view.ViewGroup)window.getContentView()).getChildAt(1)).getChildAt(0);
                android.widget.TextView preview=new android.widget.TextView(ime);
                preview.setText("SYNTHETIC_PRIVATE_PREVIEW");content.addView(preview);
                ime.mHandler.sendEmptyMessageDelayed(pending,60000);
                android.view.inputmethod.EditorInfo info=ime.getCurrentInputEditorInfo();
                try {
                    switch(event){
                        case "start":ime.onStartInput(info,true);break;
                        case "startView":ime.onStartInputView(info,true);break;
                        case "finish":ime.onFinishInput();break;
                        case "finishView":ime.onFinishInputView(false);break;
                        case "hidden":ime.onWindowHidden();break;
                        default:throw new AssertionError(event);
                    }
                    assertNull(event+" must clear the tools owner before deferred input",popupField.get(tools));
                    assertFalse(event+" must dismiss the old window",window.isShowing());
                    assertEquals(event+" must detach private preview views",0,content.getChildCount());
                } finally {tools.dismiss();ime.mHandler.removeMessages(pending);}
            }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        });
    }
    @Test public void testToolsDismissBeforeDeferredStartInput() throws Exception {toolsMustDismissBeforeDeferredInput("start");}
    @Test public void testToolsDismissBeforeDeferredStartInputView() throws Exception {toolsMustDismissBeforeDeferredInput("startView");}
    @Test public void testToolsDismissBeforeDeferredFinishInput() throws Exception {toolsMustDismissBeforeDeferredInput("finish");}
    @Test public void testToolsDismissBeforeDeferredFinishInputView() throws Exception {toolsMustDismissBeforeDeferredInput("finishView");}
    @Test public void testToolsDismissWhenWindowHidden() throws Exception {toolsMustDismissBeforeDeferredInput("hidden");}

    private void endedSessionMustReleaseEditorState(boolean finishView) throws Exception {
        type("synthetic");
        final String original=text();
        dev.fourwags.sumi.inputmethod.latin.inputlogic.InputLogic input=logic();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                for(String name:new String[]{"mEnteredText","mWordBeingCorrectedByCursor"}) {
                    Field retained=input.getClass().getDeclaredField(name);retained.setAccessible(true);
                    retained.set(input,"SYNTHETIC_RETAINED_TEXT");
                }
                String[] messages={"MSG_UPDATE_SHIFT_STATE","MSG_UPDATE_SUGGESTION_STRIP",
                    "MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP","MSG_RESUME_SUGGESTIONS",
                    "MSG_RESUME_SUGGESTIONS_FOR_START_INPUT","MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED",
                    "MSG_RESET_CACHES","MSG_WAIT_FOR_DICTIONARY_LOAD"};
                for(String name:messages) {
                    Field message=LatinIME.UIHandler.class.getDeclaredField(name);message.setAccessible(true);
                    ime.mHandler.sendEmptyMessageDelayed(message.getInt(null),60000);
                }
                if(finishView)ime.onFinishInputViewInternal(false);else ime.onFinishInputInternal();
                for(String name:messages) {
                    Field message=LatinIME.UIHandler.class.getDeclaredField(name);message.setAccessible(true);
                    assertFalse("Ended input still owns "+name,ime.mHandler.hasMessages(message.getInt(null)));
                }
                assertFalse(input.mConnection.isConnected());
                for(String name:new String[]{"mCommittedTextBeforeComposingText","mComposingText"}) {
                    Field cache=RichInputConnection.class.getDeclaredField(name);cache.setAccessible(true);
                    assertEquals(name,"",cache.get(input.mConnection).toString());
                }
                for(String name:new String[]{"mEnteredText","mWordBeingCorrectedByCursor"}) {
                    Field retained=input.getClass().getDeclaredField(name);retained.setAccessible(true);
                    assertNull(name,retained.get(input));
                }
                assertTrue(input.mSuggestedWords.isEmpty());
                Field stripField=LatinIME.class.getDeclaredField("mSuggestionStripView");stripField.setAccessible(true);
                dev.fourwags.sumi.inputmethod.latin.suggestions.SuggestionStripView strip=
                    (dev.fourwags.sumi.inputmethod.latin.suggestions.SuggestionStripView)stripField.get(ime);
                Field words=strip.getClass().getDeclaredField("mSuggestedWords");words.setAccessible(true);
                assertTrue(((SuggestedWords)words.get(strip)).isEmpty());
                for(String name:new String[]{"mWordViews","mDebugInfoViews"}) {
                    Field views=strip.getClass().getDeclaredField(name);views.setAccessible(true);
                    for(Object view:(java.util.List<?>)views.get(strip))
                        assertEquals("Retired suggestion text", "",((android.widget.TextView)view).getText().toString());
                }
            }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        });
        assertEquals("Cleanup must preserve receiving editor content",original,text());
        field(1|0x20000|0x8000,0,null);
        type("again");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.petalPaste(" literal"));
        assertEquals("Restart and live composition-only reset must preserve literal paste","again literal",text().toLowerCase());
    }
    @Test public void testFinishInputReleasesEditorSessionAndRestarts() throws Exception {endedSessionMustReleaseEditorState(false);}
    @Test public void testFinishViewReleasesEditorSessionAndRestarts() throws Exception {endedSessionMustReleaseEditorState(true);}


    @Test public void testPersonalDogButtonRefreshesWithoutResettingTyping() throws Exception {
        android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);
        int old=PetalButtonIcon.selected(prefs); boolean had=prefs.contains(PetalButtonIcon.PREFERENCE);
        try {
            type("hello");
            for(boolean large:new boolean[]{false,true}) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->prefs.edit().putBoolean("petal_large_keys",large).apply());
                SystemClock.sleep(300);
                for(int choice=0;choice<3;choice++) {
                    final int selected=choice;
                    java.util.Map<String,?> before=new java.util.HashMap<>(prefs.getAll());before.remove(PetalButtonIcon.PREFERENCE);
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->PetalButtonIcon.select(prefs,selected));
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    assertEquals("hello",text());
                    java.util.Map<String,?> after=new java.util.HashMap<>(prefs.getAll());after.remove(PetalButtonIcon.PREFERENCE);assertEquals(before,after);
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                        android.widget.ImageButton button=ime.getWindow().getWindow().getDecorView().findViewById(R.id.suggestions_strip_other_key);
                        assertEquals(activity.getString(R.string.petal_tools_description),button.getContentDescription());
                        android.graphics.Bitmap actual=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
                        android.graphics.Bitmap expected=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
                        // Clone the displayed vector's constant state to avoid comparing a scaled
                        // UI raster cache against a freshly rasterized vector's antialias edges.
                        android.graphics.drawable.Drawable d=button.getDrawable().getConstantState().newDrawable(button.getResources()).mutate();
                        android.graphics.Rect bounds=new android.graphics.Rect(d.getBounds());
                        d.setBounds(0,0,64,64);d.draw(new android.graphics.Canvas(actual));d.setBounds(bounds);
                        android.graphics.drawable.Drawable e=activity.getDrawable(PetalButtonIcon.drawable(selected));e.setBounds(0,0,64,64);e.draw(new android.graphics.Canvas(expected));
                        long[] errors=new long[3];
                        for(int candidate=0;candidate<3;candidate++) {
                            expected.eraseColor(android.graphics.Color.TRANSPARENT);
                            android.graphics.drawable.Drawable option=activity.getDrawable(PetalButtonIcon.drawable(candidate));
                            option.setBounds(0,0,64,64);option.draw(new android.graphics.Canvas(expected));
                            for(int y=0;y<64;y++)for(int x=0;x<64;x++) {
                                int actualPixel=actual.getPixel(x,y),expectedPixel=expected.getPixel(x,y),difference=0;
                                for(int shift:new int[]{0,8,16,24})difference=Math.max(difference,Math.abs(((actualPixel>>>shift)&255)-((expectedPixel>>>shift)&255)));
                                errors[candidate]+=difference;
                            }
                        }
                        // Measured framework cache resampling averaged5.45/255 difference;
                        // require a close visual match and uniquely the selected silhouette.
                        assertTrue("Displayed dog differs substantially",errors[selected]<4096L*12);
                        for(int candidate=0;candidate<3;candidate++)if(candidate!=selected)
                            assertTrue("Wrong dog painted: selected="+selected+" other="+candidate,errors[selected]<errors[candidate]);
                        saveToolsView(button,"dog-button-"+selected+"-"+large);
                        actual.recycle();expected.recycle();
                    });
                    openTools();android.widget.PopupWindow popup=toolsWindow();
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->labelled(popup.getContentView(),"Close tools").performClick());
                    assertEquals("hello",text());
                }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.content.SharedPreferences.Editor e=prefs.edit();if(had)e.putInt(PetalButtonIcon.PREFERENCE,old);else e.remove(PetalButtonIcon.PREFERENCE);e.putBoolean("petal_large_keys",false).apply();
            });
        }
    }

    private android.graphics.Rect personalMediaScreenBounds(android.view.View view){int[] xy=new int[2];view.getLocationOnScreen(xy);return new android.graphics.Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());}
    @Test public void testPersonalMediaEntryAndSendBoundToProtectedFieldPolicy() throws Exception {
        byte[] bytes;try(java.io.InputStream in=activity.getAssets().open("bloom.gif")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);bytes=out.toByteArray();}
        String id=PetalPersonalMediaStore.importStream(activity,new java.io.ByteArrayInputStream(bytes),new java.util.concurrent.atomic.AtomicBoolean());
        try {
            activity.acceptsGif=true;field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);
            openTools();android.widget.PopupWindow tools=toolsWindow();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{android.view.View entry=labelled(tools.getContentView(),"My media");assertNotNull(entry);assertTrue(entry.isEnabled());assertTrue(entry.performClick());});SystemClock.sleep(400);
            assertFalse(tools.isShowing());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.view.View media=ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_keyboard_personal_media);assertNotNull(media);assertTrue("Personal media action visible",media.isShown());assertTrue("Personal media width="+media.getWidth(),media.getWidth()>=Math.round(48*activity.getResources().getDisplayMetrics().density));assertTrue("Personal media height="+media.getHeight(),media.getHeight()>=Math.round(48*activity.getResources().getDisplayMetrics().density));
            });
            java.lang.reflect.Field owner=LatinIME.class.getDeclaredField("petalPersonalMedia");owner.setAccessible(true);PetalPersonalMediaController controller=(PetalPersonalMediaController)owner.get(ime);assertNotNull(controller);
            java.lang.reflect.Field window=PetalPersonalMediaController.class.getDeclaredField("popup");window.setAccessible(true);android.widget.PopupWindow mediaPopup=(android.widget.PopupWindow)window.get(controller);assertNotNull(mediaPopup);assertTrue(mediaPopup.isShowing());
            final boolean[] loaded={false};for(int n=0;n<100&&!loaded[0];n++){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->loaded[0]=labelled(mediaPopup.getContentView(),"GIF 1")!=null);SystemClock.sleep(50);}
            assertTrue("Imported image needs a selectable gallery item",loaded[0]);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.graphics.Rect keyboard=personalMediaScreenBounds(ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_palettes_view)),gallery=personalMediaScreenBounds(mediaPopup.getContentView());
                assertTrue("Gallery stays within keyboard: keyboard="+keyboard+" gallery="+gallery,gallery.top>=keyboard.top&&gallery.bottom<=keyboard.bottom);
                assertNotNull(labelled(mediaPopup.getContentView(),"Import & manage"));
                saveToolsView(mediaPopup.getContentView(),"synthetic-personal-gallery");
                assertTrue(labelled(mediaPopup.getContentView(),"GIF 1").performClick());
            });
            for(int n=0;n<100&&activity.receivedMediaCount==0;n++){InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(50);}
            assertEquals(1,activity.receivedMediaCount);assertEquals("image/gif",activity.receivedMime);
            activity.acceptedImageTypes=new String[]{null,"invalid","IMAGE/GIF"};field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->controller.send(id));
            for(int n=0;n<100&&activity.receivedMediaCount<2;n++){InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(50);}assertEquals(2,activity.receivedMediaCount);
            field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,0,null);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{assertFalse(ime.petalMediaAllowed());controller.send(id);});SystemClock.sleep(200);assertEquals(2,activity.receivedMediaCount);
        } finally {PetalPersonalMediaStore.remove(activity,id);}
    }
    @Test public void testPersonalMediaLateCopyCannotCrossEditors() throws Exception {
        byte[] bytes;try(java.io.InputStream in=activity.getAssets().open("wave.gif")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);bytes=out.toByteArray();}
        String id=PetalPersonalMediaStore.importStream(activity,new java.io.ByteArrayInputStream(bytes),new java.util.concurrent.atomic.AtomicBoolean());
        try {
            activity.acceptsGif=true;field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,null);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ime.onPersonalMedia(ime.getWindow().getWindow().getDecorView()));
            java.lang.reflect.Field owner=LatinIME.class.getDeclaredField("petalPersonalMedia");owner.setAccessible(true);PetalPersonalMediaController controller=(PetalPersonalMediaController)owner.get(ime);assertNotNull(controller);
            java.lang.reflect.Field lockField=PetalPersonalMediaStore.class.getDeclaredField("LOCK");lockField.setAccessible(true);Object lock=lockField.get(null);
            synchronized(lock){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{controller.dismiss();controller.send(id);});field(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,0,null);}
            SystemClock.sleep(400);InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(0,activity.receivedMediaCount);
        } finally {PetalPersonalMediaStore.remove(activity,id);}
    }

    @Test public void testPersonalMediaLargeKeysGalleryBounds()throws Exception {
        android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{prefs.edit().putBoolean("petal_large_keys",true).apply();ime.loadKeyboard();});SystemClock.sleep(300);key(Constants.CODE_EMOJI);SystemClock.sleep(200);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.view.View button=ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_keyboard_personal_media);assertNotNull(button);assertTrue(button.isShown());assertTrue(button.getWidth()>=Math.round(48*activity.getResources().getDisplayMetrics().density));assertTrue(button.getHeight()>=Math.round(48*activity.getResources().getDisplayMetrics().density));assertTrue(button.performClick());
            });SystemClock.sleep(150);
            java.lang.reflect.Field owner=LatinIME.class.getDeclaredField("petalPersonalMedia");owner.setAccessible(true);PetalPersonalMediaController controller=(PetalPersonalMediaController)owner.get(ime);java.lang.reflect.Field window=PetalPersonalMediaController.class.getDeclaredField("popup");window.setAccessible(true);android.widget.PopupWindow popup=(android.widget.PopupWindow)window.get(controller);assertNotNull(popup);assertTrue(popup.isShowing());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.graphics.Rect keyboard=personalMediaScreenBounds(ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_palettes_view)),gallery=personalMediaScreenBounds(popup.getContentView());assertTrue("Large gallery: keyboard="+keyboard+" gallery="+gallery,gallery.top>=keyboard.top&&gallery.bottom<=keyboard.bottom);assertNotNull(labelled(popup.getContentView(),"Import & manage"));controller.dismiss();
            });assertFalse(popup.isShowing());
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->prefs.edit().putBoolean("petal_large_keys",false).apply());}
    }

    private static class SilenceFake implements PetalVoiceInput.Factory,PetalVoiceInput.Engine {
        int starts,stops,closes;android.speech.RecognitionListener listener;android.content.Intent request;
        public boolean available(android.content.Context c){return true;}
        public boolean permitted(android.content.Context c){return true;}
        public PetalVoiceInput.Engine create(android.content.Context c){return this;}
        public void start(android.content.Intent i,android.speech.RecognitionListener l){starts++;listener=l;request=i;}
        public void stop(){stops++;}public void close(){closes++;}
    }
    private android.os.Bundle syntheticVoice(String text) {
        android.os.Bundle b=new android.os.Bundle();b.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,new java.util.ArrayList<>(java.util.Arrays.asList(text)));return b;
    }
    @Test public void testSelectableSilenceStopsIgnoringProviderAndRepeatedPartials() throws Exception {
        for(int duration:new int[]{2000,4000,8000}) {
            SilenceFake fake=new SilenceFake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
            try {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    DeviceProtectedUtils.getSharedPreferences(activity).edit().putInt(PetalVoiceTiming.SILENCE,duration).apply();
                    voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());
                    fake.listener.onReadyForSpeech(new android.os.Bundle());fake.listener.onBeginningOfSpeech();
                    fake.listener.onPartialResults(syntheticVoice("synthetic final"));
                });
                assertEquals((long)duration,fake.request.getLongExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,0));
                // This fake ignores the hint, never emits onEndOfSpeech and repeats a stale partial.
                for(int elapsed=0;elapsed<duration-300;elapsed+=200) {
                    SystemClock.sleep(200);
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                        fake.listener.onPartialResults(syntheticVoice("synthetic final"));fake.listener.onRmsChanged(1);
                    });
                }
                assertEquals("Must respect the chosen pause",0,fake.stops);
                SystemClock.sleep(650);assertEquals("App watchdog stops ignored provider hint",1,fake.stops);
                android.speech.RecognitionListener retired=fake.listener;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    retired.onResults(syntheticVoice("synthetic final"));retired.onResults(syntheticVoice("duplicate"));voice.stop();
                });
                assertEquals(1,fake.closes);assertEquals(1,fake.starts);
            } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
        }
        assertEquals("synthetic finalsynthetic finalsynthetic final",text());
    }
    @Test public void testSilenceDeadlineSurvivesRestartsAndCancellationReleasesEverything() throws Exception {
        SilenceFake fake=new SilenceFake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
        Field h=PetalVoiceInput.class.getDeclaredField("handler"),check=PetalVoiceInput.class.getDeclaredField("silenceCheck"),gate=PetalVoiceInput.class.getDeclaredField("gate");
        for(Field f:new Field[]{h,check,gate})f.setAccessible(true);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());
                fake.listener.onBeginningOfSpeech();fake.listener.onEndOfSpeech();fake.listener.onResults(syntheticVoice("completed phrase"));
            });
            SystemClock.sleep(1050);assertEquals(2,fake.starts);
            android.speech.RecognitionListener retired=fake.listener;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onError(android.speech.SpeechRecognizer.ERROR_NO_MATCH));
            SystemClock.sleep(1200);
            assertEquals("Timeout cannot be reset by provider retries",3,fake.starts);
            assertEquals(1,fake.stops);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onResults(new android.os.Bundle()));
            assertEquals("completed phrase",text());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->retired.onResults(syntheticVoice("stale")));
            assertEquals("completed phrase",text());
            assertFalse(((PetalDictationSession)gate.get(voice)).active());
            assertFalse(((android.os.Handler)h.get(voice)).hasCallbacks((Runnable)check.get(voice)));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());voice.cancel();});
            assertEquals(fake.starts,fake.closes);
            assertFalse(((android.os.Handler)h.get(voice)).hasCallbacks((Runnable)check.get(voice)));
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }
    @Test public void testActiveSilenceSessionCancelledOnWindowHiddenWithoutAudio() throws Exception {
        SilenceFake fake=new SilenceFake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
        Field owner=LatinIME.class.getDeclaredField("petalVoice");owner.setAccessible(true);Object old=owner.get(ime);
        Field h=PetalVoiceInput.class.getDeclaredField("handler"),check=PetalVoiceInput.class.getDeclaredField("silenceCheck");h.setAccessible(true);check.setAccessible(true);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{owner.set(ime,voice);}catch(Exception e){throw new RuntimeException(e);}
                voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());ime.onWindowHidden();
                fake.listener.onResults(syntheticVoice("late background result"));
            });
            assertEquals(1,fake.closes);assertEquals("",text());
            assertFalse(((android.os.Handler)h.get(voice)).hasCallbacks((Runnable)check.get(voice)));
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);owner.set(ime,old);}
    }
    @Test public void testSuggestionScalesHaveDistinctUnclippedTapAreas() throws Exception {
        Field wordsField=dev.fourwags.sumi.inputmethod.latin.suggestions.SuggestionStripView.class.getDeclaredField("mWordViews");wordsField.setAccessible(true);
        for(float fontScale:new float[]{1f,2f}) {
        int prior=0;
        for(int selected:new int[]{100,125,150}) {
            final dev.fourwags.sumi.inputmethod.latin.suggestions.SuggestionStripView[] view={null};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                DeviceProtectedUtils.getSharedPreferences(activity).edit().putInt(PetalTypingOptions.SUGGESTION_SIZE,selected).apply();
                view[0]=ime.getWindow().getWindow().getDecorView().findViewById(R.id.suggestion_strip_view);
                android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());config.fontScale=fontScale;
                android.content.Context themed=view[0].getContext().createConfigurationContext(config);themed.getTheme().setTo(view[0].getContext().getTheme());
                view[0]=new dev.fourwags.sumi.inputmethod.latin.suggestions.SuggestionStripView(themed,null);
                view[0].setListener(ime,ime.getWindow().getWindow().getDecorView());
                view[0].refreshSuggestionSize();
            });
            SystemClock.sleep(300);
            for(int widthDp:new int[]{320,400,700}) {
                final int width=Math.round(widthDp*activity.getResources().getDisplayMetrics().density);
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    java.util.ArrayList<SuggestedWords.SuggestedWordInfo> words=new java.util.ArrayList<>();
                    for(String word:new String[]{"syntheticlongwordalpha","syntheticlongwordbeta","syntheticlongwordgamma"})words.add(new SuggestedWords.SuggestedWordInfo(word,"",1,SuggestedWords.SuggestedWordInfo.KIND_PREDICTION,null,0,0));
                    SuggestedWords suggestions=new SuggestedWords(words,null,null,false,true,false,SuggestedWords.INPUT_STYLE_PREDICTION,0);
                    view[0].measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
                    view[0].layout(0,0,width,view[0].getMeasuredHeight());view[0].setSuggestions(suggestions,false);
                    view[0].measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
                    view[0].layout(0,0,width,view[0].getMeasuredHeight());
                    android.view.ViewGroup strip=view[0].findViewById(R.id.suggestions_strip);
                    int count=0,right=0;
                    for(int i=0;i<strip.getChildCount();i++)if(strip.getChildAt(i) instanceof android.widget.TextView){
                        android.widget.TextView word=(android.widget.TextView)strip.getChildAt(i);count++;
                        assertTrue(word.isClickable());assertTrue("Suggestion label must be visible",word.getText().length()>0);
                        assertTrue("Suggestion ink must be opaque",android.graphics.Color.alpha(word.getCurrentTextColor())==255);
                        assertTrue(word.getWidth()>=48*activity.getResources().getDisplayMetrics().density-1);
                        assertTrue(word.getHeight()>=48*activity.getResources().getDisplayMetrics().density-1);
                        assertTrue(word.getLeft()>=right);right=word.getRight();assertTrue(right<=strip.getWidth());
                        assertEquals(1f,word.getTextScaleX(),0.001f);assertEquals(1,word.getLineCount());
                        assertTrue(word.getContentDescription().toString().startsWith("syntheticlongword"));
                        android.graphics.Paint.FontMetricsInt fm=word.getPaint().getFontMetricsInt();assertTrue(word.getHeight()>=fm.bottom-fm.top+word.getCompoundPaddingTop()+word.getCompoundPaddingBottom());
                    }
                    assertEquals("Long suggestions retain all three choices",3,count);
                    assertTrue(view[0].findViewById(R.id.suggestions_strip_other_key).isClickable());
                    assertTrue(view[0].findViewById(R.id.suggestions_strip_voice_key).isClickable());
                    assertEquals(android.view.View.VISIBLE,view[0].findViewById(R.id.suggestions_strip_voice_key).getVisibility());
                    if(widthDp==400&&fontScale==1f)saveToolsView(view[0],"suggestion-size-"+selected);
                });
            }
            assertTrue(view[0].getHeight()>prior);prior=view[0].getHeight();
        }
        }
    }


    @Test public void testInitialSpeechWaitAndMissingFinalCallbackAreBoundedWithoutAudio() throws Exception {
        SilenceFake fake=new SilenceFake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
        Field gate=PetalVoiceInput.class.getDeclaredField("gate"),handler=PetalVoiceInput.class.getDeclaredField("handler"),check=PetalVoiceInput.class.getDeclaredField("silenceCheck");
        for(Field f:new Field[]{gate,handler,check})f.setAccessible(true);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView()));
            for(int n=0;n<12;n++){SystemClock.sleep(200);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onRmsChanged(9));}
            assertEquals("Initial speech wait is longer than the short pause",0,fake.stops);
            SystemClock.sleep(8100);assertEquals(1,fake.stops);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onError(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT));
            assertFalse(((PetalDictationSession)gate.get(voice)).active());assertEquals(1,fake.closes);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());
                fake.listener.onBeginningOfSpeech();fake.listener.onResults(syntheticVoice("completed only"));
                // A changed setting applies to the next activation, not a half-finished session.
                DeviceProtectedUtils.getSharedPreferences(activity).edit().putInt(PetalVoiceTiming.SILENCE,8000).apply();
            });
            SystemClock.sleep(2400);assertEquals(2,fake.stops);
            assertEquals(2000L,fake.request.getLongExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,0));
            SystemClock.sleep(5100);assertFalse(((PetalDictationSession)gate.get(voice)).active());
            assertEquals("completed only",text());assertEquals(fake.starts,fake.closes);
            assertFalse(((android.os.Handler)handler.get(voice)).hasCallbacks((Runnable)check.get(voice)));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView()));
            assertEquals(8000L,fake.request.getLongExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,0));
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }


    @Test public void testDelayedProviderEndpointDoesNotAddAnotherPauseWithoutAudio() throws Exception {
        SilenceFake fake=new SilenceFake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());fake.listener.onBeginningOfSpeech();
            });
            SystemClock.sleep(1200);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{fake.listener.onEndOfSpeech();fake.listener.onResults(syntheticVoice("one pause"));});
            SystemClock.sleep(1100);
            assertEquals("Late provider endpoint must not double the chosen pause",1,fake.stops);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onResults(new android.os.Bundle()));
            assertEquals("one pause",text());assertEquals(fake.starts,fake.closes);
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }


    @Test public void testSuggestionLabelsAndSelectionsOnLiveKeyboard() throws Exception {
        for(int selected:new int[]{100,125,150}) {
            final dev.fourwags.sumi.inputmethod.latin.suggestions.SuggestionStripView[] bar={null};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                DeviceProtectedUtils.getSharedPreferences(activity).edit().putInt(PetalTypingOptions.SUGGESTION_SIZE,selected).apply();
                bar[0]=ime.getWindow().getWindow().getDecorView().findViewById(R.id.suggestion_strip_view);
                bar[0].refreshSuggestionSize();
                java.util.ArrayList<SuggestedWords.SuggestedWordInfo> words=new java.util.ArrayList<>();
                for(String word:new String[]{"syntheticlongwordalpha","syntheticlongwordbeta","syntheticlongwordgamma"})words.add(new SuggestedWords.SuggestedWordInfo(word,"",1,SuggestedWords.SuggestedWordInfo.KIND_PREDICTION,null,0,0));
                bar[0].setSuggestions(new SuggestedWords(words,null,null,false,true,false,SuggestedWords.INPUT_STYLE_PREDICTION,0),false);
            });
            SystemClock.sleep(400);
            final String[] picked={null};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                android.view.ViewGroup strip=bar[0].findViewById(R.id.suggestions_strip);
                int shown=0;
                for(int i=0;i<strip.getChildCount();i++)if(strip.getChildAt(i) instanceof android.widget.TextView) {
                    android.widget.TextView word=(android.widget.TextView)strip.getChildAt(i);
                    assertTrue(word.isShown());assertTrue(word.getText().length()>0);
                    assertTrue(word.getLayout().getText().length()>0);shown++;
                }
                assertEquals(3,shown);saveToolsView(bar[0],"live-suggestion-size-"+selected);
                android.widget.TextView first=(android.widget.TextView)strip.getChildAt(0);
                picked[0]=first.getContentDescription().toString();first.performClick();
            });
            SystemClock.sleep(300);assertTrue("Ellipsis must commit the complete selected suggestion",text().contains(picked[0]));
        }
    }


    @Test public void testContinuousSpeechLevelsWithoutInitialNoiseAreNotCutOffWithoutAudio() throws Exception {
        SilenceFake fake=new SilenceFake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());fake.listener.onBeginningOfSpeech();
            });
            // Provider reports only speech levels, no partials and no pre-speech noise sample.
            for(int n=0;n<60;n++) {
                SystemClock.sleep(100);
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onRmsChanged(8));
                assertEquals("Continuing speech must not trigger the 2-second pause",0,fake.stops);
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onRmsChanged(0));
            SystemClock.sleep(2300);assertEquals(1,fake.stops);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->fake.listener.onResults(syntheticVoice("continuous speech")));
            assertEquals("continuous speech",text());assertEquals(1,fake.closes);
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);}
    }
    @Test public void testActiveVoiceClosesImmediatelyWhenInputUnbindsWithoutAudio() throws Exception {
        SilenceFake fake=new SilenceFake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);
        Field owner=LatinIME.class.getDeclaredField("petalVoice");owner.setAccessible(true);Object old=owner.get(ime);
        Field handler=PetalVoiceInput.class.getDeclaredField("handler"),check=PetalVoiceInput.class.getDeclaredField("silenceCheck");handler.setAccessible(true);check.setAccessible(true);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try{owner.set(ime,voice);}catch(Exception e){throw new RuntimeException(e);}
                voice.toggle(KeyboardSwitcher.getInstance().getMainKeyboardView());
                fake.listener.onBeginningOfSpeech();fake.listener.onPartialResults(syntheticVoice("discard on unbind"));
                ime.onUnbindInput();fake.listener.onResults(syntheticVoice("late result"));
            });
            assertEquals(1,fake.closes);assertEquals("",text());
            assertFalse(((android.os.Handler)handler.get(voice)).hasCallbacks((Runnable)check.get(voice)));
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(voice::cancel);owner.set(ime,old);}
    }

}
