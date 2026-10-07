package com.webapp.windows;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android bridge for the Web_app Windows VM screen.
 *  It owns the Android Storage Access Framework selections and the VM files.
 *  The actual QEMU binary is intentionally kept separate so it can be replaced
 *  by an Android-compatible build without changing the UI or storage layer.
 */
public final class VmBridge {
    private static final int REQ_ISO = 4101;
    private static final int REQ_SHARED = 4102;
    private final Activity activity;
    private final File vmDir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Process vmProcess;
    private String pendingIsoResult = "";
    private String pendingSharedResult = "";

    public VmBridge(Activity activity) {
        this.activity = activity;
        vmDir = new File(activity.getFilesDir(), "windows10-vm");
        if (!vmDir.exists()) vmDir.mkdirs();
    }

    public File getVmDir() { return vmDir; }

    @JavascriptInterface
    public void pickWindowsIso() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-iso9660-image"});
        activity.startActivityForResult(i, REQ_ISO);
    }

    @JavascriptInterface
    public void pickSharedFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(i, REQ_SHARED);
    }

    @JavascriptInterface
    public String getVmInfo() {
        File iso = new File(vmDir, "windows10.iso");
        File disk = new File(vmDir, "windows10.img");
        File qemu = new File(new File(vmDir, "bin"), "qemu-system-x86_64");
        return "ISO=" + iso.exists() + ";DISK=" + disk.exists() + ";QEMU=" + qemu.exists() +
                ";RUNNING=" + (vmProcess != null && vmProcess.isAlive());
    }

    @JavascriptInterface
    public void createWindowsDisk(final int gigabytes) {
        final int size = Math.max(16, Math.min(gigabytes, 128));
        io.execute(() -> {
            File disk = new File(vmDir, "windows10.img");
            try (RandomAccessFile raf = new RandomAccessFile(disk, "rw")) {
                raf.setLength((long) size * 1024L * 1024L * 1024L);
                pendingSharedResult = "DISK_OK:" + size + "GB";
            } catch (IOException e) {
                pendingSharedResult = "ERROR:" + e.getMessage();
            }
        });
    }

    @JavascriptInterface
    public void startVm() {
        if (vmProcess != null && vmProcess.isAlive()) return;
        File qemu = new File(new File(vmDir, "bin"), "qemu-system-x86_64");
        File iso = new File(vmDir, "windows10.iso");
        File disk = new File(vmDir, "windows10.img");
        if (!qemu.exists() || !iso.exists() || !disk.exists()) return;

        try {
            qemu.setExecutable(true);
            // Headless QEMU/VNC mode. A display frontend can connect to 127.0.0.1:5900.
            ProcessBuilder pb = new ProcessBuilder(
                    qemu.getAbsolutePath(),
                    "-machine", "q35",
                    "-accel", "tcg,thread=multi",
                    "-cpu", "max",
                    "-smp", "2",
                    "-m", "2048",
                    "-drive", "file=" + disk.getAbsolutePath() + ",format=raw,if=ide",
                    "-cdrom", iso.getAbsolutePath(),
                    "-boot", "menu=on",
                    "-vga", "std",
                    "-usb",
                    "-device", "usb-tablet",
                    "-vnc", "127.0.0.1:0"
            );
            pb.directory(vmDir);
            pb.redirectErrorStream(true);
            vmProcess = pb.start();
        } catch (IOException ignored) {
            vmProcess = null;
        }
    }

    @JavascriptInterface
    public void stopVm() {
        if (vmProcess != null) {
            vmProcess.destroy();
            vmProcess = null;
        }
    }

    @JavascriptInterface
    public String getSharedFolderPath() {
        android.content.SharedPreferences p = activity.getSharedPreferences("vm", Activity.MODE_PRIVATE);
        return p.getString("shared_uri", "");
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_SHARED) {
            try {
                activity.getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {}
            activity.getSharedPreferences("vm", Activity.MODE_PRIVATE).edit()
                    .putString("shared_uri", uri.toString()).apply();
            pendingSharedResult = "SHARED_OK";
            return;
        }
        if (requestCode == REQ_ISO) {
            io.execute(() -> copyIso(uri));
        }
    }

    private void copyIso(Uri uri) {
        File out = new File(vmDir, "windows10.iso");
        try (FileInputStream in = new FileInputStream(activity.getContentResolver().openFileDescriptor(uri, "r").getFileDescriptor());
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) fos.write(buffer, 0, n);
            fos.flush();
            pendingIsoResult = "ISO_OK";
        } catch (Exception e) {
            pendingIsoResult = "ISO_ERROR:" + e.getMessage();
        }
    }

    @JavascriptInterface
    public String pollResult() {
        String r = pendingIsoResult + (pendingSharedResult.isEmpty() ? "" : (pendingIsoResult.isEmpty() ? "" : "|") + pendingSharedResult);
        pendingIsoResult = "";
        pendingSharedResult = "";
        return r;
    }
}
