package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;

import android.content.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;

/** Explicit editing tools. No clipboard listener, history, media menu or persistent text. */
public final class PetalToolsPopup {
    private final LatinIME ime;
    private PopupWindow active;
    private boolean singleColumn;
    public PetalToolsPopup(LatinIME ime) { this.ime=ime; }
    private int dp(int value) { return Math.round(value*ime.getResources().getDisplayMetrics().density); }
    public boolean isShowing(){return active!=null&&active.isShowing();}
    public void dismiss() { if(active!=null){active.dismiss();active=null;} }
    private int color(int attribute) {
        android.util.TypedValue value=new android.util.TypedValue();
        View keyboard=ime.mKeyboardSwitcher.getMainKeyboardView();
        keyboard.getContext().getTheme().resolveAttribute(attribute,value,true);return value.data;
    }
    private Button button(LinearLayout row,int label,Runnable action,boolean enabled) {
        Button button=new Button(ime);button.setText(label);button.setTypeface(PetalFonts.sans(ime,500));button.setAllCaps(false);button.setMinHeight(dp(48));button.setMinWidth(dp(48));button.setPadding(dp(6),0,dp(6),0);
        button.setTextColor(color(R.attr.petalInk));button.setBackground(PetalUi.control(ime,color(R.attr.petalFunction),color(R.attr.petalFunctionOutline),color(R.attr.petalInk)));button.setElevation(0);button.setStateListAnimator(null);
        button.setEnabled(enabled);LinearLayout.LayoutParams spacing=row.getOrientation()==LinearLayout.VERTICAL
            ?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1);spacing.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(button,spacing);
        button.setOnClickListener(v->{action.run();});return button;
    }
    private LinearLayout row(LinearLayout parent) {LinearLayout row=new LinearLayout(ime);row.setOrientation(singleColumn?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);parent.addView(row);return row;}
    public void show(View anchor) {
        dismiss();
        LinearLayout box=new LinearLayout(ime);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),dp(8));
        android.graphics.drawable.GradientDrawable panel=new android.graphics.drawable.GradientDrawable();panel.setColor(color(R.attr.sumiKey));panel.setCornerRadius(dp(16));panel.setStroke(dp(1),color(R.attr.sumiKeyOutline));box.setBackground(panel);box.setAccessibilityPaneTitle(ime.getString(R.string.petal_tools_description));
        TextView title=new TextView(ime);title.setText(R.string.petal_tools_description);title.setTextColor(color(R.attr.petalInk));title.setTextSize(17);title.setPadding(dp(4),dp(4),dp(4),dp(8));title.setAccessibilityHeading(true);box.addView(title);
        ScrollView scroll=new ScrollView(ime);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(true);scroll.setPadding(0,0,0,dp(8));LinearLayout content=new LinearLayout(ime);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        View decor=ime.getWindow().getWindow().getDecorView();View frame=decor.findViewById(R.id.main_keyboard_frame);
        int availableWidth=Math.min(decor.getWidth(),frame.getWidth()>0?frame.getWidth():decor.getWidth());
        // At large system text sizes or narrow windows, keep each label on its own row.
        // The scrolling body takes the extra height; the close control stays fixed.
        singleColumn=availableWidth<dp(Math.round(300*ime.getResources().getConfiguration().fontScale));
        showHome(content);
        Button close=new Button(ime);close.setText(R.string.petal_tools_close);close.setAllCaps(false);close.setTextColor(color(R.attr.petalInk));close.setBackground(PetalUi.control(ime,color(R.attr.petalFunction),color(R.attr.petalFunctionOutline),color(R.attr.petalInk)));close.setElevation(0);close.setStateListAnimator(null);close.setMinHeight(dp(48));close.setOnClickListener(v->dismiss());box.addView(close);
        int height=Math.min(dp(214),frame.getHeight());
        // Use the IME width. A fixed 380 dp cap left room unused on wider displays,
        // wrapping Clipboard and Editing panel and hiding the final status row.
        int width=Math.max(1,availableWidth-dp(16));
        active=new PopupWindow(box,width,height,false);
        active.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));active.setOutsideTouchable(true);
        active.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        active.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
        final PopupWindow shown=active;
        active.setOnDismissListener(()->{content.removeAllViews();if(active==shown)active=null;shown.setOnDismissListener(null);});
        // Fit inside the existing IME area, leaving the receiving editor above it unobscured.
        int[] xy=new int[2];frame.getLocationInWindow(xy);
        active.showAtLocation(decor,Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,xy[1]+Math.max(0,frame.getHeight()-height));
    }
    private void showHome(LinearLayout content) {
        content.removeAllViews();content.setAccessibilityPaneTitle(ime.getString(R.string.petal_tools_description));
        final boolean editing=ime.petalMediaAllowed();
        LinearLayout row=row(content);
        button(row,R.string.petal_tools_settings,()->{dismiss();ime.requestHideSelf(0);ime.startActivity(new Intent(ime,PetalSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));},true);
        button(row,R.string.petal_tools_clipboard,()->showClipboard(content),editing);
        row=row(content);
        button(row,R.string.petal_tools_editing,()->showEditing(content),editing);
        button(row,R.string.petal_tools_select_all,()->{ime.petalEdit(android.R.id.selectAll);dismiss();},editing);
        row=row(content);
        button(row,R.string.petal_tools_copy,()->{ime.petalEdit(android.R.id.copy);dismiss();},editing);
        button(row,R.string.petal_tools_themes,()->showThemes(content),true);
        row=row(content);
        button(row,R.string.petal_tools_paste,()->{paste();dismiss();},editing);
        Button large=button(row,R.string.petal_tools_large_keys,()->{toggle("petal_large_keys");dismiss();ime.loadKeyboard();},true);
        row=row(content);
        Button literal=button(row,R.string.petal_tools_literal,()->{toggle("petal_literal");dismiss();ime.loadKeyboard();},true);
        button(row,R.string.petal_tools_personal_media,()->{dismiss();ime.petalOpenPersonalMediaFromTools();},editing);
        for(Button control:new Button[]{large,literal}){boolean enabled=DeviceProtectedUtils.getSharedPreferences(ime).getBoolean(control==large?"petal_large_keys":"petal_literal",false);control.setCompoundDrawablePadding(dp(8)); if(android.os.Build.VERSION.SDK_INT>=30)control.setStateDescription(ime.getString(enabled?R.string.petal_tools_on:R.string.petal_tools_off));else control.setContentDescription(control.getText()+", "+ime.getString(enabled?R.string.petal_tools_on:R.string.petal_tools_off));
            control.setTypeface(null,enabled?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);}
        TextView modes=new TextView(ime);modes.setTextColor(color(R.attr.petalInk));modes.setTextSize(14);android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(ime);modes.setText(ime.getString(R.string.petal_tools_modes,ime.getString(prefs.getBoolean("petal_large_keys",false)?R.string.petal_tools_on:R.string.petal_tools_off),ime.getString(prefs.getBoolean("petal_literal",false)?R.string.petal_tools_on:R.string.petal_tools_off)));content.addView(modes);
    }
    private void back(LinearLayout content){button(row(content),R.string.petal_tools_back,()->showHome(content),true);}
    private void showEditing(LinearLayout content){
        content.removeAllViews();back(content);content.setAccessibilityPaneTitle(ime.getString(R.string.petal_tools_editing));
        final int[][] controls={{R.string.petal_cursor_left,KeyEvent.KEYCODE_DPAD_LEFT,0},{R.string.petal_cursor_right,KeyEvent.KEYCODE_DPAD_RIGHT,0},
            {R.string.petal_select_left,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.META_SHIFT_ON},{R.string.petal_select_right,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.META_SHIFT_ON},
            {R.string.petal_word_left,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.META_CTRL_ON},{R.string.petal_word_right,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.META_CTRL_ON},
            {R.string.petal_cursor_up,KeyEvent.KEYCODE_DPAD_UP,0},{R.string.petal_cursor_down,KeyEvent.KEYCODE_DPAD_DOWN,0},
            {R.string.petal_line_start,KeyEvent.KEYCODE_MOVE_HOME,0},{R.string.petal_line_end,KeyEvent.KEYCODE_MOVE_END,0}};
        for(int n=0;n<controls.length;n+=2){LinearLayout r=row(content);for(int j=n;j<n+2;j++){int[] item=controls[j];button(r,item[0],()->ime.petalNavigate(item[1],item[2]),ime.petalMediaAllowed());}}
        LinearLayout actions=row(content);button(actions,R.string.petal_tools_select_all,()->ime.petalEdit(android.R.id.selectAll),ime.petalMediaAllowed());button(actions,R.string.petal_cut,()->ime.petalEdit(android.R.id.cut),ime.petalMediaAllowed());
        actions=row(content);button(actions,R.string.petal_tools_copy,()->ime.petalEdit(android.R.id.copy),ime.petalMediaAllowed());button(actions,R.string.petal_tools_paste,()->{paste();dismiss();},ime.petalMediaAllowed());
    }
    private void showThemes(LinearLayout content) {
        content.removeAllViews();back(content);
        content.setAccessibilityPaneTitle(ime.getString(R.string.petal_tools_themes));
        TextView heading=new TextView(ime);heading.setText(R.string.petal_themes_notice);heading.setTypeface(PetalFonts.sans(ime,500));
        heading.setTextColor(color(R.attr.petalInk));heading.setAccessibilityHeading(true);content.addView(heading);
        int selected=PetalThemeCatalog.selected(DeviceProtectedUtils.getSharedPreferences(ime));
        for(int i=0;i<PetalSettingsActivity.ICONS.length;i++) {
            final int index=i;
            Button choice=new Button(ime);choice.setText(PetalSettingsActivity.ICONS[i]);choice.setTypeface(PetalFonts.sans(ime,500));
            choice.setMinHeight(dp(56));choice.setMinWidth(dp(48));choice.setSelected(i==selected);
            choice.setTextColor(color(R.attr.petalInk));
            choice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color(R.attr.petalFunction)));
            android.graphics.drawable.Drawable icon=ime.getDrawable(PetalSettingsActivity.ICON_DRAWABLES[i]);
            icon.setBounds(0,0,dp(32),dp(32));choice.setCompoundDrawablesRelative(icon,null,null,null);
            choice.setCompoundDrawablePadding(dp(8));
            content.addView(choice,new LinearLayout.LayoutParams(-1,-2));
            choice.setOnClickListener(v->{
                dismiss();
                PetalSettingsActivity.selectTheme(ime,index);
                // The existing theme listener reloads the visible IME; no activity switch,
                // editor operation or clipboard access is needed to return to typing.
            });
        }
    }
    private void toggle(String key) {
        SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(ime);
        prefs.edit().putBoolean(key,!prefs.getBoolean(key,false)).apply();
    }
    private CharSequence clipboardText() {
        if(!ime.petalMediaAllowed())return null;
        ClipboardManager clipboard=(ClipboardManager)ime.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData data=clipboard.getPrimaryClip();
        return data!=null&&data.getItemCount()>0 ? data.getItemAt(0).getText() : null;
    }
    private void paste() {
        CharSequence text=clipboardText();
        if(text!=null)ime.petalPaste(text);else Toast.makeText(ime,R.string.petal_clipboard_empty,Toast.LENGTH_SHORT).show();
    }
    private void showClipboard(LinearLayout content) {
        ClipboardManager manager=(ClipboardManager)ime.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip=ime.petalMediaAllowed()?manager.getPrimaryClip():null;
        ClipDescription description=clip==null?null:clip.getDescription();
        boolean sensitive=description!=null && description.getExtras()!=null && description.getExtras().getBoolean("android.content.extra.IS_SENSITIVE",false);
        CharSequence text=!sensitive && clip!=null && clip.getItemCount()>0?clip.getItemAt(0).getText():null;content.removeAllViews();back(content);
        TextView label=new TextView(ime);label.setText(R.string.petal_clipboard_notice);label.setTextColor(color(R.attr.petalInk));content.addView(label);
        TextView preview=new TextView(ime);preview.setText(sensitive?ime.getString(R.string.petal_clipboard_sensitive):text==null?ime.getString(R.string.petal_clipboard_empty):PetalClipboardPreview.visible(text));preview.setEllipsize(android.text.TextUtils.TruncateAt.END);preview.setMaxLines(4);preview.setTextColor(color(R.attr.petalInk));content.addView(preview);
        LinearLayout row=row(content);button(row,R.string.petal_tools_paste,()->{paste();dismiss();},text!=null||sensitive);
    }
}
