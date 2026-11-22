package com.rehab2.aac;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Set;

/** Updates only byte-identifiable bundled copies. Unknown replacements stay intact. */
public final class SystemIconUpgrade {
    private SystemIconUpgrade() {}
    public interface AtomicReplace { void replace(File source, File target) throws Exception; }

    public static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static synchronized String install(File target, byte[] bundled, Set<String> previous, AtomicReplace replace) throws Exception {
        if (target.exists()) {
            byte[] existing;
            try (FileInputStream input = new FileInputStream(target);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                existing = output.toByteArray();
            }
            if (Arrays.equals(existing, bundled)) return "current";
            if (!previous.contains(sha256(existing))) return "preserved_unknown";
        }
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) throw new IOException("Missing icon directory");
        File pending = File.createTempFile("icon-upgrade-", ".tmp", parent);
        try {
            try (FileOutputStream output = new FileOutputStream(pending)) {
                output.write(bundled);
                output.getFD().sync();
            }
            // Atomic replacement on the same Android filesystem, never delete first.
            replace.replace(pending, target);
            return "updated";
        } finally { if (pending.exists()) pending.delete(); }
    }
}
