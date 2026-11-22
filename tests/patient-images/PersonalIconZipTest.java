import com.rehab2.patient.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
public class PersonalIconZipTest {
 static int n;
 static void ok(boolean x){n++;if(!x)throw new AssertionError("check "+n);}
 interface Job {void run() throws Exception;}
 static void reject(Job j)throws Exception{try{j.run();}catch(Exception expected){n++;return;}throw new AssertionError("accepted invalid input");}
 static byte[] zip(Map<String,byte[]> files)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){for(var e:files.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}}return b.toByteArray();}
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory("private-zip-test").toFile(), stage=new File(root,"stage");
  File source=new File(root,"source.png");Files.write(source.toPath(),new byte[]{1,2,3,4});
  String hash=PersonalIconZip.sha256(source);
  JSONObject row=new JSONObject().put("motiv","test").put("file","slike/test.png").put("sha256",hash).put("width",128).put("height",128).put("candidate_item_ids",new JSONArray().put("thank_you"));
  Map<String,byte[]> files=new LinkedHashMap<>();files.put("slike/test.png",Files.readAllBytes(source.toPath()));files.put("Pripravljene_slike_23.json",new JSONArray().put(row).toString().getBytes("UTF-8"));
  try(PersonalIconZip p=PersonalIconZip.read(new ByteArrayInputStream(zip(files)),stage)){ok(p.entries.size()==1);ok(p.entries.get(0).sha256.equals(hash));ok(Arrays.equals(Files.readAllBytes(source.toPath()),Files.readAllBytes(p.entries.get(0).file.toPath())));}
  ok(stage.list().length==0);
  for(String path:new String[]{"../escape","/absolute","C:/escape","slike\\escape","a/../bad","./bad","SLIKE/TEST.PNG"}){files.put(path,new byte[]{1});reject(()->PersonalIconZip.read(new ByteArrayInputStream(zip(files)),stage));files.remove(path);}
  files.put("slike/test.png",new byte[]{9});reject(()->PersonalIconZip.read(new ByteArrayInputStream(zip(files)),stage));ok(stage.list().length==0);
  files.put("slike/test.png",Files.readAllBytes(source.toPath()));row.put("width",9000);files.put("Pripravljene_slike_23.json",new JSONArray().put(row).toString().getBytes("UTF-8"));reject(()->PersonalIconZip.read(new ByteArrayInputStream(zip(files)),stage));
  files.put("oversize",new byte[13*1024*1024]);reject(()->PersonalIconZip.read(new ByteArrayInputStream(zip(files)),stage));
  PatientImageStore store=new PatientImageStore(new File(root,"profiles"));String a=store.currentPatientId(),b=store.createPatient();
  File original=store.importImage(a,"thank_you","png",new ByteArrayInputStream(new byte[]{8}),f->true);
  Map<String,File> sources=new LinkedHashMap<>();sources.put("thank_you",source);sources.put("understand",source);
  Map<String,String> hashes=new HashMap<>();hashes.put("thank_you",hash);hashes.put("understand",hash);
  reject(()->store.importBatch(a,sources,hashes,false,f->true));ok(store.resolve(a,"thank_you").equals(original));
  hashes.put("understand","0".repeat(64));reject(()->store.importBatch(a,sources,hashes,true,f->true));ok(store.resolve(a,"thank_you").equals(original));ok(store.resolve(a,"understand")==null);
  hashes.put("understand",hash);File backup=store.importBatch(a,sources,hashes,true,f->true);ok(new File(backup,"before.properties").isFile());ok(original.exists());ok(PersonalIconZip.sha256(store.resolve(a,"thank_you")).equals(hash));ok(store.resolve(b,"thank_you")==null);
  PatientImageStore reopened=new PatientImageStore(new File(root,"profiles"));ok(reopened.resolve(a,"understand").isFile());
  store.selectPatient(b);reject(()->store.importBatch(a,sources,hashes,true,f->true));ok(store.resolve(b,"understand")==null);
  // Simulate process death after index replacement but before backup deletion: recover all old bindings.
  File index=new File(root,"profiles/"+a+"/images.properties");Files.copy(new File(backup,"before.properties").toPath(),new File(index.getPath()+".bak").toPath());
  ok(reopened.resolve(a,"thank_you").equals(original));ok(reopened.resolve(a,"understand")==null);
  System.out.println("Personal ZIP/batch assertions: "+n);
 }
}
