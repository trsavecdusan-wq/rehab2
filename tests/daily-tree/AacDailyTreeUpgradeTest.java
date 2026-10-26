import com.rehab2.aac.AacDailyTreeUpgrade;
import java.util.*;

public class AacDailyTreeUpgradeTest {
    static int checks;
    static void equal(Object a, Object b) {
        checks++;
        if (!Objects.equals(a, b)) throw new AssertionError(a + " != " + b);
    }
    public static void main(String[] args) {
        List<String> old = Arrays.asList("choose_myself", "cigarette", "smoking", "lighter", "ashtray");
        List<String> next = Arrays.asList("thirsty", "hungry", "rest", "walk", "shop", "choose_myself", "smoking", "cigarette", "lighter", "ashtray");
        equal(AacDailyTreeUpgrade.children("i_want", old, next, false), next);
        equal(AacDailyTreeUpgrade.children("i_want", next, next, false), next);
        equal(AacDailyTreeUpgrade.children("i_want", Collections.emptyList(), next, false), next);
        equal(AacDailyTreeUpgrade.children("i_want", Arrays.asList("cigarette"), next, false), next);
        equal(AacDailyTreeUpgrade.children("i_want", old, next, true), old);
        List<String> custom = Arrays.asList("my_private_place", "cigarette");
        List<String> merged = AacDailyTreeUpgrade.children("i_want", custom, next, false);
        equal(merged.get(0), "my_private_place");
        equal(merged.get(1), "cigarette");
        equal(new HashSet<>(merged).size(), merged.size());
        equal(AacDailyTreeUpgrade.children("i_want", merged, next, false), merged);
        equal(AacDailyTreeUpgrade.children("patient_category", custom, next, false), custom);
        equal(AacDailyTreeUpgrade.children("people", Arrays.asList("person_zana", "person_dusan"), Arrays.asList("family_group", "friends_group"), false), Arrays.asList("family_group", "friends_group"));
        equal(AacDailyTreeUpgrade.children("people", custom, Arrays.asList("family_group"), true), custom);
        equal(AacDailyTreeUpgrade.children("place_group", Arrays.asList("room", "home"), Arrays.asList("shop", "room", "home"), false), Arrays.asList("shop", "room", "home"));
        equal(old.size(), 5);
        equal(custom.size(), 2);
        System.out.println(checks + " migration checks passed");
    }
}
