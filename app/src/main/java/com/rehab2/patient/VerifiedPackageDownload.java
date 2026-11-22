package com.rehab2.patient;

import java.io.*;
import java.net.*;
import java.util.*;

/** Bounded HTTPS download. No profile/image binding is changed by this class. */
public final class VerifiedPackageDownload {
    public interface Cancelled { boolean get(); }
    public interface Progress { void update(long bytes, long total); }
    public interface Connections { HttpURLConnection open(URL url) throws IOException; }
    public static final String LANA_URL = "https://github.com/trsavecdusan-wq/rehab2/releases/download/v1.2.834/Lana_23_osebnih_ikon_in_pregled_490.zip";
    public static final String LANA_SHA256 = "cda03ddc17619b2122499bbf2cabaa6047760f194da004fd16dc754e85439f70";
    public static final long LANA_BYTES = 44229921L;
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"));
    private final Connections connections;
    public VerifiedPackageDownload() { this(url -> (HttpURLConnection)url.openConnection()); }
    public VerifiedPackageDownload(Connections connections) { this.connections=connections; }
    public File download(String address, String hash, long expected, File directory, Cancelled cancel, Progress progress) throws Exception {
        if (!hash.matches("[0-9a-f]{64}") || expected<1 || expected>PersonalIconZip.MAX_ZIP) throw new IOException("Neveljaven opis paketa.");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Zasebna shramba ni dosegljiva.");
        File target=File.createTempFile("download-", ".zip", directory);
        boolean complete=false;
        long started=System.nanoTime();
        try {
            URL url=new URL(address);
            for(int redirects=0;redirects<=5;redirects++) {
                if(cancel.get()) throw new IOException("Prenos preklican.");
                if(!"https".equals(url.getProtocol()) || !HOSTS.contains(url.getHost()) || url.getUserInfo()!=null || (url.getPort()!=-1 && url.getPort()!=443)) throw new IOException("Nedovoljen naslov prenosa.");
                HttpURLConnection connection=connections.open(url);
                try {
                    connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
                    connection.setRequestProperty("Accept-Encoding","identity");
                    connection.setRequestProperty("User-Agent","NovaRehab-private-icon-import");
                    int status=connection.getResponseCode();
                    if(status==301 || status==302 || status==303 || status==307 || status==308) {
                        String next=connection.getHeaderField("Location");if(next==null)throw new IOException("Manjka naslov preusmeritve.");
                        url=new URL(url,next);continue;
                    }
                    if(status!=200)throw new IOException("Paket ni dosegljiv (HTTP "+status+"). Poskusi znova pozneje.");
                    String length=connection.getHeaderField("Content-Length");
                    if(length!=null && Long.parseLong(length)!=expected)throw new IOException("Napačna velikost paketa.");
                    long total=0, notified=0;
                    try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(target)) {
                        byte[] buffer=new byte[32768];int n;
                        while((n=in.read(buffer))!=-1) {
                            if(cancel.get())throw new IOException("Prenos preklican.");
                            if(System.nanoTime()-started>180000000000L)throw new IOException("Prenos je trajal predolgo. Poskusi znova.");
                            total+=n;if(total>expected)throw new IOException("Paket je prevelik.");
                            out.write(buffer,0,n);
                            if(total-notified>=262144) { progress.update(total,expected);notified=total; }
                        }
                        out.getFD().sync();
                    }
                    if(total!=expected || !hash.equals(PersonalIconZip.sha256(target)))throw new IOException("Preverjanje SHA-256 ali velikosti ni uspelo. Slike niso spremenjene.");
                    if(cancel.get())throw new IOException("Prenos preklican.");
                    complete=true;return target;
                } finally { connection.disconnect(); }
            }
            throw new IOException("Preveč preusmeritev.");
        } finally { if(!complete)target.delete(); }
    }
}
