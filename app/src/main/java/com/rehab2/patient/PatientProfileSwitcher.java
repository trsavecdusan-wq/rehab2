package com.rehab2.patient;

import java.io.*;
import java.util.*;

/** Uses the image store's UUIDs; snapshots only the active patient's profile file. */
public final class PatientProfileSwitcher {
    private static final Object LOCK = new Object();
    public interface AtomicReplace { void replace(File source, File target) throws Exception; }
    private final File root, profile;
    private final PatientImageStore images;
    private final AtomicReplace replace;
    public PatientProfileSwitcher(File root, File profile, AtomicReplace replace) {
        this.root=root; this.profile=profile; this.replace=replace;
        this.images=new PatientImageStore(root);
    }
    private File snapshot(String id) throws IOException {
        try { if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid patient UUID",e); }
        File dir=new File(root,id);
        if (!dir.isDirectory()) throw new IOException("Unknown patient");
        return new File(dir,"profile-snapshot.json");
    }
    private File journal() { return new File(root,"profile-switch.pending"); }
    private byte[] read(File file) throws IOException {
        if (!file.exists()) return "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (InputStream input=new FileInputStream(file); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n;
            while ((n=input.read(buffer))!=-1) {
                if (output.size()+n>1024*1024) throw new IOException("Profile too large");
                output.write(buffer,0,n);
            }
            return output.toByteArray();
        }
    }
    private void write(File file, byte[] bytes) throws Exception {
        if (bytes.length>1024*1024) throw new IOException("Profile too large");
        File parent=file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create profile directory");
        File pending=File.createTempFile("profile-", ".tmp",parent);
        try {
            try (FileOutputStream output=new FileOutputStream(pending)) { output.write(bytes); output.getFD().sync(); }
            replace.replace(pending,file);
        } finally { if (pending.exists()) pending.delete(); }
    }
    /** A crash before the identity commit restores old data; after it restores new data. */
    public void recover() throws Exception {
        synchronized (LOCK) {
            if (!journal().exists()) return;
            File saved=snapshot(images.currentPatientId());
            if (!saved.isFile()) throw new IOException("Missing recovery profile");
            write(profile,read(saved));
            if (!journal().delete()) throw new IOException("Cannot finish profile recovery");
        }
    }
    public List<String> listPatients() throws Exception {
        synchronized (LOCK) {
            recover(); images.currentPatientId();
            List<String> ids=new ArrayList<>();
            File[] files=root.listFiles();
            if (files!=null) for (File file:files) {
                try { if (file.isDirectory() && UUID.fromString(file.getName()).toString().equals(file.getName())) ids.add(file.getName()); }
                catch (IllegalArgumentException ignored) {}
            }
            Collections.sort(ids); return ids;
        }
    }
    public byte[] profileFor(String id) throws Exception {
        synchronized (LOCK) { recover(); return read(id.equals(images.currentPatientId())?profile:snapshot(id)); }
    }
    public String createPatient(byte[] initialProfile) throws Exception {
        synchronized (LOCK) {
            recover(); images.currentPatientId(); // The original patient keeps legacy ownership.
            String id=images.createPatient(); write(snapshot(id),initialProfile); return id;
        }
    }
    public void selectPatient(String id) throws Exception {
        synchronized (LOCK) {
            recover(); String old=images.currentPatientId(); if (old.equals(id)) return;
            File next=snapshot(id);
            byte[] current=read(profile), target=read(next);
            write(snapshot(old),current); write(next,target);
            write(journal(),new byte[]{1});
            try {
                write(profile,target); images.selectPatient(id);
                if (!journal().delete()) throw new IOException("Cannot finish profile switch");
            } catch (Exception error) {
                try { recover(); } catch (Exception recovery) { error.addSuppressed(recovery); }
                throw error;
            }
        }
    }
}
