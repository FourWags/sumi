package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;

public class PetalSettingsActivity extends Activity {
    public static final String[] ICONS = PetalThemeCatalog.NAMES;
    public static final int[] ICON_DRAWABLES = PetalThemeCatalog.ICONS;
    private android.content.SharedPreferences prefs;
    private Button enableKeyboard, microphonePermission;
    private final android.os.Handler setupHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean observingSetup;
    private final android.database.ContentObserver setupObserver=new android.database.ContentObserver(setupHandler) {
        @Override public void onChange(boolean selfChange) {if(observingSetup)refreshSetupState();}
    };
    private TextView keyboardStatus;
    private Spinner themeSelector, layoutSelector;
    private PetalThemePreview themePreview;
    private int INK=0xff30262c, ACCENT=0xff724151, PAPER=0xfffffaf4;
    private final java.util.Map<android.view.View,String> settingsSections=new java.util.LinkedHashMap<>();
    private final java.util.Map<String,ImageButton> navigation=new java.util.LinkedHashMap<>();
    private ScrollView settingsScroll;
    private TextView pageHeading;
    private ImageView companion;
    private String activePage="appearance";
    private static final String PAGE_STATE="sumi_settings_page";
    public static final String THEME_SELECTOR = "petal_theme_selector";
    public static final String LAYOUT_SELECTOR = "petal_layout_selector";
    private static final String[] LAYOUTS = {"Large keys", "Standard QWERTY"};
    private static final String[] LAYOUT_DETAILS = {
        "Enlarged QWERTY: familiar rows with taller keys and larger lettering.",
        "Regular-size English (US) QWERTY with a shorter keyboard."
    };
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = DeviceProtectedUtils.getSharedPreferences(this);
        PetalThemeCatalog.ensureSelection(this);
        FourWagsKeyIcon.initialize(this);
        boolean dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if(dark){INK=0xfff5e9e2;ACCENT=0xffdca9b6;PAPER=0xff2e292d;}
        int surface=dark?0xff211e20:0xfff5f0e9;
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.HORIZONTAL);shell.setBackgroundColor(surface);
        setContentView(shell);
        shell.setOnApplyWindowInsetsListener((v,insets)->{shell.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        ScrollView railScroll=new ScrollView(this);railScroll.setFillViewport(true);railScroll.setBackgroundColor(0xff29242a);
        shell.addView(railScroll,new LinearLayout.LayoutParams(dp(64),-1));
        LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.VERTICAL);rail.setGravity(android.view.Gravity.CENTER_HORIZONTAL);rail.setPadding(dp(7),dp(12),dp(7),dp(12));railScroll.addView(rail);
        companion=new ImageView(this);companion.setImageResource(PetalButtonIcon.drawable(PetalButtonIcon.selected(prefs)));companion.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams dogParams=new LinearLayout.LayoutParams(dp(34),dp(42));dogParams.bottomMargin=dp(16);rail.addView(companion,dogParams);
        String[] pages={"setup","appearance","typing","voice","privacy","about"};
        String[] labels={"Setup","Appearance","Typing","Voice","Privacy","About"};
        int[] icons={R.drawable.sumi_nav_setup,R.drawable.sumi_nav_palette,R.drawable.sumi_nav_keyboard,R.drawable.sumi_nav_voice,R.drawable.sumi_nav_privacy,R.drawable.sumi_nav_about};
        for(int i=0;i<pages.length;i++){
            final String page=pages[i];ImageButton nav=new ImageButton(this);nav.setTag("sumi_nav_"+page);nav.setContentDescription(labels[i]+" settings");nav.setTooltipText(labels[i]);nav.setImageResource(icons[i]);nav.setPadding(dp(13),dp(13),dp(13),dp(13));nav.setMinimumWidth(dp(50));nav.setMinimumHeight(dp(50));nav.setScaleType(ImageView.ScaleType.FIT_CENTER);nav.setOnClickListener(v->showPage(page,true));
            LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(dp(50),dp(50));navParams.bottomMargin=dp(8);rail.addView(nav,navParams);navigation.put(page,nav);
        }
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);shell.addView(content,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setPadding(dp(18),dp(14),dp(18),dp(14));content.addView(header);
        TextView owner=new TextView(this);owner.setText("FOUR WAGS");owner.setTextColor(dark?0xffc6b5bf:0xff74626b);owner.setTextSize(10);owner.setLetterSpacing(.16f);owner.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);header.addView(owner);
        TextView title=new TextView(this);title.setText("Sumi");title.setTextColor(INK);title.setTextSize(20);title.setTypeface(PetalFonts.sans(this,500));header.addView(title);
        android.view.View divider=new android.view.View(this);divider.setBackgroundColor(dark?0xff52444d:0xffded2d3);content.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        settingsScroll=new ScrollView(this);settingsScroll.setTag("sumi_settings_scroll");settingsScroll.setFillViewport(true);content.addView(settingsScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(16),dp(16),dp(24));settingsScroll.addView(box);
        pageHeading=new TextView(this);pageHeading.setTextSize(27);pageHeading.setTextColor(INK);pageHeading.setTypeface(PetalFonts.sans(this,500));pageHeading.setAccessibilityHeading(true);box.addView(pageHeading);
        LinearLayout root=box;
        box=section(root,"Get started","Use Sumi in your existing apps, including Google Messages.");
        enableKeyboard = button(box,"Enable keyboard", () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        keyboardStatus = new TextView(this);keyboardStatus.setTextColor(INK);keyboardStatus.setTextSize(15);keyboardStatus.setPadding(0,dp(8),0,dp(8));
        keyboardStatus.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        box.addView(keyboardStatus);
        button(box,"Choose keyboard", () -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        box=section(root,"Appearance & size","Choose familiar QWERTY in regular or enlarged sizes.");
        Spinner dogSelector = selector(box, "Shiba design", "sumi_keyboard_button_selector", PetalButtonIcon.NAMES, null, false);
        dogSelector.setSelection(PetalButtonIcon.selected(prefs));
        dogSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position != PetalButtonIcon.selected(prefs)) PetalButtonIcon.select(prefs, position); companion.setImageResource(PetalButtonIcon.drawable(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        layoutSelector = selector(box, "Layout", LAYOUT_SELECTOR, LAYOUTS, LAYOUT_DETAILS, false);
        layoutSelector.setSelection(prefs.getBoolean("petal_large_keys", false) ? 0 : 1);
        layoutSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                boolean large = position == 0;
                if (prefs.getBoolean("petal_large_keys", false) != large)
                    prefs.edit().putBoolean("petal_large_keys", large).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        themeSelector = selector(box, "Theme", THEME_SELECTOR, ICONS, null, true);
        themeSelector.setSelection(savedTheme());
        themeSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position != savedTheme()) selectIcon(position);
                if(themePreview!=null)themePreview.setSelection(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        themePreview=new PetalThemePreview(this);themePreview.setSelection(savedTheme());box.addView(themePreview,new LinearLayout.LayoutParams(-1,dp(94)));
        note(box,"Keyboard colors change with the selected theme. Choose a launcher icon separately.");
        button(box,"Launcher icon",this::showIconPicker);
        button(box,"Keyboard height", () -> startActivity(new Intent(this,PetalHeightActivity.class)));
        box=section(root,"Personal media","Import your own GIFs and image stickers. This collection stays on this phone and is excluded from backup.");
        button(box,"Import & manage personal media",()->startActivity(new Intent(this,PetalPersonalMediaActivity.class)));
        box=section(root,"Typing","Suggestions stay on your device. Backspace or Revert undoes a correction.");
        toggle(box,"Popup letters on keypress", dev.fourwags.sumi.inputmethod.latin.settings.Settings.PREF_POPUP_ON, true);
        toggle(box,"Number row", dev.fourwags.sumi.inputmethod.latin.settings.Settings.PREF_SHOW_NUMBER_ROW, false);
        option(box,"Suggestion bar size",PetalTypingOptions.SUGGESTION_SIZE,
                new String[]{"Regular","Larger (default)","Largest"},new int[]{100,125,150},125);
        note(box,"Larger words and taller tap areas in the word-selection bar. Separate from keyboard height; also follows your system font size.");
        option(box,"Popup letter size",PetalTypingOptions.PREVIEW_SIZE,new String[]{"Regular","Larger","Largest"},new int[]{100,125,150},100);
        option(box,"Popup linger after release",PetalTypingOptions.PREVIEW_DURATION,new String[]{"None","Brief (120 ms)","Longer (250 ms)"},new int[]{0,120,250},0);
        note(box,"Show a larger letter above each tapped key. Hidden in password and other protected fields. This is separate from long-press alternatives.");
        toggle(box,"Long-press alternatives", dev.fourwags.sumi.inputmethod.latin.settings.SettingsValues.PREF_LONG_PRESS_ALTERNATIVES, false);
        toggle(box,"Spelling suggestions", "show_suggestions", true);
        toggle(box,"Conservative autocorrect", "pref_key_auto_correction", true);
        toggle(box,"Next-word suggestions (bundled dictionary)", "next_word_prediction", true);
        toggle(box,"Literal typing (code and sensitive text)", "petal_literal", false);
        box=section(root,"Voice typing","Microphone access is optional. Granting permission does not start recording.");
        option(box,"Stop dictation after silence",PetalVoiceTiming.SILENCE,
                new String[]{"Short — 2 seconds (default)","Medium — 4 seconds","Long — 8 seconds"},new int[]{2000,4000,8000},2000);
        note(box,"Applies to your next dictation. Waits up to 10 seconds for your first words; each session lasts at most one minute. Stop finishes now; Cancel discards. Finishing the last phrase can take a few seconds.");
        TextView voiceInfo=note(box,getString(PetalVoiceInput.available(this)?R.string.petal_voice_settings:R.string.petal_voice_unavailable));
        microphonePermission=button(box,getString(R.string.petal_voice_permission_button),()->PetalVoiceInput.permission(this));
        box=section(root,"Privacy & dictionary","No network, automatic word learning, or saved clipboard history. Grammar rewriting is off.");
        toggle(box,"Use my personal dictionary", "petal_personal_dictionary", false);
        button(box,"Manage personal dictionary", () -> {
            try { startActivity(new Intent(Settings.ACTION_USER_DICTIONARY_SETTINGS)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this,"Personal dictionary settings unavailable on this device",Toast.LENGTH_LONG).show(); }
        });
        TextView explanation = new TextView(this); explanation.setTextColor(INK);explanation.setTextSize(15);explanation.setText("Personal dictionary is off by default. Only words you explicitly add through Android dictionary settings are used when enabled. Protected editors use literal typing. For editors that omit field metadata, turn on Literal typing. Backspace immediately undoes a correction; the Revert button does the same."); box.addView(explanation);
        box=section(root,"About Sumi","Sumi (墨) means ink in Japanese: a fitting name for a keyboard made for writing. Sumi is a Four Wags keyboard with local suggestions and optional voice typing.");
        note(box,"Made with care by Four Wags. Thank you to the open-source developers and everyone who helped test and improve Sumi.");
        box=section(root,"Open-source acknowledgments","Sumi is based on OpenBoard by the OpenBoard team. Copyright and license notices for OpenBoard, Android Open Source Project code, and bundled fonts remain available below.");
        note(box,"Original source: github.com/openboard-team/openboard\nSumi source (current repository address): github.com/FourWags/sumi");
        button(box,"Third-party notices",()->showLegalNotice("THIRD_PARTY_NOTICES.txt","Third-party notices"));
        button(box,"GNU GPL v3",()->showLegalNotice("LICENSE","GNU GPL v3"));
        button(box,"Apache License 2.0",()->showLegalNotice("LICENSE-Apache-2.0.txt","Apache License 2.0"));
        button(box,"Bundled font licenses",()->showLegalNotice("FONT_NOTICES.txt","Bundled font licenses"));
        String initial=state==null?null:state.getString(PAGE_STATE);
        if(initial==null){boolean enabled=false;for(android.view.inputmethod.InputMethodInfo info:((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).getEnabledInputMethodList())if(getPackageName().equals(info.getPackageName()))enabled=true;initial=enabled?"appearance":"setup";}
        showPage(initial,false);
        if(FourWagsKeyIcon.pending(this)) showIconPicker();
    }
    /** Navigation changes visibility only; it never writes keyboard preferences. */
    void showPage(String page,boolean announce) {
        if(!navigation.containsKey(page))page="appearance";
        activePage=page;
        for(java.util.Map.Entry<android.view.View,String> item:settingsSections.entrySet())item.getKey().setVisibility(page.equals(item.getValue())?android.view.View.VISIBLE:android.view.View.GONE);
        for(java.util.Map.Entry<String,ImageButton> item:navigation.entrySet()){
            boolean selected=page.equals(item.getKey());ImageButton button=item.getValue();button.setSelected(selected);
            android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(selected?0xffdca9b6:0x0029242a);background.setCornerRadius(dp(13));
            android.content.res.ColorStateList ripple=android.content.res.ColorStateList.valueOf(0x447b6572);button.setBackground(new android.graphics.drawable.RippleDrawable(ripple,background,null));
            button.setImageTintList(android.content.res.ColorStateList.valueOf(selected?0xff30212a:0xfff5e9e2));
        }
        String title="setup".equals(page)?"Get started":"appearance".equals(page)?"Appearance":"typing".equals(page)?"Typing":"voice".equals(page)?"Voice":"privacy".equals(page)?"Privacy":"About Sumi";
        pageHeading.setText(title);settingsScroll.scrollTo(0,0);
        if(announce)pageHeading.announceForAccessibility(title);
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString(PAGE_STATE,activePage);super.onSaveInstanceState(state);}
    private void showLegalNotice(String filename,String title) {
        try (java.io.InputStream in=getAssets().open("licenses/"+filename);
             java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int count;
            while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
            TextView text=new TextView(this);text.setText(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
            text.setTextColor(INK);text.setTextSize(14);text.setPadding(dp(20),dp(12),dp(20),dp(12));
            ScrollView scroll=new ScrollView(this);scroll.addView(text);
            new android.app.AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close",null).show();
        } catch (java.io.IOException e) {
            Toast.makeText(this,"Notice unavailable in this build",Toast.LENGTH_LONG).show();
        }
    }
    private LinearLayout section(LinearLayout root,String title,String description) {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(PAPER);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),INK==0xff30262c?0xffded2d3:0xff52444d);card.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);root.addView(card,lp);
        TextView heading=new TextView(this);heading.setText(title);heading.setTextSize(19);heading.setTextColor(INK);heading.setTypeface(PetalFonts.sans(this,500));heading.setAccessibilityHeading(true);card.addView(heading);
        String group=title.equals("Get started")?"setup":title.equals("Appearance & size")?"appearance":title.equals("Typing")||title.equals("Personal media")?"typing":title.equals("Voice typing")?"voice":title.equals("Privacy & dictionary")?"privacy":"about";
        settingsSections.put(card,group);
        note(card,description);return card;
    }
    private TextView note(LinearLayout parent,String text){TextView view=new TextView(this);view.setText(text);view.setTextColor(INK);view.setTextSize(14);view.setTypeface(PetalFonts.sans(this,400));view.setLineSpacing(dp(2),1);view.setPadding(0,dp(6),0,dp(10));parent.addView(view);return view;}
    private int savedTheme() { return PetalThemeCatalog.selected(prefs); }
    @Override protected void onResume() {
        super.onResume();
        refreshSetupState();
        layoutSelector.setSelection(prefs.getBoolean("petal_large_keys", false) ? 0 : 1);
        themeSelector.setSelection(savedTheme());
        if(themePreview!=null)themePreview.setSelection(savedTheme());
    }
    @Override protected void onStart() {
        super.onStart();observingSetup=true;
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),false,setupObserver);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),false,setupObserver);
    }
    @Override protected void onStop() {
        observingSetup=false;getContentResolver().unregisterContentObserver(setupObserver);
        setupHandler.removeCallbacksAndMessages(null);super.onStop();
    }
    private void refreshSetupState() {
        // Returning from Android settings or the IME picker must refresh selected vs enabled.
        ComponentName ime = new ComponentName(this, LatinIME.class);
        ComponentName selected = ComponentName.unflattenFromString(
                String.valueOf(Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD)));
        boolean active = ime.equals(selected);
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        boolean enabled = false;
        for (android.view.inputmethod.InputMethodInfo info : manager.getEnabledInputMethodList())
            if (ime.equals(new ComponentName(info.getPackageName(), info.getServiceName()))) enabled = true;
        updateSetupControls(active,enabled,checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED);
    }
    void updateSetupControls(boolean active,boolean enabled,boolean microphoneAllowed) {
        enableKeyboard.setText(active?R.string.sumi_keyboard_active:enabled?R.string.sumi_keyboard_enabled:R.string.sumi_keyboard_enable);
        PetalUi.setupControl(enableKeyboard,active||enabled);
        keyboardStatus.setText(active ? "Sumi is your active keyboard." : enabled
                ? "Sumi is enabled. Tap Choose keyboard to use it."
                : "Sumi is not enabled. Tap Enable keyboard to set it up.");
        microphonePermission.setText(microphoneAllowed?R.string.petal_microphone_allowed:R.string.petal_voice_permission_button);
        PetalUi.setupControl(microphonePermission,microphoneAllowed);
    }
    private Spinner selector(LinearLayout box, String label, String tag, String[] names,
            String[] descriptions, boolean icons) {
        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        spinner.setId(android.view.View.generateViewId());
        spinner.setTag(tag);
        spinner.setContentDescription(label);
        spinner.setMinimumHeight(dp(56));
        spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names) {
            private android.view.View row(int position, boolean expanded) {
                LinearLayout row = new LinearLayout(PetalSettingsActivity.this);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(56));
                row.setPadding(dp(8), dp(8), dp(8), dp(8));
                row.setBackgroundColor(PAPER);
                if (icons) {
                    ImageView icon = new ImageView(PetalSettingsActivity.this);
                    icon.setImageResource(ICON_DRAWABLES[position]);
                    icon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
                }
                TextView text = new TextView(PetalSettingsActivity.this);
                text.setTextColor(INK);
                text.setTextSize(16);
                text.setPadding(dp(8), 0, 0, 0);
                text.setText(expanded ? names[position] + (descriptions == null ? "" : "\n" + descriptions[position])
                        : label + ": " + names[position]);
                // One compact row normally, but wrapping remains available for large-font users.
                row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                return row;
            }
            @Override public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                return row(position, false);
            }
            @Override public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                return row(position, true);
            }
        });
        box.addView(spinner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return spinner;
    }
    private void option(LinearLayout box,String title,String key,String[] labels,int[] values,int fallback) {
        TextView label=note(box,title);Spinner spinner=new Spinner(this);spinner.setId(android.view.View.generateViewId());label.setLabelFor(spinner.getId());label.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);spinner.setTag(key);spinner.setContentDescription(title);spinner.setMinimumHeight(dp(48));
        spinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels) {
            private android.view.View choice(int index) {
                TextView text=new TextView(PetalSettingsActivity.this);text.setText(labels[index]);text.setTextSize(16);text.setTextColor(INK);text.setTypeface(PetalFonts.sans(PetalSettingsActivity.this,400));text.setBackgroundColor(PAPER);text.setSingleLine(false);text.setMinHeight(dp(48));text.setGravity(android.view.Gravity.CENTER_VERTICAL);text.setPadding(dp(8),dp(8),dp(8),dp(8));text.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1,-2));return text;
            }
            @Override public android.view.View getView(int index,android.view.View old,android.view.ViewGroup parent){return choice(index);}
            @Override public android.view.View getDropDownView(int index,android.view.View old,android.view.ViewGroup parent){return choice(index);}
        });
        int stored=prefs.getInt(key,fallback),position=0;for(int n=0;n<values.length;n++)if(stored==values[n])position=n;
        spinner.setSelection(position);spinner.setContentDescription(title+": "+labels[position]);box.addView(spinner,new LinearLayout.LayoutParams(-1,-2));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,android.view.View v,int index,long id){spinner.setContentDescription(title+": "+labels[index]);if(prefs.getInt(key,fallback)!=values[index])prefs.edit().putInt(key,values[index]).apply();}
            public void onNothingSelected(AdapterView<?> p){}
        });
    }
    private void toggle(LinearLayout box,String title,String key,boolean fallback) {
        Switch control=new Switch(this);control.setText(title);control.setTypeface(PetalFonts.sans(this,500));control.setMinHeight(dp(48));control.setPadding(0,dp(8),0,dp(8));control.setTextColor(INK);control.setTextSize(16);control.setSingleLine(false);control.setChecked(prefs.getBoolean(key,fallback));
        control.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean(key,checked).apply());box.addView(control);
    }
    private Button button(LinearLayout box,String title,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setTextSize(15);b.setTypeface(PetalFonts.sans(this,500));b.setAllCaps(false);b.setTextColor(INK);b.setMinHeight(dp(52));b.setBackground(PetalUi.control(this,INK==0xff30262c?0xfff1e5e8:0xff493b45,INK==0xff30262c?0xffd8c4ca:0xff77616f,ACCENT));b.setElevation(0);b.setStateListAnimator(null);b.setOnClickListener(v->action.run());box.addView(b);return b;
    }
    private void showIconPicker() {
        LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(dp(16),dp(8),dp(16),dp(8));
        String[] labels={"Layered keys","Inkstroke key","Shiba key"};
        int[] icons={R.drawable.fw_sumi_a_foreground,R.drawable.fw_sumi_b_foreground,R.drawable.fw_sumi_c_foreground};
        LinearLayout[] rows=new LinearLayout[3];
        for(int i=0;i<3;i++){
            LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(68));
            ImageView image=new ImageView(this);image.setImageResource(icons[i]);image.setBackgroundColor(0xff1c1b1a);
            image.setContentDescription(null);row.addView(image,new LinearLayout.LayoutParams(dp(56),dp(56)));
            TextView title=new TextView(this);title.setText(labels[i]);title.setTextColor(INK);title.setTextSize(16);
            title.setPadding(dp(16),0,0,0);row.addView(title);row.setContentDescription(labels[i]+" launcher icon");
            choices.addView(row);rows[i]=row;
        }
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle("Choose Sumi's icon")
            .setMessage("You can change this later in Settings.")
            .setView(choices)
            .setNegativeButton("Later",(d,which)->FourWagsKeyIcon.skip(this))
            .create();
        dialog.setOnCancelListener(d->FourWagsKeyIcon.skip(this));
        for(int i=0;i<3;i++){
            final String id=String.valueOf((char)('a'+i));
            rows[i].setOnClickListener(v->{
                try{FourWagsKeyIcon.choose(this,id);dialog.dismiss();}
                catch(RuntimeException e){Toast.makeText(this,"Could not save the icon. Please try again.",Toast.LENGTH_LONG).show();}
            });
        }
        dialog.show();
    }
    private void selectIcon(int selected) {
        selectTheme(this, selected);
        Toast.makeText(this,ICONS[selected]+" theme selected.",Toast.LENGTH_SHORT).show();
    }
    /** Shared selection path for Settings and the in-keyboard picker. */
    public static void selectTheme(Context context, int selected) {
        if (selected < 0 || selected >= ICONS.length) throw new IllegalArgumentException("Unknown theme");
        PackageManager pm=context.getPackageManager();
        int legacy=PetalThemeCatalog.LEGACY_INDICES[selected];
        if(legacy<PetalThemeCatalog.ALIAS_COUNT && !FourWagsKeyIcon.hasChoice(context)) {
            pm.setComponentEnabledSetting(new ComponentName(context,context.getPackageName()+".Icon"+legacy),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
            for(int i=0;i<PetalThemeCatalog.ALIAS_COUNT;i++) if(i!=legacy) pm.setComponentEnabledSetting(new ComponentName(context,context.getPackageName()+".Icon"+i),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
        }
        android.content.SharedPreferences.Editor editor=DeviceProtectedUtils.getSharedPreferences(context).edit().putInt("petal_theme",legacy);
        if(legacy<PetalThemeCatalog.ALIAS_COUNT) editor.putInt("petal_icon",legacy);
        editor.apply();
    }
}
