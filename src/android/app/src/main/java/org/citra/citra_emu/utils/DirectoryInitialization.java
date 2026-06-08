/**
 * Copyright 2014 Dolphin Emulator Project
 * Licensed under GPLv2+
 * Refer to the license.txt file included.
 */

package org.citra.citra_emu.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.citra.citra_emu.NativeLibrary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A service that spawns its own thread in order to copy several binary and shader files
 * from the Citra APK to the external file system.
 */
public final class DirectoryInitialization {
    public static final String BROADCAST_ACTION = "org.citra.citra_emu.BROADCAST";

    public static final String EXTRA_STATE = "directoryState";
    private static volatile DirectoryInitializationState directoryState = null;
    private static String userPath;
    private static AtomicBoolean isCitraDirectoryInitializationRunning = new AtomicBoolean(false);

    public static void start(Context context) {
        // Can take a few seconds to run, so don't block UI thread.
        //noinspection TrivialFunctionalExpressionUsage
        ((Runnable) () -> init(context)).run();
    }

    private static void init(Context context) {
        if (!isCitraDirectoryInitializationRunning.compareAndSet(false, true))
            return;

        if (directoryState != DirectoryInitializationState.CITRA_DIRECTORIES_INITIALIZED) {
            if (PermissionsHandler.hasWriteAccess(context)) {
                if (setCitraUserDirectory(context)) {
                    // 关键修复:必须把 userPath 推到 native,否则 native 端走默认的
                    // /sdcard/citra-emu/ 路径,Android 13+ 上 MediaProvider 直接 SecurityException,
                    // 然后 cfg.cpp:430 assertion,app 闪退。修在 native.cpp 的 SetUserDirectory
                    // 里面,这里只是补上 java 这一端漏掉的调用。
                    NativeLibrary.SetUserDirectory(userPath);
                    initializeInternalStorage(context);
                    NativeLibrary.CreateConfigFile();
                    directoryState = DirectoryInitializationState.CITRA_DIRECTORIES_INITIALIZED;
                } else {
                    directoryState = DirectoryInitializationState.CANT_FIND_EXTERNAL_STORAGE;
                }
            } else {
                directoryState = DirectoryInitializationState.EXTERNAL_STORAGE_PERMISSION_NEEDED;
            }
        }

        isCitraDirectoryInitializationRunning.set(false);
        sendBroadcastState(directoryState, context);
    }

    /**
     * HyperOS / MIUI 上面 Context.getExternalFilesDir(null) 第一次调用可能 null,
     * 这里重试 N 次,每次 sleep 一会儿。
     */
    private static File tryRetry(java.util.concurrent.Callable<File> supplier, int retries, long sleepMs) {
        File result = null;
        for (int i = 0; i < retries; i++) {
            try {
                result = supplier.call();
            } catch (Exception e) {
                Log.error("[DirectoryInitialization] tryRetry[" + i + "] threw: " + e.getMessage());
            }
            if (result != null) {
                return result;
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ignored) {
            }
        }
        return result;
    }

    private static void deleteDirectoryRecursively(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles())
                deleteDirectoryRecursively(child);
        }
        file.delete();
    }

    public static boolean areCitraDirectoriesReady() {
        return directoryState == DirectoryInitializationState.CITRA_DIRECTORIES_INITIALIZED;
    }

    public static String getUserDirectory() {
        if (directoryState == null) {
            throw new IllegalStateException("DirectoryInitialization has to run at least once!");
        } else if (isCitraDirectoryInitializationRunning.get()) {
            throw new IllegalStateException(
                    "DirectoryInitialization has to finish running first!");
        }
        return userPath;
    }

    private static native void SetSysDirectory(String path);

    private static boolean setCitraUserDirectory(Context context) {
        // 之前用 Environment.getExternalStorageDirectory() (/storage/emulated/0/citra-emu),
        // Android 11+ scoped storage 之后这个路径被系统隔离,Android 15 更是只有
        // MANAGE_EXTERNAL_STORAGE 才能读写,MIUI/HyperOS 还不让用户授这个权限,
        // 导致 config.ini / 存档 / shader cache 全部读不到,SettingsActivity 一点就崩。
        //
        // 改用 context.getExternalFilesDir(null) — app 私有的外部存储目录,
        // 任何 Android 版本都不需要任何特殊权限,跨 ROM (MIUI/ColorOS/原生) 都能读写。
        // 路径形如 /storage/emulated/0/Android/data/<package>/files
        // native 代码不关心具体路径,只要这个目录可写就行。
        //
        // getExternalFilesDir() 在个别 ROM 上可能返回 null(存储未挂载),
        // 走内部存储 fallback:Context.getFilesDir() — 总是可写,只是卸载会清。
        File[] candidates = new File[]{
                context.getExternalFilesDir(null),
                // 修 12:HyperOS 14 / MIUI 14 上面 Context.getExternalFilesDir(null) 第一次调用
                // 可能返回 null(存储未完全就绪),过几毫秒再调一次才返回真路径。HyperOS 还会
                // 异步创建这个目录,中间状态是 "目录存在但 isDirectory() 返回 false"。所以
                // 拿不到的时候重试 3 次、每次 50ms,再 fallback 到内部存储。
                tryRetry(() -> context.getExternalFilesDir(null), 3, 50),
                context.getFilesDir()
        };
        for (File dir : candidates) {
            if (dir == null) {
                Log.error("[DirectoryInitialization] candidate dir is null, skipping");
                continue;
            }
            try {
                if (!dir.exists()) {
                    boolean mkdirsOk = dir.mkdirs();
                    Log.debug("[DirectoryInitialization] mkdirs(" + dir + ") = " + mkdirsOk);
                }
                if (dir.isDirectory() && dir.canWrite()) {
                    userPath = dir.getAbsolutePath();
                    Log.debug("[DirectoryInitialization] User Dir: " + userPath);
                    return true;
                } else {
                    Log.error("[DirectoryInitialization] dir " + dir
                            + " isDirectory=" + dir.isDirectory()
                            + " canWrite=" + dir.canWrite());
                }
            } catch (Exception e) {
                Log.error("[DirectoryInitialization] dir " + dir + " failed: " + e.getMessage(), e);
            }
        }
        Log.error("[DirectoryInitialization] all candidates exhausted, user dir not set");
        return false;
    }

    private static void initializeInternalStorage(Context context) {
        File sysDirectory = new File(context.getFilesDir(), "Sys");

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String revision = NativeLibrary.GetGitRevision();
        if (!preferences.getString("sysDirectoryVersion", "").equals(revision)) {
            // There is no extracted Sys directory, or there is a Sys directory from another
            // version of Citra that might contain outdated files. Let's (re-)extract Sys.
            deleteDirectoryRecursively(sysDirectory);
            copyAssetFolder("Sys", sysDirectory, true, context);

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("sysDirectoryVersion", revision);
            editor.apply();
        }

        // Let the native code know where the Sys directory is.
        SetSysDirectory(sysDirectory.getPath());
    }

    private static void sendBroadcastState(DirectoryInitializationState state, Context context) {
        Intent localIntent =
                new Intent(BROADCAST_ACTION)
                        .putExtra(EXTRA_STATE, state);
        LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent);
    }

    private static void copyAsset(String asset, File output, Boolean overwrite, Context context) {
        Log.verbose("[DirectoryInitialization] Copying File " + asset + " to " + output);

        try {
            if (!output.exists() || overwrite) {
                InputStream in = context.getAssets().open(asset);
                OutputStream out = new FileOutputStream(output);
                copyFile(in, out);
                in.close();
                out.close();
            }
        } catch (IOException e) {
            Log.error("[DirectoryInitialization] Failed to copy asset file: " + asset +
                    e.getMessage());
        }
    }

    private static void copyAssetFolder(String assetFolder, File outputFolder, Boolean overwrite,
                                        Context context) {
        Log.verbose("[DirectoryInitialization] Copying Folder " + assetFolder + " to " +
                outputFolder);

        try {
            boolean createdFolder = false;
            for (String file : context.getAssets().list(assetFolder)) {
                if (!createdFolder) {
                    outputFolder.mkdir();
                    createdFolder = true;
                }
                copyAssetFolder(assetFolder + File.separator + file, new File(outputFolder, file),
                        overwrite, context);
                copyAsset(assetFolder + File.separator + file, new File(outputFolder, file), overwrite,
                        context);
            }
        } catch (IOException e) {
            Log.error("[DirectoryInitialization] Failed to copy asset folder: " + assetFolder +
                    e.getMessage());
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;

        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public enum DirectoryInitializationState {
        CITRA_DIRECTORIES_INITIALIZED,
        EXTERNAL_STORAGE_PERMISSION_NEEDED,
        CANT_FIND_EXTERNAL_STORAGE
    }
}
