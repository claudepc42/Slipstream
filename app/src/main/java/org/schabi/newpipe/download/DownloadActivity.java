package org.schabi.newpipe.download;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewTreeObserver;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ActivityDownloaderBinding;
import org.schabi.newpipe.qr.QrScanActivity;
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.FocusOverlayView;

import java.io.IOException;

import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.fragment.MissionsFragment;

public class DownloadActivity extends AppCompatActivity {

    private static final String TAG = "DownloadActivity";
    private static final String MISSIONS_FRAGMENT_TAG = "fragment_tag";

    private final ActivityResultLauncher<Intent> pickFolderLauncher =
            registerForActivityResult(new StartActivityForResult(), this::onFolderPicked);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        // Service
        final Intent i = new Intent();
        i.setClass(this, DownloadManagerService.class);
        startService(i);

        ThemeHelper.setTheme(this);

        super.onCreate(savedInstanceState);

        final ActivityDownloaderBinding downloaderBinding =
                ActivityDownloaderBinding.inflate(getLayoutInflater());
        setContentView(downloaderBinding.getRoot());

        setSupportActionBar(downloaderBinding.toolbarLayout.toolbar);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.downloads_title);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        getWindow().getDecorView().getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                updateFragments();
                getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        downloaderBinding.fabQrScan.setOnClickListener(v -> {
            startActivity(new Intent(this, QrScanActivity.class));
        });

        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.setupFocusObserver(this);
        }

        promptForDownloadFolderIfNeeded();
    }

    private void promptForDownloadFolderIfNeeded() {
        final String videoPathKey = getString(R.string.download_path_video_key);
        final String currentPath = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(videoPathKey, null);
        if (currentPath == null || currentPath.isEmpty()) {
            NoFileManagerSafeGuard.launchSafe(
                    pickFolderLauncher,
                    StoredDirectoryHelper.getPicker(this),
                    TAG,
                    this
            );
        }
    }

    private void onFolderPicked(final ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        final Uri uri = result.getData().getData();
        if (uri == null) {
            return;
        }
        try {
            grantUriPermission(getPackageName(), uri, StoredDirectoryHelper.PERMISSION_FLAGS);
            final StoredDirectoryHelper storage = new StoredDirectoryHelper(this, uri, null);
            if (!storage.canWrite()) {
                Log.w(TAG, "Picked folder not writable: " + uri);
                return;
            }
        } catch (final IOException err) {
            Log.e(TAG, "Error acquiring folder URI: " + uri, err);
            return;
        }
        final String uriString = uri.toString();
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString(getString(R.string.download_path_video_key), uriString)
                .putString(getString(R.string.download_path_audio_key), uriString)
                .apply();
    }

    private void updateFragments() {
        final MissionsFragment fragment = new MissionsFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame, fragment, MISSIONS_FRAGMENT_TAG)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        final MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.download_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
