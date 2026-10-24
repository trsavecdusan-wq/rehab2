import com.rehab2.patient.PatientImageStore;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Standalone JVM regression tests; no Android mocks or downloaded dependencies. */
public final class PatientImageStoreTest {
    static int assertions;
    static void check(boolean value, String why) { assertions++; if (!value) throw new AssertionError(why); }
    interface Operation { void run() throws Exception; }
    static void rejects(Operation action) throws Exception {
        try { action.run(); } catch (IOException expected) { assertions++; return; }
        throw new AssertionError("Expected failure");
    }
    static ByteArrayInputStream bytes(String value) throws Exception { return new ByteArrayInputStream(value.getBytes("UTF-8")); }
    static String text(File file) throws Exception { return new String(Files.readAllBytes(file.toPath()), "UTF-8"); }
    public static void main(String[] args) throws Exception {
        File temp = Files.createTempDirectory("novarehab-patient-images-").toFile();
        File root = new File(temp, "private");
        PatientImageStore store = new PatientImageStore(root);
        String first = store.currentPatientId();
        check(first.equals(new PatientImageStore(root).currentPatientId()), "ID survives restart");
        String second = store.createPatient();
        check(!first.equals(second), "Unique patient ID");
        check(first.equals(store.currentPatientId()), "Create does not select");
        check(store.ownsLegacyImages(first) && !store.ownsLegacyImages(second), "Legacy pictures belong only to original patient");
        File profile = new File(temp, "patient_profile.json");
        File items = new File(temp, "aac_items.json");
        File dom = new File(temp, "dom.json");
        Files.write(profile.toPath(), "{\"firstName\":\"Lana\",\"unknownField\":17}".getBytes("UTF-8"));
        Files.write(items.toPath(), "[{\"id\":\"thank_you\",\"imagePath\":\"system/thank_you.png\",\"speakTextSl\":\"Hvala.\"}]".getBytes("UTF-8"));
        Files.write(dom.toPath(), "{\"id\":\"dom\",\"itemIds\":[\"thank_you\"]}".getBytes("UTF-8"));
        byte[] oldProfile = Files.readAllBytes(profile.toPath()), oldItems = Files.readAllBytes(items.toPath()), oldDom = Files.readAllBytes(dom.toPath());
        File backup = store.backupLegacy(first, profile, items, null, new File(temp, "missing"));
        check(Arrays.equals(oldProfile, Files.readAllBytes(new File(backup, "0-patient_profile.json").toPath())), "Exact backup with unknown fields");
        check(Arrays.equals(oldItems, Files.readAllBytes(new File(backup, "1-aac_items.json").toPath())), "Exact AAC backup");
        File original = store.importImage(first, "thank_you", "png", bytes("original"), f -> true);
        check(text(original).equals("original"), "Unchanged original bytes");
        check(store.resolve(first, "thank_you").equals(original), "Existing AAC ID binding");
        check(store.resolve(second, "thank_you") == null, "Patient isolation");
        File replacement = store.importImage(first, "thank_you", "png", bytes("replacement"), f -> true);
        check(!replacement.equals(original) && text(original).equals("original"), "No overwrite");
        check(store.resolve(first, "thank_you").equals(replacement), "New binding");
        rejects(() -> store.importImage(first, "thank_you", "png", bytes("bad"), f -> false));
        rejects(() -> store.importImage(first, "thank_you", "png", bytes(""), f -> true));
        rejects(() -> store.importImage(first, "thank_you", "../png", bytes("bad"), f -> true));
        InputStream interrupted = new InputStream() { public int read() throws IOException { throw new IOException("disconnected"); } };
        rejects(() -> store.importImage(first, "thank_you", "jpg", interrupted, f -> true));
        check(store.resolve(first, "thank_you").equals(replacement), "Failed imports preserve binding");
        store.selectPatient(second);
        check(new PatientImageStore(root).currentPatientId().equals(second), "Selection persists");
        check(!store.ownsLegacyImages(second) && store.ownsLegacyImages(first), "Switch does not transfer legacy ownership");
        check(store.resolve(second, "thank_you") == null, "New patient uses fallback");
        store.selectPatient(first);
        check(replacement.delete() && store.resolve(first, "thank_you") == null, "Missing photo uses fallback");
        check(store.hasBinding(first, "thank_you"), "Missing bound photo requests system, not another legacy personal photo");
        store.clearBinding(first, "thank_you");
        check(!store.hasBinding(first, "thank_you"), "Explicit reset removes only personal overlay");
        check(original.exists() && store.resolve(first, "thank_you") == null, "Unbind preserves originals");
        rejects(() -> store.selectPatient("../other"));
        rejects(() -> store.selectPatient(UUID.randomUUID().toString()));
        rejects(() -> store.importImage(first, "_version", "png", bytes("bad"), f -> true));
        InputStream oversized = new InputStream() {
            long left = 25L * 1024 * 1024 + 1;
            public int read() { return left-- > 0 ? 1 : -1; }
            public int read(byte[] b, int off, int len) {
                if (left == 0) return -1;
                int count = (int)Math.min(left, len); left -= count; return count;
            }
        };
        rejects(() -> store.importImage(first, "thank_you", "png", oversized, f -> true));
        File index = new File(new File(root, first), "images.properties");
        File journal = new File(index.getPath() + ".bak");
        check(index.renameTo(journal), "Simulate interrupted replacement");
        Files.write(index.toPath(), "incomplete new index".getBytes("UTF-8"));
        check(store.resolve(first, "thank_you") == null && !journal.exists(), "Recover previous index");
        Files.write(index.toPath(), "_version=999\n".getBytes("UTF-8"));
        rejects(() -> store.resolve(first, "thank_you"));
        check(Arrays.equals(oldProfile, Files.readAllBytes(profile.toPath())), "Profile byte-for-byte unchanged");
        check(Arrays.equals(oldItems, Files.readAllBytes(items.toPath())), "AAC byte-for-byte unchanged");
        check(Arrays.equals(oldDom, Files.readAllBytes(dom.toPath())), "Communication profile unchanged");
        System.out.println("PASS: " + assertions + " assertions; fixtures: " + temp);
    }
}
