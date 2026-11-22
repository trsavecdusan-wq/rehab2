package com.rehab2.patient;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.security.MessageDigest;
import org.json.*;

/** Offline, bounded, untrusted ZIP reader. ZIP paths are never used as output paths. */
public final class PersonalIconZip implements Closeable {
    public static final long MAX_ZIP = 80L * 1024 * 1024;
    private static final long MAX_FILE = 12L * 1024 * 1024, MAX_TOTAL = 100L * 1024 * 1024;
    public final List<Entry> entries = new ArrayList<>();
    public final File directory;
    public String archiveSha256;
    public static final class Entry {
        public final String motif, sha256, reviewNote;
        public final File file;
        public final List<String> candidates;
        public final int width, height;
        Entry(String m, String h, File f, List<String> c, int w, int t, String note) {
            motif=m; sha256=h; reviewNote=note; file=f; candidates=Collections.unmodifiableList(c); width=w; height=t;
        }
    }
    private PersonalIconZip(File dir) { directory=dir; }
    public static PersonalIconZip read(InputStream source, File privateParent) throws Exception {
        File dir = new File(privateParent, UUID.randomUUID().toString());
        if (!dir.mkdirs()) throw new IOException("Zasebne mape ni mogoče ustvariti.");
        PersonalIconZip result = new PersonalIconZip(dir);
        try {
            File archive = new File(dir,"package.zip");
            try (FileOutputStream out = new FileOutputStream(archive)) { transfer(source,out,MAX_ZIP); }
            result.archiveSha256=sha256(archive);
            Map<String,File> files = new HashMap<>();
            long total=0; int count=0;
            try (ZipFile zip = new ZipFile(archive)) {
                Set<String> names = new HashSet<>();
                Enumeration<? extends ZipEntry> all=zip.entries();
                while(all.hasMoreElements()) {
                    ZipEntry e=all.nextElement(); String name=e.getName();
                    if (++count>300 || name.length()>240 || name.startsWith("/") || name.contains("\\") || name.contains(":") ||
                        Arrays.asList(name.split("/",-1)).contains("..") || Arrays.asList(name.split("/",-1)).contains(".") ||
                        name.indexOf('\0')>=0 || !names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Nevarna ali podvojena pot v ZIP-u.");
                    if(e.isDirectory()) continue;
                    if(e.getSize()<0 || e.getSize()>MAX_FILE || (total+=e.getSize())>MAX_TOTAL) throw new IOException("Paket presega omejitev velikosti.");
                    File target=new File(dir,UUID.randomUUID().toString());
                    CRC32 crc=new CRC32(); long actual=0;
                    try(InputStream in=zip.getInputStream(e); FileOutputStream out=new FileOutputStream(target)) {
                        byte[] b=new byte[8192]; int n;
                        while((n=in.read(b))!=-1) { actual+=n; if(actual>e.getSize() || actual>MAX_FILE) throw new IOException("Napačna velikost datoteke."); crc.update(b,0,n); out.write(b,0,n); }
                    }
                    if(actual!=e.getSize() || crc.getValue()!=e.getCrc()) throw new IOException("Napačna kontrolna vsota ZIP.");
                    files.put(name,target);
                }
            }
            File manifest=files.get("Pripravljene_slike_23.json");
            if(manifest==null || manifest.length()>262144) throw new IOException("Manjka veljaven Pripravljene_slike_23.json.");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=new FileInputStream(manifest)) { transfer(in,bytes,262144); }
            JSONArray rows=new JSONArray(new String(bytes.toByteArray(),"UTF-8").replaceFirst("^\\uFEFF",""));
            if(rows.length()<1 || rows.length()>100) throw new IOException("Neveljavno število slik.");
            Set<String> used=new HashSet<>();
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i); String name=row.getString("file"), hash=row.getString("sha256");
                File image=files.get(name);
                if(!name.endsWith(".png") || image==null || !used.add(name) || !hash.matches("[0-9a-f]{64}") || !hash.equals(sha256(image))) throw new IOException("Slika ali SHA-256 ni veljaven: "+name);
                int w=row.getInt("width"),h=row.getInt("height");
                if(w<32 || h<32 || w>4096 || h>4096 || (long)w*h>8388608) throw new IOException("Prevelike mere slike.");
                List<String> ids=new ArrayList<>(); JSONArray candidates=row.getJSONArray("candidate_item_ids");
                if(candidates.length()>30) throw new IOException("Preveč predlaganih povezav.");
                for(int j=0;j<candidates.length();j++) { String id=candidates.getString(j); if(!id.matches("[a-z0-9_]{1,100}") || ids.contains(id)) throw new IOException("Neveljaven ID."); ids.add(id); }
                String motif=row.getString("motiv"); if(motif.length()>100) throw new IOException("Predolg naziv.");
                String note=row.optString("review_note", ""); if(note.length()>2000) throw new IOException("Predolga opomba.");
                result.entries.add(new Entry(motif,hash,image,ids,w,h,note));
            }
            archive.delete();
            for(File file:files.values()) { boolean keep=false; for(Entry e:result.entries) if(e.file.equals(file)) keep=true; if(!keep) file.delete(); }
            return result;
        } catch(Exception e) { result.close(); throw e; }
    }
    public static String sha256(File file) throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) { byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)d.update(b,0,n); }
        StringBuilder s=new StringBuilder();for(byte v:d.digest())s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();
    }
    private static void transfer(InputStream in,OutputStream out,long limit) throws IOException {
        byte[] b=new byte[8192];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("Datoteka je prevelika.");out.write(b,0,n);}
    }
    public void close() { File[] files=directory.listFiles();if(files!=null)for(File f:files)f.delete();directory.delete(); }
}
