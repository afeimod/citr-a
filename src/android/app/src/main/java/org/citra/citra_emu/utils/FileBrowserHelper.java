package org.citra.citra_emu.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nononsenseapps.filepicker.Utils;

import org.citra.citra_emu.R;
import org.citra.citra_emu.activities.CustomFilePickerActivity;

import java.io.File;
import java.util.List;

public final class FileBrowserHelper {
    /**
     * Android 11+ scoped storage 下,项目里依赖的 com.nononsenseapps:filepicker:4.2.1
     * (2018 年就停止维护)要读 Environment.getExternalStorageDirectory() 整棵外部存储。
     * 系统在没拿到 MANAGE_EXTERNAL_STORAGE 权限时会直接拋 SecurityException,
     * Android 15 上更是在 launch 这个 Activity 的时候当场闪退。
     *
     * 这里在每次 launch file picker 之前都检一下,没拿到就弹 toast + 跱到系统设置页让用户授,
     * 授完返回再来点一次就能用。
     *
     * @return true 表示已授,可以直接 launch;false 表示跳设置去了,调用方不要再 launch。
     */
    private static boolean ensureManageStoragePermission(FragmentActivity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true; // 11 以下不需要这个权限
        }
        if (Environment.isExternalStorageManager()) {
            return true;
        }
        Toast.makeText(activity, R.string.manage_storage_permission_needed, Toast.LENGTH_LONG).show();
        try {
            activity.startActivity(new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (Exception e) {
            // 个别 ROM(如 MIUI)可能不支持 ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static void openDirectoryPicker(FragmentActivity activity, int requestCode, int title, List<String> extensions) {
        if (!ensureManageStoragePermission(activity)) {
            return;
        }
        Intent i = new Intent(activity, CustomFilePickerActivity.class);

        i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);
        i.putExtra(FilePickerActivity.EXTRA_START_PATH,
                Environment.getExternalStorageDirectory().getPath());
        i.putExtra(CustomFilePickerActivity.EXTRA_TITLE, title);
        i.putExtra(CustomFilePickerActivity.EXTRA_EXTENSIONS, String.join(",", extensions));

        activity.startActivityForResult(i, requestCode);
    }

    public static void openFilePicker(FragmentActivity activity, int requestCode, int title,
                                      List<String> extensions, boolean allowMultiple) {
        if (!ensureManageStoragePermission(activity)) {
            return;
        }
        Intent i = new Intent(activity, CustomFilePickerActivity.class);

        i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, allowMultiple);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
        i.putExtra(FilePickerActivity.EXTRA_START_PATH,
                Environment.getExternalStorageDirectory().getPath());
        i.putExtra(CustomFilePickerActivity.EXTRA_TITLE, title);
        i.putExtra(CustomFilePickerActivity.EXTRA_EXTENSIONS, String.join(",", extensions));

        activity.startActivityForResult(i, requestCode);
    }

    @Nullable
    public static String getSelectedDirectory(Intent result) {
        // Use the provided utility method to parse the result
        List<Uri> files = Utils.getSelectedFilesFromResult(result);
        if (!files.isEmpty()) {
            File file = Utils.getFileForUri(files.get(0));
            return file.getAbsolutePath();
        }

        return null;
    }

    @Nullable
    public static String[] getSelectedFiles(Intent result) {
        // Use the provided utility method to parse the result
        List<Uri> files = Utils.getSelectedFilesFromResult(result);
        if (!files.isEmpty()) {
            String[] paths = new String[files.size()];
            for (int i = 0; i < files.size(); i++)
                paths[i] = Utils.getFileForUri(files.get(i)).getAbsolutePath();
            return paths;
        }

        return null;
    }
}
