package com.rehab2.aac;

import java.util.Collection;

/** Runtime image choice only; never rewrites an item or a personal image binding. */
public final class AacSystemImagePolicy {
    private AacSystemImagePolicy() {}
    public static String select(String source, String currentPath, boolean edited, boolean locked,
                                Collection<String> knownSystemPaths, String femalePath,
                                String malePath, boolean male) {
        if (edited || locked || !"SYSTEM".equals(source) || currentPath == null ||
            knownSystemPaths == null || !knownSystemPaths.contains(currentPath)) return null;
        String chosen = male ? malePath : femalePath;
        return chosen == null || chosen.isEmpty() ? null : chosen;
    }
}
