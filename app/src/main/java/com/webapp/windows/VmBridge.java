package com.webapp.windows;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android bridge used by the Web_app Windows VM screen. */
public final class VmBridge {
    private static final int REQ_ISO = 4101;
    private static final int REQ_EXPORT = 4102;
    private static final int REQ_IMPORT = 4103;
    private final Activity activity;
    private final File vmDir;
    private final File sharedDir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Process vmProcess;
    private String pendingResult = "";
    private String exportName = "";

    public VmBridge(Activity activity) {
        this.activity = activity;
        vmDir = new File(activity.getFilesDir(), "windows10-vm");
        sharedDir = new File(vmDir, "shared");
        if (!vmDir.exists()) vmDir.mkdirs();
        if (!sharedDir.exists()) sharedDir.mkdirs();
    }

    @JavascriptInterface
    public void pickWindowsIso() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-iso9660-image"});
        activity.startActivityForResult(i, REQ_ISO);
    }

    @JavascriptInterface
    public void importFilesToVm() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        activity.startActivityForResult(i, REQ_IMPORT);
    }

    @JavascriptInterface
    public String listSharedFiles() {
        StringBuilder b = new StringBuilder();
        File[] files = sharedDir.listFiles();
        if (files == null) return "";
        for (File f : files) if (f.isFile()) {
            if (b.length() > 0) b.append("\n");
            b.append(f.getName()).append("\t").append(f.length());
        }
        return b.toString();
    }

    @JavascriptInterface
    public void exportFileFromVm(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\")) return;
        File source = new File(sharedDir, name);
        if (!source.isFile()) return;
        exportName = name;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, name);
        activity.startActivityForResult(i, REQ_EXPORT);
    }

    @JavascriptInterface
    public String getVmInfo() {
        File iso = new File(vmDir, "windows10.iso");
        File disk = new File(vmDir, "windows10.img");
        File qemu = new File(new File(vmDir, "bin"), "qemu-system-x86_64");
        File[] files = sharedDir.listFiles();
        return "ISO=" + iso.exists() + ";DISK=" + disk.exists() + ";QEMU=" + qemu.exists() +
                ";SHARED=" + (files == null ? 0 : files.length) +
                ";RUNNING=" + (vmProcess != null && vmProcess.isAlive());
    }

    @JavascriptInterface
    public void createWindowsDisk(final int gigabytes) {
        final int size = Math.max(16, Math.min(gigabytes, 128));
        io.execute(() -> {
            File disk = new File(vmDir, "windows10.img");
            try (RandomAccessFile raf = new RandomAccessFile(disk, "rw")) {
                raf.setLength((long) size * 1024L * 1024L * 1024L);
                pendingResult = "DISK_OK:" + size + "GB";
            } catch (IOException e) {
                pendingResult = "ERROR:" + e.getMessage();
            }
        });
    }

    @JavascriptInterface
    public void startVm() {
        if (vmProcess != null && vmProcess.isAlive()) return;
        File qemu = new File(new File(vmDir, "bin"), "qemu-system-x86_64");
        File iso = new File(vmDir, "windows10.iso");
        File disk = new File(vmDir, "windows10.img");
        if (!qemu.exists() || !iso.exists() || !disk.exists()) {
            pendingResult = "VM_MISSING_COMPONENT";
            return;
        }
        try {
            qemu.setExecutable(true);
            ProcessBuilder pb = new ProcessBuilder(
                    qemu.getAbsolutePath(), "-machine", "q35",
                    "-accel", "tcg,thread=multi", "-cpu", "max", "-smp", "2", "-m", "2048",
                    "-drive", "file=" + disk.getAbsolutePath() + ",format=raw,if=ide",
                    "-drive", "file=fat:rw:" + sharedDir.getAbsolutePath() + ",format=raw,if=ide",
                    "-cdrom", iso.getAbsolutePath(), "-boot", "menu=on", "-vga", "std",
                    "-usb", "-device", "usb-tablet", "-vnc", "127.0.0.1:0");
            pb.directory(vmDir);
            pb.redirectErrorStream(true);
            vmProcess = pb.start();
            pendingResult = "VM_STARTED";
        } catch (IOException e) {
            vmProcess = null;
            pendingResult = "VM_ERROR:" + e.getMessage();
        }
    }

    @JavascriptInterface
    public void stopVm() {
        if (vmProcess != null) {
            vmProcess.destroy();
            vmProcess = null;
            pendingResult = "VM_STOPPED";
        }
    }

    @JavascriptInterface
    public String pollResult() {
        String r = pendingResult;
        pendingResult = "";
        return r;
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (requestCode == REQ_ISO && data.getData() != null) {
            final Uri uri = data.getData();
            io.execute(() -> copyUriToFile(uri, new File(vmDir, "windows10.iso"), "ISO_OK", "ISO_ERROR"));
        } else if (requestCode == REQ_IMPORT) {
            io.execute(() -> importSelection(data));
        } else if (requestCode == REQ_EXPORT && data.getData() != null && !exportName.isEmpty()) {
            final Uri destination = data.getData();
            final File source = new File(sharedDir, exportName);
            final String name = exportName;
            io.execute(() -> copyFileToUri(source, destination, "EXPORT_OK:" + name, "EXPORT_ERROR"));
            exportName = "";
        }
    }

    private void importSelection(Intent data) {
        int count = 0;
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) if (copyUriToShared(clip.getItemAt(i).getUri())) count++;
        } else if (data.getData() != null && copyUriToShared(data.getData())) count = 1;
        pendingResult = "IMPORT_OK:" + count;
    }

    private boolean copyUriToShared(Uri uri) {
        String name = "arquivo-" + System.currentTimeMillis();
        android.database.Cursor c = null;
        try {
            c = activity.getContentResolver().query(uri, new String[]{"_display_name"}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) name = sanitize(n);
            }
        } catch (Exception ignored) {} finally { if (c != null) c.close(); }
        return copyUriToFile(uri, new File(sharedDir, name), null, null);
    }

    private String sanitize(String n) { return n.replaceAll("[\\\\/:*?\"<>|]", "_"); }

    private boolean copyUriToFile(Uri uri, File target, String ok, String error) {
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("Não foi possível abrir o arquivo");
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.flush();
            if (ok != null) pendingResult = ok;
            return true;
        } catch (Exception e) {
            if (error != null) pendingResult = error + ":" + e.getMessage();
            return false;
        }
    }

    private boolean copyFileToUri(File source, Uri destination, String ok, String error) {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = activity.getContentResolver().openOutputStream(destination)) {
            if (out == null) throw new IOException("Não foi possível criar o arquivo");
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.flush();
            if (ok != null) pendingResult = ok;
            return true;
        } catch (Exception e) {
            if (error != null) pendingResult = error + ":" + e.getMessage();
            return false;
        }
    }
}
