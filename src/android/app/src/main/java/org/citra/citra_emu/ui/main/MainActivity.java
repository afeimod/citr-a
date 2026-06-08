package org.citra.citra_emu.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.citra.citra_emu.NativeLibrary;
import org.citra.citra_emu.R;
import org.citra.citra_emu.activities.EmulationActivity;
import org.citra.citra_emu.features.settings.ui.SettingsActivity;
import org.citra.citra_emu.model.GameProvider;
import org.citra.citra_emu.ui.platform.PlatformGamesFragment;
import org.citra.citra_emu.utils.AddDirectoryHelper;
import org.citra.citra_emu.utils.BillingManager;
import org.citra.citra_emu.utils.DirectoryInitialization;
import org.citra.citra_emu.utils.FileBrowserHelper;
import org.citra.citra_emu.utils.PermissionsHandler;
import org.citra.citra_emu.utils.PicassoUtils;
import org.citra.citra_emu.utils.StartupHandler;
import org.citra.citra_emu.utils.ThemeUtil;

import java.util.Arrays;
import java.util.Collections;

/**
 * The main Activity of the Lollipop style UI. Manages several PlatformGamesFragments, which
 * individually display a grid of available games for each Fragment, in a tabbed layout.
 */
public final class MainActivity extends AppCompatActivity implements MainView {
    private Toolbar mToolbar;
    private int mFrameLayoutId;
    private PlatformGamesFragment mPlatformGamesFragment;

    private MainPresenter mPresenter = new MainPresenter(this);

    // Singleton to manage user billing state
    private static BillingManager mBillingManager;

    private static MenuItem mPremiumButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme();

        super.onCreate(savedInstanceState);
        // Android 11 (API 30) 起,setSystemUiVisibility() 被废弃,系统不再保证会跟着
        // 你的 flag 隐藏/恢复 system bars。改用 WindowCompat + WindowInsetsControllerCompat
        // 是官方推荐的现代写法。重要说明:
        //   setDecorFitsSystemWindows(false) 让内容画到 status bar / navigation bar 后面,
        //   否则就算你在 styles.xml 里设了 transparent,decor view 还是会自动添加 padding
        //   把内容推下去,状态栏挡的问题依然存在。
        //   hide(systemBars()) + BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE 是 "sticky immersive"
        //   模式:用户从边缘上滑/下滑能临时调出 system bars,几秒后自动隐藏。
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            // 根据当前主题明/暗决定 status bar 图标颜色。
            // 亮色背景(status bar 图标应该用黑色) vs 暗色背景(图标用白色)。
            int nightMode = getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            insetsController.setAppearanceLightStatusBars(
                    nightMode != android.content.res.Configuration.UI_MODE_NIGHT_YES);
            insetsController.setAppearanceLightNavigationBars(
                    nightMode != android.content.res.Configuration.UI_MODE_NIGHT_YES);
        }

        setContentView(R.layout.activity_main);

        findViews();

        setSupportActionBar(mToolbar);

        mFrameLayoutId = R.id.games_platform_frame;
        mPresenter.onCreate();

        // Android 13 (API 33) 起 POST_NOTIFICATIONS 变成 dangerous permission,
        // 不申请拿不到,后面 tryDismissRunningNotification / notification channel 都可能被静默拒掉。
        // 在主界面起来时请求一次,用户拒了也不影响主流程(只是通知不出来)。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        if (savedInstanceState == null) {
            StartupHandler.HandleInit(this);
            if (PermissionsHandler.hasWriteAccess(this)) {
                mPlatformGamesFragment = new PlatformGamesFragment();
                getSupportFragmentManager().beginTransaction().add(mFrameLayoutId, mPlatformGamesFragment)
                        .commit();
            }
        } else {
            mPlatformGamesFragment = (PlatformGamesFragment) getSupportFragmentManager().getFragment(savedInstanceState, "mPlatformGamesFragment");
        }
        PicassoUtils.init();

        // Setup billing manager, so we can globally query for Premium status.
        // 用 try-catch 守住:Billing 7.x 的 API 有变更,设备兼容性也参差,
        // 就算 BillingClient 初始化坏了也不能让 app 起不来。崩了把异常塞到 logcat,
        // 后面 mBillingManager == null 走 fallback 分支。
        try {
            mBillingManager = new BillingManager(this);
        } catch (Throwable t) {
            android.util.Log.e("CitraMain",
                    "BillingManager init failed: " + t.getClass().getName() + ": " + t.getMessage(), t);
            mBillingManager = null;
        }

        // Dismiss previous notifications (should not happen unless a crash occurred)
        EmulationActivity.tryDismissRunningNotification(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (PermissionsHandler.hasWriteAccess(this)) {
            if (getSupportFragmentManager() == null) {
                return;
            }
            if (outState == null) {
                return;
            }
            getSupportFragmentManager().putFragment(outState, "mPlatformGamesFragment", mPlatformGamesFragment);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPresenter.addDirIfNeeded(new AddDirectoryHelper(this));
    }

    // TODO: Replace with a ButterKnife injection.
    private void findViews() {
        mToolbar = findViewById(R.id.toolbar_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_game_grid, menu);
        mPremiumButton = menu.findItem(R.id.button_premium);

        // mBillingManager 可能为 null(onCreate 里的 try-catch 抓住 BillingClient 7.x 异常后)
        if (mBillingManager != null && mBillingManager.isPremiumCached()) {
            // User had premium in a previous session, hide upsell option
            setPremiumButtonVisible(false);
        }

        return true;
    }

    static public void setPremiumButtonVisible(boolean isVisible) {
        if (mPremiumButton != null) {
            mPremiumButton.setVisible(isVisible);
        }
    }

    /**
     * MainView
     */

    @Override
    public void setVersionString(String version) {
        mToolbar.setSubtitle(version);
    }

    @Override
    public void refresh() {
        getContentResolver().insert(GameProvider.URI_REFRESH, null);
        refreshFragment();
    }

    @Override
    public void launchSettingsActivity(String menuTag) {
        if (PermissionsHandler.hasWriteAccess(this)) {
            SettingsActivity.launch(this, menuTag, "");
        } else {
            PermissionsHandler.checkWritePermission(this);
        }
    }

    @Override
    public void launchFileListActivity(int request) {
        // MANAGE_EXTERNAL_STORAGE 检查已下沉到 FileBrowserHelper,
        // 那里统一处理(弹 toast + 跱设置页),这里不需要重复。
        if (PermissionsHandler.hasWriteAccess(this)) {
            switch (request) {
                case MainPresenter.REQUEST_ADD_DIRECTORY:
                    FileBrowserHelper.openDirectoryPicker(this,
                                                      MainPresenter.REQUEST_ADD_DIRECTORY,
                                                      R.string.select_game_folder,
                                                      Arrays.asList("elf", "axf", "cci", "3ds",
                                                                    "cxi", "app", "3dsx", "cia",
                                                                    "rar", "zip", "7z", "torrent",
                                                                    "tar", "gz"));
                    break;
                case MainPresenter.REQUEST_INSTALL_CIA:
                    FileBrowserHelper.openFilePicker(this, MainPresenter.REQUEST_INSTALL_CIA,
                                                     R.string.install_cia_title,
                                                     Collections.singletonList("cia"), true);
                    break;
            }
        } else {
            PermissionsHandler.checkWritePermission(this);
        }
    }

    /**
     * @param requestCode An int describing whether the Activity that is returning did so successfully.
     * @param resultCode  An int describing what Activity is giving us this callback.
     * @param result      The information the returning Activity is providing us.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        switch (requestCode) {
            case MainPresenter.REQUEST_ADD_DIRECTORY:
                // If the user picked a file, as opposed to just backing out.
                if (resultCode == MainActivity.RESULT_OK) {
                    // When a new directory is picked, we currently will reset the existing games
                    // database. This effectively means that only one game directory is supported.
                    // TODO(bunnei): Consider fixing this in the future, or removing code for this.
                    getContentResolver().insert(GameProvider.URI_RESET, null);
                    // Add the new directory
                    mPresenter.onDirectorySelected(FileBrowserHelper.getSelectedDirectory(result));
                }
                break;
                case MainPresenter.REQUEST_INSTALL_CIA:
                    // If the user picked a file, as opposed to just backing out.
                    if (resultCode == MainActivity.RESULT_OK) {
                        NativeLibrary.InstallCIAS(FileBrowserHelper.getSelectedFiles(result));
                        mPresenter.refeshGameList();
                    }
                    break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PermissionsHandler.REQUEST_CODE_WRITE_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    DirectoryInitialization.start(this);

                    mPlatformGamesFragment = new PlatformGamesFragment();
                    getSupportFragmentManager().beginTransaction().add(mFrameLayoutId, mPlatformGamesFragment)
                            .commit();

                    // Immediately prompt user to select a game directory on first boot
                    if (mPresenter != null) {
                        mPresenter.launchFileListActivity(MainPresenter.REQUEST_ADD_DIRECTORY);
                    }
                } else {
                    Toast.makeText(this, R.string.write_permission_needed, Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    /**
     * Called by the framework whenever any actionbar/toolbar icon is clicked.
     *
     * @param item The icon that was clicked on.
     * @return True if the event was handled, false to bubble it up to the OS.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mPresenter.handleOptionSelection(item.getItemId());
    }

    private void refreshFragment() {
        if (mPlatformGamesFragment != null) {
            mPlatformGamesFragment.refresh();
        }
    }

    @Override
    protected void onDestroy() {
        EmulationActivity.tryDismissRunningNotification(this);
        super.onDestroy();
    }

    /**
     * @return true if Premium subscription is currently active
     */
    public static boolean isPremiumActive() {
        return mBillingManager.isPremiumActive();
    }

    /**
     * Invokes the billing flow for Premium
     *
     * @param callback Optional callback, called once, on completion of billing
     */
    public static void invokePremiumBilling(Runnable callback) {
        mBillingManager.invokePremiumBilling(callback);
    }
}
