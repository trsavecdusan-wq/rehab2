import com.rehab2.patient.PatientImageStore;
import com.rehab2.patient.PatientProfileSwitcher;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PatientProfileSwitcherTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Check "+checks); }
    public static void main(String[] args) throws Exception {
        File dir=Files.createTempDirectory("patient-profile-switch-").toFile();
        File root=new File(dir,"private"), profile=new File(dir,"patient.json");
        byte[] first="{\"firstName\":\"A\",\"communicationLevel\":\"confirmed\",\"custom\":42}".getBytes("UTF-8");
        byte[] second="{\"firstName\":\"B\"}".getBytes("UTF-8");
        Files.write(profile.toPath(),first);
        PatientImageStore images=new PatientImageStore(root);
        String a=images.currentPatientId();
        File original=images.importImage(a,"thank_you","png",new ByteArrayInputStream(new byte[]{10,20,30}),f->true);
        boolean[] fail={false};
        PatientProfileSwitcher manager=new PatientProfileSwitcher(root,profile,(from,to)->{
            if (fail[0] && to.equals(profile)) throw new IOException("Simulated write failure");
            Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        });
        String b=manager.createPatient(second);
        check(!a.equals(b));check(images.currentPatientId().equals(a));
        manager.selectPatient(b);
        check(images.currentPatientId().equals(b));check(Arrays.equals(Files.readAllBytes(profile.toPath()),second));
        check(!images.hasBinding(b,"thank_you"));check(images.hasBinding(a,"thank_you"));
        check(!images.ownsLegacyImages(b));check(images.ownsLegacyImages(a));
        manager.selectPatient(a);
        check(Arrays.equals(Files.readAllBytes(profile.toPath()),first));
        check(images.resolve(a,"thank_you").equals(original));
        check(manager.listPatients().size()==2);
        byte[] edited="{\"firstName\":\"A edited\",\"custom\":43}".getBytes("UTF-8");
        Files.write(profile.toPath(),edited);manager.selectPatient(b);manager.selectPatient(a);
        check(Arrays.equals(Files.readAllBytes(profile.toPath()),edited));
        // Crash before UUID commit restores the previous profile.
        File journal=new File(root,"profile-switch.pending");
        Files.write(journal.toPath(),new byte[]{1});Files.write(profile.toPath(),second);
        manager.recover();check(images.currentPatientId().equals(a));
        check(Arrays.equals(Files.readAllBytes(profile.toPath()),edited));check(!journal.exists());
        // Crash after UUID commit restores the selected profile.
        images.selectPatient(b);Files.write(journal.toPath(),new byte[]{1});
        manager.recover();check(Arrays.equals(Files.readAllBytes(profile.toPath()),second));
        check(!journal.exists());
        try { manager.selectPatient("../invalid");throw new AssertionError(); } catch(IOException expected) { check(images.currentPatientId().equals(b)); }
        fail[0]=true;
        try { manager.selectPatient(a);throw new AssertionError(); } catch(IOException expected) { check(images.currentPatientId().equals(b)); }
        fail[0]=false;manager.recover();
        check(Arrays.equals(Files.readAllBytes(profile.toPath()),second));check(!journal.exists());
        check(Arrays.equals(Files.readAllBytes(original.toPath()),new byte[]{10,20,30}));
        System.out.println("PASS: "+checks+" patient switch, new profile, image isolation and recovery assertions");
    }
}
