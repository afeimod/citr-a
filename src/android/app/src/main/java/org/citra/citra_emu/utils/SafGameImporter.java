// Copyright 2024 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import org.citra.citra_emu.model.GameDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 解决 Android 13+ scoped storage 下,SAF picker 选中的目录无法用 java.io.File 直接读
 * 的问题。
 *
 * 流程:
 * 1. user 在 picker 选了一个目录,SAF 返回 content://tree/... URI
 * 2. DirectoryInitialization / AddDirectoryHelper 把这个 treeUri 字符串存到 folders 表
 * 3. scanLibrary 调用本类的 importFromSafTreeUri,把树内 .cci/.3ds/.cxi 等文件
 *    全部复制到应用私有目录 getExternalFilesDir(null)/games/<filename>
 * 4. 复制后的 file path 走 native Loader::GetLoader 直接读,不再受 scoped storage 限制
 *
 * 这是一次性 work:复制完成后 treeUri 跟本地 cache path 都在 folders 表里。
 * 后续游戏启动、添加游戏、删除游戏都走本地 cache path,不依赖 SAF 权限。
 *
 * 注意:复制大游戏(单 .cci 可能 1-4 GB)会耗时,跑在 worker thread 上,UI 上有 ProgressDialog。
 */
public final class SafGameImporter {

    private static final String TAG = "SafGameImporter";

    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>(java.util.Arrays.asList(
            ".3ds", ".3dsx", ".elf", ".axf", ".cci", ".cxi", ".app"));

    /** 进度回调,参数是 (copied_count, total_count, current_filename) */
    public interface ProgressCallback {
        void onProgress(long copied, long total, String currentName);

        void onFinished(long copied);

        void onError(String message);
    }

    /**
     * 把 SAF treeUri 下的所有支持格式游戏复制到应用私有目录,返回复制的总文件数。
     * 如果 treeUri 是 null 或无权访问,返回 -1。
     */
    public static long importFromSafTreeUri(@NonNull Context context, @NonNull String treeUriString,
                                            @NonNull ProgressCallback callback) {
        Uri treeUri;
        try {
            treeUri = Uri.parse(treeUriString);
        } catch (Exception e) {
            callback.onError("Invalid tree URI: " + e.getMessage());
            return -1;
        }

        // 取出持久化 URI permission(系统会保留这个权限,只要 folders 表里还存着)
        try {
            context.getContentResolver().takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // 已经 take 过或无权限,继续尝试读取
        }

        DocumentFile tree;
        try {
            tree = DocumentFile.fromTreeUri(context, treeUri);
        } catch (Exception e) {
            callback.onError("Cannot open tree: " + e.getMessage());
            return -1;
        }
        if (tree == null || !tree.canRead()) {
            callback.onError("Tree not readable (permission denied?)");
            return -1;
        }

        File destDir = getAppGamesDir(context);
        if (destDir == null) {
            callback.onError("Cannot access app private directory");
            return -1;
        }

        // 第一遍:计数(用于进度)
        long total = countGamesRecursive(context, tree, SUPPORTED_EXTENSIONS);
        if (total == 0) {
            callback.onFinished(0);
            return 0;
        }

        // 第二遍:复制
        AtomicLong copied = new AtomicLong(0);
        boolean ok = copyRecursive(context, tree, destDir, SUPPORTED_EXTENSIONS, copied, total, callback);
        long finalCount = copied.get();
        if (ok) {
            callback.onFinished(finalCount);
        }
        return ok ? finalCount : -1;
    }

    /**
     * 检查 file path 是否属于应用私有 games 目录(即 .cci 已经被 SAF 复制过)
     */
    public static boolean isInAppGamesDir(Context context, String filePath) {
        if (context == null || filePath == null) return false;
        File destDir = getAppGamesDir(context);
        if (destDir == null) return false;
        try {
            File f = new File(filePath);
            String destCanonical = destDir.getCanonicalPath();
            String fileCanonical = f.getCanonicalPath();
            return fileCanonical.startsWith(destCanonical + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    public static File getAppGamesDir(Context context) {
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            // fallback 到 internal storage,卸载时会清
            externalFilesDir = new File(context.getFilesDir(), "external_files");
            if (!externalFilesDir.exists()) externalFilesDir.mkdirs();
        }
        File gamesDir = new File(externalFilesDir, "games");
        if (!gamesDir.exists()) gamesDir.mkdirs();
        return gamesDir;
    }

    // ---- 内部实现 ----

    private static long countGamesRecursive(Context context, DocumentFile parent, Set<String> exts) {
        long count = 0;
        try {
            for (DocumentFile child : parent.listFiles()) {
                if (child.isDirectory()) {
                    count += countGamesRecursive(context, child, exts);
                } else {
                    String name = child.getName();
                    if (name == null) continue;
                    int dot = name.lastIndexOf('.');
                    if (dot < 0) continue;
                    String ext = name.substring(dot).toLowerCase(Locale.ROOT);
                    if (exts.contains(ext)) {
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            Log.error(TAG + " countGamesRecursive failed: " + e.getMessage());
        }
        return count;
    }

    private static boolean copyRecursive(Context context, DocumentFile parent, File destDir,
                                        Set<String> exts, AtomicLong copied, long total,
                                        ProgressCallback callback) {
        DocumentFile[] children;
        try {
            children = parent.listFiles();
        } catch (Exception e) {
            callback.onError("List children failed: " + e.getMessage());
            return false;
        }
        if (children == null) return true;

        boolean success = true;
        for (DocumentFile child : children) {
            try {
                if (child.isDirectory()) {
                    File subDir = new File(destDir, sanitizeFilename(child.getName()));
                    if (!subDir.exists()) subDir.mkdirs();
                    if (!copyRecursive(context, child, subDir, exts, copied, total, callback)) {
                        success = false;
                    }
                } else {
                    String name = child.getName();
                    if (name == null) continue;
                    int dot = name.lastIndexOf('.');
                    if (dot < 0) continue;
                    String ext = name.substring(dot).toLowerCase(Locale.ROOT);
                    if (!exts.contains(ext)) continue;

                    callback.onProgress(copied.get(), total, name);

                    File target = new File(destDir, sanitizeFilename(name));
                    if (target.exists() && target.length() == child.length()) {
                        // 已存在且大小一致,跳过
                        copied.incrementAndGet();
                        continue;
                    }
                    if (copyFile(context, child.getUri(), target)) {
                        copied.incrementAndGet();
                    } else {
                        Log.error(TAG + " Failed to copy " + name);
                        success = false;
                    }
                }
            } catch (Exception e) {
                Log.error(TAG + " copy child failed: " + e.getMessage());
                success = false;
            }
        }
        return success;
    }

    private static boolean copyFile(Context context, Uri source, File dest) {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = resolver.openInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) return false;
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            Log.error(TAG + " copyFile " + source + " -> " + dest + " failed: " + e.getMessage());
            // 半成品删除
            if (dest.exists()) dest.delete();
            return false;
        }
    }

    private static String sanitizeFilename(String name) {
        if (name == null) return "_";
        // 去掉路径分隔符,避免逃出 destDir
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
