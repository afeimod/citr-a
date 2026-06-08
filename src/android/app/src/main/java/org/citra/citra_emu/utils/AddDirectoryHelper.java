package org.citra.citra_emu.utils;

import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import org.citra.citra_emu.model.GameDatabase;
import org.citra.citra_emu.model.GameProvider;

public class AddDirectoryHelper {
    private Context mContext;

    public AddDirectoryHelper(Context context) {
        this.mContext = context;
    }

    /**
     * 加一个目录到 game library。Android 13+ scoped storage 下,用户用 SAF picker 选中的
     * 目录不能直接用 File.listFiles() 读,需要走 DocumentFile API。这个 helper 同时也接
     * 受纯 file path(例如 picker 把 treeUri 转出来的路径),但为了走 SAF 复制,
     * 这里入参的 dir 应当是 treeUri 字符串(比如 content://tree/...)。
     *
     * 调用前 FileBrowserHelper 应该 takePersistableUriPermission,这里再 take 一次
     * 不会报错。
     */
    public void addDirectory(String dir, AddDirectoryListener addDirectoryListener) {
        AsyncQueryHandler handler = new AsyncQueryHandler(mContext.getContentResolver()) {
            @Override
            protected void onInsertComplete(int token, Object cookie, Uri uri) {
                addDirectoryListener.onDirectoryAdded();
            }
        };

        ContentValues file = new ContentValues();
        file.put(GameDatabase.KEY_FOLDER_PATH, dir);

        handler.startInsert(0,                // We don't need to identify this call to the handler
                null,                        // We don't need to pass additional data to the handler
                GameProvider.URI_FOLDER,    // Tell the GameProvider we are adding a folder
                file);
    }

    public interface AddDirectoryListener {
        void onDirectoryAdded();
    }
}
