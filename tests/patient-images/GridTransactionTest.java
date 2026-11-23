import com.rehab2.patient.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class GridTransactionTest {
 static int n;static void ok(boolean v){n++;if(!v)throw new AssertionError("check "+n);}
 interface Job{void run()throws Exception;}
 static void rejects(Job j)throws Exception{try{j.run();}catch(Exception e){n++;return;}throw new AssertionError("expected failure");}
 static void save(File f,Properties p)throws Exception{p.setProperty("_version","1");try(FileOutputStream o=new FileOutputStream(f)){p.store(o,"");o.getFD().sync();}}
 public static void main(String[]args)throws Exception{
  File root=Files.createTempDirectory("grid-v4").toFile();PatientImageStore s=new PatientImageStore(root);String a=s.currentPatientId(),b=s.createPatient();
  File old=s.importImage(a,"yes","png",new ByteArrayInputStream(new byte[]{1}),f->true),src=new File(root,"source.png");Files.write(src.toPath(),new byte[]{2});String h=PersonalIconZip.sha256(src);
  ok(!s.gridV4(a));rejects(()->s.importGridV4(a,Map.of("yes",src),Map.of("yes",h),f->false));ok(!s.gridV4(a));ok(s.resolve(a,"yes").equals(old));
  File backup=s.importGridV4(a,Map.of("yes",src),Map.of("yes",h),f->true);File fresh=s.resolve(a,"yes");ok(s.gridV4(a));ok(!fresh.equals(old));ok(s.gridBackups(a).contains(backup.getName()));
  s.selectPatient(b);ok(!s.gridV4(b));ok(s.resolve(b,"yes")==null);rejects(()->s.restoreGrid(a,backup.getName()));rejects(()->s.importGridV4(a,Map.of("yes",src),Map.of("yes",h),f->true));s.selectPatient(a);
  s.restoreImages(a,backup.getName(),f->true);ok(s.gridV4(a));ok(s.resolve(a,"yes").equals(old));s.restoreGrid(a,backup.getName());ok(!s.gridV4(a));ok(old.isFile()&&fresh.isFile());
  // Crash after both writes but before commit: journal must restore the complete old state.
  s.importGridV4(a,Map.of("yes",src),Map.of("yes",h),f->true);
  Properties undo=new Properties();undo.setProperty("enabled","false");undo.setProperty("image.yes",old.getName());save(new File(root,a+"/grid-pending.properties"),undo);
  PatientImageStore reopened=new PatientImageStore(root);ok(!reopened.gridV4(a));ok(reopened.resolve(a,"yes").equals(old));ok(!new File(root,a+"/grid-pending.properties").exists());
  // A preserved .bak journal must also recover on the next read.
  save(new File(root,a+"/grid-pending.properties.bak"),undo);ok(!reopened.gridV4(a));ok(reopened.resolve(a,"yes").equals(old));
  rejects(()->s.restoreGrid(a,"../../other"));
  System.out.println("PASS: "+n+" transactional grid checks");
 }
}
