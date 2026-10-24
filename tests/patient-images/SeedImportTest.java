import com.rehab2.patient.PatientImageStore;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class SeedImportTest {
    static int assertions;
    static void check(boolean condition, String why) { assertions++; if (!condition) throw new AssertionError(why); }
    interface Op { void run() throws Exception; }
    static void rejects(Op op) throws Exception {
        try { op.run(); } catch (IOException expected) { assertions++; return; }
        throw new AssertionError("Expected blocked operation");
    }
    static String hash(byte[] bytes) throws Exception {
        StringBuilder value = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) value.append(String.format("%02x", b & 255));
        return value.toString();
    }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("novarehab-seed-test-").toFile();
        PatientImageStore store = new PatientImageStore(root);
        String patient = store.currentPatientId(), other = store.createPatient();
        byte[] photo = "photo fixture".getBytes("UTF-8");
        String checksum = hash(photo), pack = hash("package".getBytes("UTF-8"));
        PatientImageStore.Source source = () -> new ByteArrayInputStream(photo);
        PatientImageStore.Validator valid = file -> file.length() > 0;
        rejects(() -> store.importSeed(pack, patient, "please", checksum, source, valid));
        check(store.confirmedSeedPatient(pack) == null, "No consent is inferred from active patient");
        store.confirmSeedPatient(pack, patient);
        check(store.confirmedSeedPatient(pack).equals(patient), "Explicit confirmation persists");
        check(!store.seedComplete(pack, patient), "Confirmation is not import success");
        check(store.importSeed(pack, patient, "please", checksum, source, valid) == PatientImageStore.SeedResult.IMPORTED, "First import");
        File original = store.resolve(patient, "please");
        check(Arrays.equals(photo, Files.readAllBytes(original.toPath())), "Original byte integrity");
        PatientImageStore restarted = new PatientImageStore(root);
        check(restarted.currentPatientId().equals(patient), "Upgrade/restart preserves UUID");
        check(restarted.importSeed(pack, patient, "please", checksum, () -> { throw new IOException("Must not read"); }, valid)
            == PatientImageStore.SeedResult.SKIPPED_EXISTING, "Restart skips existing valid image");
        check(original.getParentFile().listFiles().length == 1, "Restart creates no duplicate");
        File custom = store.importImage(patient, "thank_you", "png", new ByteArrayInputStream("custom".getBytes("UTF-8")), valid);
        check(store.importSeed(pack, patient, "thank_you", checksum, source, valid) == PatientImageStore.SeedResult.SKIPPED_EXISTING, "Preserve manually imported image");
        check(store.resolve(patient, "thank_you").equals(custom), "Existing binding unchanged");
        store.selectPatient(other);
        rejects(() -> store.importSeed(pack, other, "please", checksum, source, valid));
        rejects(() -> store.importSeed(pack, patient, "please", checksum, source, valid));
        rejects(() -> store.confirmSeedPatient(pack, other));
        check(store.resolve(other, "please") == null, "No wrong-patient binding");
        store.selectPatient(patient);
        String interruptedPack = hash("interrupted".getBytes("UTF-8"));
        store.confirmSeedPatient(interruptedPack, patient);
        PatientImageStore.Source broken = () -> new InputStream() {
            int count;
            public int read() throws IOException { if (++count > 3) throw new IOException("interrupted"); return 1; }
        };
        rejects(() -> store.importSeed(interruptedPack, patient, "retry", checksum, broken, valid));
        check(!store.hasBinding(patient, "retry") && !store.seedComplete(interruptedPack, patient), "Interrupted copy is not success");
        check(Arrays.stream(original.getParentFile().listFiles()).noneMatch(f -> f.getName().endsWith(".pending")), "Failed temporary copy removed");
        rejects(() -> store.importSeed(interruptedPack, patient, "retry", checksum, source, file -> false));
        rejects(() -> store.importSeed(interruptedPack, patient, "retry", hash("wrong".getBytes("UTF-8")), source, valid));
        File invalidBackupSource = new File(root, "directory-not-file"); invalidBackupSource.mkdir();
        rejects(() -> store.importSeed(interruptedPack, patient, "retry", checksum, source, valid, invalidBackupSource));
        check(!store.hasBinding(patient, "retry"), "Backup failure prevents binding");
        int beforeRetry = original.getParentFile().listFiles().length;
        check(store.importSeed(interruptedPack, patient, "retry", checksum, source, valid) == PatientImageStore.SeedResult.RECOVERED, "Reuse original after interruption before binding");
        check(original.getParentFile().listFiles().length == beforeRetry, "Recovery creates no duplicate");
        check(!store.seedComplete(pack, patient), "Success not recorded prematurely");
        store.completeSeed(pack, patient, "please=IMPORTED;thank_you=SKIPPED_EXISTING");
        check(new PatientImageStore(root).seedComplete(pack, patient), "Completed package survives restart");
        check(store.resolve(patient, "thank_you").equals(custom), "Completion preserves previous picture");
        System.out.println("PASS: " + assertions + " seed assertions; fixtures: " + root);
    }
}
