import com.rehab2.patient.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class ImageRestoreTest {
 static int n;static void ok(boolean v){n++;if(!v)throw new AssertionError("check "+n);}
 interface Job {void run()throws Exception;}
 static void rejects(Job j)throws Exception{try{j.run();}catch(Exception e){n++;return;}throw new AssertionError("expected reject");}
 public static void main(String[] args)throws Exception {
  File root=Files.createTempDirectory("image-restore").toFile();PatientImageStore s=new PatientImageStore(root);String a=s.currentPatientId(),b=s.createPatient();
  File old=s.importImage(a,"thank_you","png",new ByteArrayInputStream(new byte[]{1}),f->true);
  File newer=new File(root,"new.png");Files.write(newer.toPath(),new byte[]{2});String h=PersonalIconZip.sha256(newer);
  File data=new File(root,"speech-cache-sentinel");Files.write(data.toPath(),new byte[]{9});
  File backup=s.importBatch(a,Map.of("thank_you",newer,"lets_go",newer),Map.of("thank_you",h,"lets_go",h),true,f->true);
  File imported=s.resolve(a,"thank_you");ok(!imported.equals(old));ok(s.imageBackups(a).contains(backup.getName()));ok(s.imageBackupBindings(a,backup.getName()).get("thank_you").equals(old));
  rejects(()->s.restoreImages(a,"../escape",f->true));rejects(()->s.restoreImages(a,backup.getName(),f->false));ok(s.resolve(a,"thank_you").equals(imported));
  s.selectPatient(b);rejects(()->s.restoreImages(a,backup.getName(),f->true));ok(s.resolve(b,"thank_you")==null);s.selectPatient(a);
  Set<String> before=new HashSet<>(s.imageBackups(a));s.restoreImages(a,backup.getName(),f->true);
  ok(s.resolve(a,"thank_you").equals(old));ok(s.resolve(a,"lets_go")==null);ok(imported.isFile());ok(Files.readAllBytes(data.toPath())[0]==9);
  String redo=s.imageBackups(a).stream().filter(x->!before.contains(x)).findFirst().orElseThrow();s.restoreImages(a,redo,f->true);ok(s.resolve(a,"thank_you").equals(imported));
  ok(new PatientImageStore(root).resolve(a,"thank_you").equals(imported));
  // A missing backup original must never erase the current collection.
  old.delete();rejects(()->s.restoreImages(a,backup.getName(),f->true));ok(s.resolve(a,"thank_you").equals(imported));
  s.selectPatient(b);File empty=s.importBatch(b,Map.of("yes",newer),Map.of("yes",h),false,f->true);
  s.restoreImages(b,empty.getName(),f->true);ok(s.resolve(b,"yes")==null);
  System.out.println("PASS: "+n+" restore checks");
 }
}
