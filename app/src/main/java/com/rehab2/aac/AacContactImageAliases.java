package com.rehab2.aac;

import java.util.*;

/** Same named contact, same patient's private store. Never infer family relationships. */
public final class AacContactImageAliases {
    private static final List<List<String>> GROUPS = Arrays.asList(
        Arrays.asList("person_dusan", "dusan", "where_dusan"),
        Arrays.asList("person_zana", "sister_zana", "where_zana"),
        Arrays.asList("person_sergej", "grandfather_sergej"),
        Arrays.asList("person_julija", "julija"),
        Arrays.asList("person_oksana", "oksana"),
        Arrays.asList("person_inna", "inna"),
        Arrays.asList("person_franc", "franc")
    );
    private AacContactImageAliases() {}
    public static List<String> candidates(String itemId) {
        return candidates(itemId, true);
    }
    public static List<String> candidates(String itemId, boolean includeAliases) {
        List<String> result = new ArrayList<>();
        result.add(itemId);
        if (!includeAliases) return Collections.unmodifiableList(result);
        for (List<String> group : GROUPS) {
            if (!group.contains(itemId)) continue;
            for (String id : group) if (!id.equals(itemId)) result.add(id);
            break;
        }
        return Collections.unmodifiableList(result);
    }
}
