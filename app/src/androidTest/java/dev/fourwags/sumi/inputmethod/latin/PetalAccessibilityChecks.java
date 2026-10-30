package dev.fourwags.sumi.inputmethod.latin;
import android.view.View;
import com.google.android.apps.common.testing.accessibility.framework.*;
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid;
import java.util.*;
import java.io.*;
import static org.junit.Assert.*;

final class PetalAccessibilityChecks {
    static void check(View view, String label) { check(view,label,false); }
    static void checkQwerty(View view, String label) { check(view,label,true); }
    private static boolean knownQwertyTarget(AccessibilityHierarchyCheck check, AccessibilityHierarchyCheckResult result) {
        if(!check.getClass().getSimpleName().equals("TouchTargetSizeCheck")) return false;
        com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement element=result.getElement();
        if(element==null||!element.isClickable()||!"android.widget.Button".contentEquals(element.getClassName())) return false;
        com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement parent=element.getParentView();
        // Only real virtual keys belonging to MainKeyboardView. Settings, tools,
        // Revert, emoji tabs/actions and other touch targets retain strict gating.
        return parent!=null&&"dev.fourwags.sumi.inputmethod.latin:id/keyboard_view".equals(parent.getResourceName());
    }
    private static void check(View view, String label, boolean qwerty) {
        AccessibilityHierarchyAndroid hierarchy=AccessibilityHierarchyAndroid.newBuilder(view).setObtainRenderingInfo(true).build();
        StringBuilder report=new StringBuilder("ATF 4.1.1 LATEST: "+label+"\n");
        List<String> errors=new ArrayList<>();
        for(AccessibilityHierarchyCheck check:AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(AccessibilityCheckPreset.LATEST)) {
            for(AccessibilityHierarchyCheckResult result:check.runCheckOnHierarchy(hierarchy)) {
                if(result.getType()!=AccessibilityCheckResult.AccessibilityCheckResultType.NOT_RUN) {
                    String line=result.getType()+" "+check.getClass().getSimpleName()+": "+result.getMessage(Locale.US);
                    if(label.startsWith("rail-") && result.getElement()!=null) {
                        com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement element=result.getElement();
                        line+=" [class="+element.getClassName()+", text="+element.getText()+", bounds="+element.getBoundsInScreen()+", layout="+element.getLayoutParams()+"]";
                    }
                    report.append(line).append("\n");
                    boolean known=qwerty&&knownQwertyTarget(check,result);
                    if(known) report.append("KNOWN_QWERTY_TOUCH_TARGET_LIMIT: virtual key ").append(result.getElement().getId()).append("; raw finding retained above.\n");
                    if(!known&&(result.getType()==AccessibilityCheckResult.AccessibilityCheckResultType.ERROR
                        || result.getType()==AccessibilityCheckResult.AccessibilityCheckResultType.WARNING
                        || (result.getType()==AccessibilityCheckResult.AccessibilityCheckResultType.INFO
                            && check.getClass().getSimpleName().equals("TextSizeCheck"))))errors.add(line);
                }
            }
        }
        try(FileWriter out=new FileWriter(new File(view.getContext().getExternalFilesDir(null),"accessibility-"+label+".txt"))){out.write(report.toString());}
        catch(IOException e){throw new RuntimeException(e);}
        assertTrue(report.toString(),errors.isEmpty());
    }
}
