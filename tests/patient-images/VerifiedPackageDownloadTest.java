import com.rehab2.patient.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
public class VerifiedPackageDownloadTest {
 static int count;
 static void ok(boolean b){count++;if(!b)throw new AssertionError("check "+count);}
 interface Job {void run()throws Exception;}
 static void rejects(Job j)throws Exception{try{j.run();}catch(Exception e){count++;return;}throw new AssertionError("accepted invalid transfer");}
 static class Reply extends HttpURLConnection {
  byte[] data;int code;String location,length;boolean disconnected;
  Reply(URL u,byte[] data,int code,String location,String length){super(u);this.data=data;this.code=code;this.location=location;this.length=length;}
  public int getResponseCode(){return code;}public String getHeaderField(String key){return key.equals("Location")?location:key.equals("Content-Length")?length:null;}
  public InputStream getInputStream(){return new ByteArrayInputStream(data);}public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}public void connect(){}
 }
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory("verified-package").toFile(),source=new File(root,"source");byte[] data={1,2,3,4};Files.write(source.toPath(),data);String hash=PersonalIconZip.sha256(source);File dir=new File(root,"downloads");
  VerifiedPackageDownload valid=new VerifiedPackageDownload(u->new Reply(u,data,200,null,"4"));
  File downloaded=valid.download("https://github.com/test",hash,4,dir,()->false,(n,t)->{});ok(Arrays.equals(data,Files.readAllBytes(downloaded.toPath())));downloaded.delete();
  rejects(()->valid.download("http://github.com/test",hash,4,dir,()->false,(n,t)->{}));
  rejects(()->valid.download("https://evil.example/test",hash,4,dir,()->false,(n,t)->{}));
  rejects(()->valid.download("https://github.com:444/test",hash,4,dir,()->false,(n,t)->{}));
  rejects(()->valid.download("https://github.com/test","0".repeat(64),4,dir,()->false,(n,t)->{}));
  rejects(()->valid.download("https://github.com/test",hash,4,dir,()->true,(n,t)->{}));
  rejects(()->valid.download("https://github.com/test",hash,5,dir,()->false,(n,t)->{}));
  for(int code:new int[]{403,404,500})rejects(()->new VerifiedPackageDownload(u->new Reply(u,data,code,null,null)).download("https://github.com/test",hash,4,dir,()->false,(n,t)->{}));
  rejects(()->new VerifiedPackageDownload(u->new Reply(u,data,302,"http://github.com/no",null)).download("https://github.com/test",hash,4,dir,()->false,(n,t)->{}));
  rejects(()->new VerifiedPackageDownload(u->new Reply(u,data,302,"https://evil.example/no",null)).download("https://github.com/test",hash,4,dir,()->false,(n,t)->{}));
  rejects(()->new VerifiedPackageDownload(u->new Reply(u,data,302,"https://github.com/loop",null)).download("https://github.com/test",hash,4,dir,()->false,(n,t)->{}));
  rejects(()->new VerifiedPackageDownload(u->new Reply(u,new byte[]{1,2},200,null,null)).download("https://github.com/test",hash,4,dir,()->false,(n,t)->{}));
  rejects(()->new VerifiedPackageDownload(u->new Reply(u,new byte[8],200,null,null)).download("https://github.com/test",hash,4,dir,()->false,(n,t)->{}));
  rejects(()->new VerifiedPackageDownload(u->{throw new SocketTimeoutException();}).download("https://github.com/test",hash,4,dir,()->false,(n,t)->{}));
  ok(dir.list().length==0);
  File redirect=new VerifiedPackageDownload(u->new Reply(u,data,u.getHost().equals("github.com")?302:200,"https://release-assets.githubusercontent.com/test","4")).download("https://github.com/test",hash,4,dir,()->false,(n,t)->{});ok(redirect.isFile());redirect.delete();
  System.out.println("PASS: "+count+" download checks");
  if(args.length>0){File real=new VerifiedPackageDownload().download(VerifiedPackageDownload.LANA_URL,VerifiedPackageDownload.LANA_SHA256,VerifiedPackageDownload.LANA_BYTES,dir,()->false,(n,t)->{});ok(real.length()==44229921L);try(PersonalIconZip zip=PersonalIconZip.read(new FileInputStream(real),new File(root,"review"))){ok(zip.entries.size()==23);}real.delete();System.out.println("PASS: real public GitHub download / SHA256 / 23 entries");}
 }
}
