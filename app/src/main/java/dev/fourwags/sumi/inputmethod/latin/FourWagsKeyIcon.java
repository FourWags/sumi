package dev.fourwags.sumi.inputmethod.latin;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.SharedPreferences;
import java.util.ArrayList;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;

/** Independent launcher choice; never changes the IME service or the color theme. */
// Commit icon choice before switching persistent PackageManager component state.
@SuppressLint("ApplySharedPref")
public final class FourWagsKeyIcon {
    private static final String CHOICE="fourwags_icon_choice",PENDING="fourwags_icon_pending",INITIALIZED="fourwags_icon_initialized";
    private FourWagsKeyIcon() {}
    private static SharedPreferences prefs(Context c){return DeviceProtectedUtils.getSharedPreferences(c);}
    public static boolean hasChoice(Context c){return prefs(c).contains(CHOICE);}
    public static boolean pending(Context c){return prefs(c).getBoolean(PENDING,false);}
    public static void skip(Context c){boolean first=pending(c);if(prefs(c).edit().putBoolean(PENDING,false).commit()&&first)try{sync(c,"c");}catch(RuntimeException ignored){} }
    public static void initialize(Context c){
        SharedPreferences p=prefs(c);
        if(p.contains(INITIALIZED)){
            String selected=p.getString(CHOICE,null);
            if(selected!=null&&!p.getBoolean(PENDING,false))try{sync(c,selected);}catch(RuntimeException ignored){}
            return;
        }
        boolean fresh=false;
        try {var info=c.getPackageManager().getPackageInfo(c.getPackageName(),0);fresh=info.firstInstallTime==info.lastUpdateTime;}
        catch(PackageManager.NameNotFoundException ignored) {}
        SharedPreferences.Editor edit=p.edit().putBoolean(INITIALIZED,true).putBoolean(PENDING,fresh);
        if(fresh)edit.putString(CHOICE,"c");
        if(!edit.commit())return;
        // Keep the old launch entry active until the first-use picker is handled.
    }
    public static void choose(Context c,String id){
        if(!"a".equals(id)&&!"b".equals(id)&&!"c".equals(id))throw new IllegalArgumentException("Unknown icon");
        SharedPreferences p=prefs(c);String old=p.getString(CHOICE,null);
        if(!p.edit().putString(CHOICE,id).putBoolean(PENDING,false).commit())throw new IllegalStateException("Could not save icon");
        try{sync(c,id);}catch(RuntimeException failure){SharedPreferences.Editor revert=p.edit();if(old==null)revert.remove(CHOICE);else revert.putString(CHOICE,old);revert.commit();throw failure;}
    }
    private static ComponentName alias(Context c,String id){return new ComponentName(c,c.getPackageName()+".FourWagsIcon_"+id);}
    private static void sync(Context c,String id){
        PackageManager pm=c.getPackageManager();ComponentName chosen=alias(c,id);
        if(Build.VERSION.SDK_INT>=33){
            ArrayList<PackageManager.ComponentEnabledSetting> batch=new ArrayList<>();
            for(int n=0;n<PetalThemeCatalog.ALIAS_COUNT;n++){
                ComponentName name=new ComponentName(c,c.getPackageName()+".Icon"+n);
                if(pm.getComponentEnabledSetting(name)!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                    batch.add(new PackageManager.ComponentEnabledSetting(name,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP));
            }
            for(String candidate:new String[]{"a","b","c"}){
                ComponentName name=alias(c,candidate);int state=candidate.equals(id)?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                if(pm.getComponentEnabledSetting(name)!=state)batch.add(new PackageManager.ComponentEnabledSetting(name,state,PackageManager.DONT_KILL_APP));
            }
            if(!batch.isEmpty())pm.setComponentEnabledSettings(batch);
        }else{
            pm.setComponentEnabledSetting(chosen,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
            for(int n=0;n<PetalThemeCatalog.ALIAS_COUNT;n++)pm.setComponentEnabledSetting(new ComponentName(c,c.getPackageName()+".Icon"+n),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
            for(String other:new String[]{"a","b","c"})if(!other.equals(id))pm.setComponentEnabledSetting(alias(c,other),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
        }
    }
}
