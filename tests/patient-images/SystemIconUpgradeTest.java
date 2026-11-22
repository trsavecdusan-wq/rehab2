import com.rehab2.aac.SystemIconUpgrade;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Arrays;

public class SystemIconUpgradeTest {
    static int assertions;
    static void check(boolean result) { assertions++; if (!result) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("system-icon-upgrade-").toFile();
        byte[] old = {1,2,3}, current = {4,5,6}, personal = {7,8,9};
        File icon = new File(dir,"icon.png");
        SystemIconUpgrade.AtomicReplace replace = (a,b) -> Files.move(a.toPath(), b.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        check(SystemIconUpgrade.install(icon, old, Collections.emptySet(), replace).equals("updated"));
        check(SystemIconUpgrade.install(icon, old, Collections.emptySet(), replace).equals("current"));
        check(SystemIconUpgrade.install(icon, current, Collections.singleton(SystemIconUpgrade.sha256(old)), replace).equals("updated"));
        check(Arrays.equals(Files.readAllBytes(icon.toPath()), current));
        Files.write(icon.toPath(), personal);
        check(SystemIconUpgrade.install(icon, current, Collections.singleton(SystemIconUpgrade.sha256(old)), replace).equals("preserved_unknown"));
        check(Arrays.equals(Files.readAllBytes(icon.toPath()), personal));
        // Missing/corrupt catalog must fail closed for existing custom files.
        check(SystemIconUpgrade.install(icon, old, Collections.emptySet(), replace).equals("preserved_unknown"));
        check(Arrays.equals(Files.readAllBytes(icon.toPath()), personal));
        check(dir.listFiles().length == 1);
        try {
            SystemIconUpgrade.install(icon, current, Collections.singleton(SystemIconUpgrade.sha256(personal)),
                (a,b) -> { throw new java.io.IOException("Simulated interruption before replacement"); });
            throw new AssertionError("Failure must propagate");
        } catch (java.io.IOException expected) {
            check(Arrays.equals(Files.readAllBytes(icon.toPath()), personal));
            check(dir.listFiles().length == 1);
        }
        check(SystemIconUpgrade.sha256(new byte[0]).equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        System.out.println("PASS: " + assertions + " system image upgrade assertions");
    }
}
