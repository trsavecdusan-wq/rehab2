package com.rehab2.patient;

import java.io.*;
import java.util.Properties;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Private image overlay. Never reads or rewrites AAC/profile JSON or communication profiles.
 * All access in this single-process app is serialized, including separate store instances.
 */
public final class PatientImageStore {
    private static final Object LOCK = new Object();
    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private final File root;
    public interface Validator { boolean valid(File file); }
    public interface Source { InputStream open() throws IOException; }
    public enum SeedResult { IMPORTED, RECOVERED, SKIPPED_EXISTING }

    public PatientImageStore(File root) { this.root = root; }

    public String currentPatientId() throws IOException {
        synchronized (LOCK) {
            Properties state = read(new File(root, "active.properties"));
            String id = state.getProperty("patientId");
            if (id != null) { patientDir(id); return id; }
            if (new File(root, "active.properties").exists()) throw new IOException("Missing patient identity");
            id = createPatient();
            selectPatient(id);
            return id;
        }
    }

    public String createPatient() throws IOException {
        synchronized (LOCK) {
            ensureDir(root);
            String id = UUID.randomUUID().toString();
            File dir = new File(root, id);
            if (!dir.mkdir()) throw new IOException("Cannot create patient directory");
            return id;
        }
    }

    public void selectPatient(String id) throws IOException {
        synchronized (LOCK) {
            patientDir(id);
            Properties state = read(new File(root, "active.properties"));
            if (state.getProperty("legacyPatientId") == null) state.setProperty("legacyPatientId", id);
            state.setProperty("patientId", id);
            write(new File(root, "active.properties"), state);
        }
    }

    /** Old shared image references belong only to the original single-patient installation. */
    public boolean ownsLegacyImages(String patientId) throws IOException {
        synchronized (LOCK) {
            patientDir(patientId);
            return patientId.equals(read(new File(root, "active.properties")).getProperty("legacyPatientId"));
        }
    }

    /** Copies original bytes to a new filename; replacing a binding never deletes an original. */
    public File importImage(String patientId, String itemId, String extension,
                            InputStream input, Validator validator) throws IOException {
        synchronized (LOCK) {
            checkItem(itemId);
            if (!extension.matches("jpg|jpeg|png|webp")) throw new IOException("Unsupported image format");
            File dir = patientDir(patientId);
            Properties bindings = read(new File(dir, "images.properties"));
            File originals = new File(dir, "originals");
            ensureDir(originals);
            File target = new File(originals, UUID.randomUUID() + "." + extension);
            if (!target.createNewFile()) throw new IOException("Original already exists");
            try {
                copy(input, target);
                if (!validator.valid(target)) throw new IOException("Invalid image");
            } catch (IOException | RuntimeException error) {
                target.delete(); // Only the incomplete/unaccepted new import, never an older original.
                throw error;
            }
            bindings.setProperty(itemId, target.getName());
            write(new File(dir, "images.properties"), bindings);
            return target;
        }
    }

    public boolean hasBinding(String patientId, String itemId) throws IOException {
        synchronized (LOCK) {
            checkItem(itemId);
            return read(new File(patientDir(patientId), "images.properties")).containsKey(itemId);
        }
    }

    public String confirmedSeedPatient(String packId) throws IOException {
        synchronized (LOCK) {
            checkPack(packId);
            return read(new File(root, "seed-consent.properties")).getProperty(packId);
        }
    }

    /** Only call after an explicit local caregiver confirmation, never infer from a profile name. */
    public void confirmSeedPatient(String packId, String patientId) throws IOException {
        synchronized (LOCK) {
            checkPack(packId);
            if (!currentPatientId().equals(patientId)) throw new IOException("Patient changed");
            Properties consent = read(new File(root, "seed-consent.properties"));
            String old = consent.getProperty(packId);
            if (old != null && !old.equals(patientId)) throw new IOException("Package belongs to another patient");
            consent.setProperty(packId, patientId);
            write(new File(root, "seed-consent.properties"), consent);
        }
    }

    public boolean seedComplete(String packId, String patientId) throws IOException {
        synchronized (LOCK) {
            requireSeedIdentity(packId, patientId);
            return read(new File(patientDir(patientId), "seed-results.properties")).containsKey(packId);
        }
    }

    /** Caller invokes this only after every package entry has imported or preserved a valid image. */
    public void completeSeed(String packId, String patientId, String result) throws IOException {
        synchronized (LOCK) {
            requireSeedIdentity(packId, patientId);
            File file = new File(patientDir(patientId), "seed-results.properties");
            Properties results = read(file);
            results.setProperty(packId, result);
            write(file, results);
        }
    }

    /** Resumable seed import: immutable deterministic original, staged copy, then atomic binding.
     * A crash after rename but before index commit reuses the same verified original on retry.
     */
    public SeedResult importSeed(String packId, String patientId, String itemId, String sha256,
                                  Source source, Validator validator, File... legacyFiles) throws IOException {
        synchronized (LOCK) {
            requireSeedIdentity(packId, patientId);
            checkItem(itemId);
            if (!sha256.matches("[0-9a-f]{64}")) throw new IOException("Invalid checksum");
            File existing = resolve(patientId, itemId);
            if (existing != null && validator.valid(existing)) return SeedResult.SKIPPED_EXISTING;
            File dir = patientDir(patientId);
            File originals = new File(dir, "originals");
            ensureDir(originals);
            String name = UUID.nameUUIDFromBytes((packId + ":" + itemId + ":" + sha256).getBytes("UTF-8")) + ".png";
            File original = new File(originals, name);
            File pending = new File(originals, name + ".pending");
            boolean recovered = original.isFile();
            if (recovered) {
                if (!digest(original).equals(sha256) || !validator.valid(original))
                    throw new IOException("Existing seed original is invalid; preserved for inspection");
            } else {
                try {
                    try (InputStream input = source.open()) { copy(input, pending); }
                    if (!digest(pending).equals(sha256) || !validator.valid(pending))
                        throw new IOException("Package image validation failed");
                    if (original.exists() || !pending.renameTo(original)) throw new IOException("Cannot publish original");
                } finally { pending.delete(); }
            }
            File[] backups = java.util.Arrays.copyOf(legacyFiles, legacyFiles.length + 1);
            backups[legacyFiles.length] = new File(dir, "images.properties");
            backupLegacy(patientId, backups); // No binding changes unless the backup succeeded.
            Properties bindings = read(new File(dir, "images.properties"));
            bindings.setProperty(itemId, name);
            write(new File(dir, "images.properties"), bindings);
            return recovered ? SeedResult.RECOVERED : SeedResult.IMPORTED;
        }
    }

    private void requireSeedIdentity(String packId, String patientId) throws IOException {
        checkPack(packId);
        if (!patientId.equals(currentPatientId()) || !patientId.equals(confirmedSeedPatient(packId)))
            throw new IOException("Patient identity not confirmed for this package");
    }

    private static void checkPack(String packId) throws IOException {
        if (packId == null || !packId.matches("[0-9a-f]{64}")) throw new IOException("Invalid package ID");
    }

    private static String digest(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }

    public File resolve(String patientId, String itemId) throws IOException {
        synchronized (LOCK) {
            checkItem(itemId);
            File dir = patientDir(patientId);
            String name = read(new File(dir, "images.properties")).getProperty(itemId);
            if (name == null) return null;
            if (!name.matches("[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)")) throw new IOException("Invalid image reference");
            File originals = new File(dir, "originals");
            File file = new File(originals, name);
            if (!file.getCanonicalFile().getParentFile().equals(originals.getCanonicalFile()))
                throw new IOException("Image outside patient directory");
            return file.isFile() && file.length() > 0 ? file : null;
        }
    }

    public void clearBinding(String patientId, String itemId) throws IOException {
        synchronized (LOCK) {
            checkItem(itemId);
            File index = new File(patientDir(patientId), "images.properties");
            Properties bindings = read(index);
            bindings.remove(itemId);
            write(index, bindings);
        }
    }

    /** One immutable snapshot per import. Missing legacy files are legitimate on a fresh install. */
    public File backupLegacy(String patientId, File... sources) throws IOException {
        synchronized (LOCK) {
            File backup = new File(patientDir(patientId), "backups/" + UUID.randomUUID());
            ensureDir(backup);
            for (int i = 0; i < sources.length; i++) {
                File source = sources[i];
                if (source == null || !source.exists()) continue;
                if (!source.isFile()) throw new IOException("Legacy source is not a file");
                File target = new File(backup, i + "-" + source.getName());
                if (!target.createNewFile()) throw new IOException("Backup already exists");
                try (InputStream input = new FileInputStream(source)) { copy(input, target); }
            }
            return backup;
        }
    }

    private File patientDir(String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new IOException("Invalid patient ID");
        File dir = new File(root, id);
        if (!dir.isDirectory() || !dir.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))
            throw new IOException("Unknown patient");
        return dir;
    }

    private static void checkItem(String id) throws IOException {
        if (id == null || id.trim().isEmpty() || id.equals("_version") || id.length() > 256) throw new IOException("Invalid AAC ID");
    }

    private static void ensureDir(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create private directory");
    }

    private static void copy(InputStream input, File target) throws IOException {
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) throw new IOException("Import exceeds 25 MiB");
                output.write(buffer, 0, count);
            }
            if (total == 0) throw new IOException("Empty import");
            output.getFD().sync();
        }
    }

    private static Properties read(File file) throws IOException {
        File backup = new File(file.getPath() + ".bak");
        if (backup.exists()) {
            if (file.exists() && !file.delete()) throw new IOException("Cannot recover index");
            if (!backup.renameTo(file)) throw new IOException("Cannot restore index");
        }
        Properties properties = new Properties();
        if (!file.exists()) return properties;
        try (InputStream input = new FileInputStream(file)) { properties.load(input); }
        if (!"1".equals(properties.getProperty("_version"))) throw new IOException("Unsupported index version");
        properties.remove("_version");
        return properties;
    }

    /** Recoverable atomic replacement: old index remains available until the new one is durable. */
    private static void write(File file, Properties properties) throws IOException {
        ensureDir(file.getParentFile());
        read(file); // Recover any interrupted previous write before starting a new one.
        File pending = new File(file.getPath() + ".new");
        File backup = new File(file.getPath() + ".bak");
        Properties serialized = new Properties();
        serialized.putAll(properties);
        serialized.setProperty("_version", "1");
        try (FileOutputStream output = new FileOutputStream(pending)) {
            serialized.store(output, "NovaRehab private patient images");
            output.getFD().sync();
        }
        if (file.exists() && !file.renameTo(backup)) throw new IOException("Cannot protect old index");
        if (!pending.renameTo(file)) throw new IOException("Cannot publish index");
        if (backup.exists() && !backup.delete()) throw new IOException("Cannot commit index");
    }
}
