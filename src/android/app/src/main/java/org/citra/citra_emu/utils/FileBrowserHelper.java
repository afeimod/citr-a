package org.citra.citra_emu.utils;

import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

public final class FileBrowserHelper {

    /**
     * 启动系统目录选择器(SAF 的 ACTION_OPEN_DOCUMENT_TREE)。
     *
     * 之前用 com.nononsenseapps:filepicker:4.2.1 那个 2018 年的死项目,直接读
     * Environment.getExternalStorageDirectory(),Android 11+ scoped storage 下被系统隔离,
     * Android 15 上直接闪退;而且它要 MANAGE_EXTERNAL_STORAGE 权限,MIUI/HyperOS
     * 在标准权限页里把这个权限藏了,用户根本授不了。
     *
     * 改用 Google 官方的 SAF 之后,完全不需要任何特殊权限,跨厂商(原生 Android / MIUI /
     * ColorOS / HyperOS 等)都能用,用户在系统文件管理器里选一个目录就行。
     */
    public static void openDirectoryPicker(FragmentActivity activity, int requestCode,
                                           int title, List<String> extensions) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(Intent.EXTRA_TITLE, activity.getString(title));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * 启动系统文件选择器(SAF 的 ACTION_OPEN_DOCUMENT,多选)。
     */
    public static void openFilePicker(FragmentActivity activity, int requestCode,
                                      int title, List<String> extensions,
                                      boolean allowMultiple) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
        intent.putExtra(Intent.EXTRA_TITLE, activity.getString(title));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * 从 ACTION_OPEN_DOCUMENT_TREE 的返回 Intent 里取出用户选的目录,
     * 并尝试把 content:// URI 转成实际的文件路径给 native 用。
     *
     * 这里有个坑:SAF 返回的是 content:// URI,native 代码要的是真路径。
     * 转换规则来自 DocumentsContract 的 Document ID 格式:
     *   - primary:Download         → /storage/emulated/0/Download
     *   - primary:Download:sub     → /storage/emulated/0/Download/sub
     *   - 1234-5678:DCIM           → /storage/1234-5678/DCIM  (SD 卡)
     * 取不到 / 转换失败时返回 null(让上层兜底)。
     */
    @Nullable
    public static String getSelectedDirectory(Intent result) {
        if (result == null || result.getData() == null) {
            return null;
        }
        return treeUriToPath(result.getData());
    }

    /**
     * 从 ACTION_OPEN_DOCUMENT 的返回 Intent 里取出用户选的文件(们),转成文件路径。
     * 多选走 getClipData(),单选走 getData()。
     */
    @Nullable
    public static String[] getSelectedFiles(Intent result) {
        if (result == null) {
            return null;
        }
        List<String> paths = new ArrayList<>();

        if (result.getClipData() != null) {
            int count = result.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri uri = result.getClipData().getItemAt(i).getUri();
                String p = documentUriToPath(uri);
                if (p != null) paths.add(p);
            }
        } else if (result.getData() != null) {
            String p = documentUriToPath(result.getData());
            if (p != null) paths.add(p);
        }

        if (paths.isEmpty()) return null;
        return paths.toArray(new String[0]);
    }

    @Nullable
    private static String treeUriToPath(Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            return documentIdToPath(docId);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String documentUriToPath(Uri docUri) {
        try {
            String docId = DocumentsContract.getDocumentId(docUri);
            return documentIdToPath(docId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * "primary:Download:sub" → "/storage/emulated/0/Download/sub"
     * "1234-5678:DCIM"       → "/storage/1234-5678/DCIM"
     */
    @Nullable
    private static String documentIdToPath(String docId) {
        if (docId == null) return null;
        String[] parts = docId.split(":");
        if (parts.length < 2) return null;

        String volume = parts[0];
        StringBuilder path = new StringBuilder();

        if ("primary".equals(volume)) {
            path.append("/storage/emulated/0/");
        } else {
            // SD 卡或其他 volume
            path.append("/storage/").append(volume).append("/");
        }

        for (int i = 1; i < parts.length; i++) {
            path.append(parts[i]);
            if (i < parts.length - 1) path.append("/");
        }
        return path.toString();
    }
}
