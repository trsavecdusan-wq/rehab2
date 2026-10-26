package com.rehab2.aac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Recognises shipped 794 branches; never removes unknown patient-created links. */
public final class AacDailyTreeUpgrade {
    private AacDailyTreeUpgrade() {}

    private static final Map<String, List<String>> LEGACY = new HashMap<>();
    static {
        LEGACY.put("people", Arrays.asList("about_me", "socialno", "miss_someone",
                "person_dusan", "person_zana", "person_sergej", "person_julija",
                "person_oksana", "person_inna", "person_franc", "person_other"));
        LEGACY.put("i_want", Arrays.asList("choose_myself", "cigarette", "smoking", "lighter", "ashtray"));
        LEGACY.put("place_group", Arrays.asList("room", "terrace", "balcony", "courtyard",
                "go_outside_with_wheelchair", "go_inside", "bathroom", "dining_room",
                "home", "outside", "inside", "therapy"));
    }

    public static boolean handles(String id) { return LEGACY.containsKey(id); }

    public static List<String> children(String id, List<String> current,
                                        List<String> canonical, boolean edited) {
        if (edited || !handles(id)) return new ArrayList<>(current);
        // Only a recognised old system list (or its incomplete subset) is replaced.
        // Unknown additions identify a custom branch: keep them, append missing links.
        HashSet<String> known = new HashSet<>(LEGACY.get(id));
        known.addAll(canonical);
        if (known.containsAll(current)) return new ArrayList<>(canonical);
        List<String> result = new ArrayList<>(current);
        for (String child : canonical) if (!result.contains(child)) result.add(child);
        return result;
    }
}
