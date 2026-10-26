import com.rehab2.aac.AacUpgradeFieldPolicy;
import org.json.JSONObject;

/** Runs against compiled application classes with a real JVM org.json implementation. */
public final class UpgradeFieldPolicyTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Check " + checks);
    }
    public static void main(String[] args) {
        String[] flags = {"userEdited", "locked", "lockedByUser", "modifiedByTherapist",
            "therapistEdited", "manualEdit", "manualOverride", "customized", "translationManualOverride"};
        for (String flag : flags) {
            JSONObject before = new JSONObject().put(flag, true).put("imagePath", "system/chosen.png")
                .put("iconSource", "SYSTEM").put("labelSl", "Moj napis")
                .put("speechTextByLanguage", new JSONObject().put("uk", "Моє речення"));
            JSONObject proposed = new JSONObject().put("imagePath", "system/review.png")
                .put("iconSource", "SYSTEM").put("labelSl", "Sistemski napis");
            AacUpgradeFieldPolicy.INSTANCE.mergeUserFields(before, proposed);
            check(proposed.getString("imagePath").equals("system/chosen.png"));
            check(proposed.getString("labelSl").equals("Moj napis"));
            check(proposed.getJSONObject("speechTextByLanguage").getString("uk").equals("Моє речення"));
            check(proposed.getBoolean(flag));
            AacUpgradeFieldPolicy.INSTANCE.mergeUserFields(before, proposed);
            check(proposed.getString("imagePath").equals("system/chosen.png"));
        }
        JSONObject personal = new JSONObject().put("imagePath", "custom/original.png").put("iconSource", "PATIENT");
        JSONObject changed = new JSONObject().put("imagePath", "system/review.png");
        AacUpgradeFieldPolicy.INSTANCE.mergeUserFields(personal, changed);
        check(changed.getString("imagePath").equals("custom/original.png"));
        JSONObject shipped = new JSONObject().put("imagePath", "system/old.png").put("iconSource", "SYSTEM");
        JSONObject repair = new JSONObject().put("imagePath", "system/review.png").put("labelSl", "PO MOŽGANSKI KAPI");
        AacUpgradeFieldPolicy.INSTANCE.mergeUserFields(shipped, repair);
        check(repair.getString("imagePath").equals("system/review.png"));
        check(repair.getString("labelSl").equals("PO MOŽGANSKI KAPI"));
        check(shipped.getString("imagePath").equals("system/old.png"));
        System.out.println("PASS: " + checks + " upgrade field preservation checks");
    }
}
