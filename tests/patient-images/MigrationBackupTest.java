import com.rehab2.patient.PatientImageStore;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class MigrationBackupTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("migration-backup-test-").toFile();
        PatientImageStore store = new PatientImageStore(new File(dir, "private"));
        String patient = store.currentPatientId();
        File items = new File(dir, "aac_items.json");
        byte[] original = "{\"custom\":true}".getBytes("UTF-8");
        Files.write(items.toPath(), original);
        File backup = store.backupMigrationOnce(patient, "daily-798", items);
        check(Arrays.equals(original, Files.readAllBytes(new File(backup, "0-aac_items.json").toPath())));
        Files.write(items.toPath(), "after migration".getBytes("UTF-8"));
        check(store.backupMigrationOnce(patient, "daily-798", items).equals(backup));
        check(Arrays.equals(original, Files.readAllBytes(new File(backup, "0-aac_items.json").toPath())));
        check(new PatientImageStore(new File(dir, "private")).backupMigrationOnce(patient, "daily-798", items).equals(backup));
        File invalid = new File(dir, "not-a-file"); invalid.mkdir();
        try { store.backupMigrationOnce(patient, "retry", invalid); throw new AssertionError(); }
        catch (IOException expected) { checks++; }
        check("after migration".equals(new String(Files.readAllBytes(items.toPath()), "UTF-8")));
        File retried = store.backupMigrationOnce(patient, "retry", items);
        check(new File(retried, "0-aac_items.json").isFile());
        check(store.currentPatientId().equals(patient));
        check(store.backupMigrationOnce(patient, "fresh", new File(dir, "absent")).isDirectory());
        System.out.println("PASS: " + checks + " migration backup assertions");
    }
}
