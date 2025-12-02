package com.hijacker;

/*
    Copyright (C) 2019  Christos Kyriakopoulos
    Copyright (C) 2025  Christian <kimocoder> Bremvaag

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.Fragment;
import androidx.activity.OnBackPressedCallback;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import android.provider.Settings;
import android.util.JsonReader;
import androidx.core.content.pm.PackageInfoCompat;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.lang.ref.WeakReference;

import javax.net.ssl.HttpsURLConnection;

import static com.hijacker.AP.getAPByMac;
import static com.hijacker.CustomAction.TYPE_ST;
import static com.hijacker.Device.trimMac;
import static com.hijacker.IsolatedFragment.is_ap;
import static com.hijacker.Shell.getFreeShell;
import static com.hijacker.Shell.runOne;

public class MainActivity extends AppCompatActivity{
    static final String NETHUNTER_BOOTKALI_BASH = "/data/data/com.offsec.nethunter/files/scripts/bootkali_bash";
    static final String RELEASES_LINK = "https://api.github.com/repos/chrisk44/Hijacker/releases";
    static final String WORDLISTS_LINK = "https://api.github.com/repos/chrisk44/Hijacker/contents/wordlists";
    static final int BUFFER_SIZE = 1048576;
    static final int AIREPLAY_DEAUTH = 1, AIREPLAY_WEP = 2;
    static final int BAND_2 = 1, BAND_5 = 2, BAND_BOTH = 3;
    static final int FRAGMENT_AIRODUMP = R.id.nav_mylist, FRAGMENT_MDK = R.id.nav_mdk4, FRAGMENT_CRACK = R.id.nav_crack,
            FRAGMENT_REAVER = R.id.nav_reaver, FRAGMENT_HCXDUMPTOOL = R.id.nav_hcxdumptool, FRAGMENT_CUSTOM = R.id.nav_custom_actions, FRAGMENT_SETTINGS = R.id.nav_settings;
    static final int PROCESS_AIRODUMP=0, PROCESS_AIREPLAY=1, PROCESS_MDK_BF=2, PROCESS_MDK_DOS=3, PROCESS_AIRCRACK=4, PROCESS_REAVER=5, PROCESS_HCXDUMPTOOL=6;
    static final int SORT_NOSORT = 0, SORT_ESSID = 1, SORT_BEACONS_FRAMES = 2, SORT_DATA_FRAMES = 3, SORT_PWR = 4;
    static final int CHROOT_FOUND = 0, CHROOT_BIN_MISSING = 1, CHROOT_DIR_MISSING = 2, CHROOT_BOTH_MISSING = 3;
    //State variables
    static boolean wpacheckcont = false;
    static boolean notif_on = false, background = false;    //notif_on: notification should be shown, background: the app is running in the background
    static int aireplay_running = 0, currentFragment = FRAGMENT_AIRODUMP;         //Set currentFragment in onResume of each Fragment
    static String last_airodump = null, last_mdk = null, last_reaver = null;
    //Filters
    static boolean show_ap = true, show_st = true, show_na_st = true, wpa = true, wep = true, opn = true, wps = true;
    static final boolean[] show_ch = {true, false, false, false, false, false, false, false, false, false, false, false, false, false, false};
    static int pwr_filter = 120;
    static String manuf_filter = "";
    //Airodump list sort 
    static int sort = SORT_NOSORT;
    static boolean sort_reverse = false;
    static boolean toSort = false;     //Variable to mark that the list must be sorted, so Tile.sort() must be called
    //Views that need to be accessible globally
    // UI views - avoid static references to Activity views to prevent leaks
    TextView ap_count, st_count;                               //AP and ST count textviews in toolbar
    ProgressBar progress;
    Toolbar toolbar;
    View rootView;
    // Keep a static weak reference to application context only
    // Do not keep NotificationCompat.Builder instances as static fields (they hold a Context and may leak an Activity).
    // Keep the application context and build NotificationCompat.Builder on-demand.
    static Context appContext;
    // Use WeakReference to prevent memory leaks when Activity is recreated
    private static WeakReference<MainActivity> instanceRef;
    static NotificationManager mNotificationManager;
    // Notification builders cached (constructed with application context in setup)
    NotificationCompat.Builder notif;
    NotificationCompat.Builder error_notif;
    NotificationCompat.Builder handshake_notif;
    static FragmentManager mFragmentManager;
    static String path, cap_tmp_path, data_path, actions_path, wl_path, cap_path, reaver_sess_path, firm_backup_file, manufDBFile, arch, busybox, curl;             //path: App files path (ends with .../files)
    // Restored static globals used across the codebase
    static MyListAdapter adapter;
    static CustomActionAdapter custom_action_adapter;
    static FileExplorerAdapter file_explorer_adapter;
    static SharedPreferences pref;
    static SharedPreferences.Editor pref_edit;
    static final Drawable[] overflow = {null, null, null, null, null, null, null, null};
    static final SparseArray<String> navTitlesMap = new SparseArray<>();
    static int progress_int;
    static ClipboardManager clipboard;
    static Thread wpa_thread;
    static Runnable wpa_runnable;
    static Menu menu;
    static long last_action;
    static File aliases_file;
    static FileWriter aliases_in;
    static final HashMap<String, String> aliases = new HashMap<>();
    static HashMap<String, String> manufHashMap;
    // When true, Airodump should not reset the kernel driver's con_mode sysfs value.
    // Used by TestDialog when running an end-to-end tools test that manages con_mode itself.
    public static volatile boolean preventConModeReset = false;
    //App and device info
    static String versionName, deviceModel;
    static int versionCode;
    static String devChipset = "";
    static ActionBar actionBar;
    static String bootkali_init_bin = "bootkali_init";
    //Preferences - Defaults are in strings.xml
    static String iface, prefix, airodump_dir, aireplay_dir, aircrack_dir, mdk4bf_dir, mdk4dos_dir, reaver_dir, pixiewps_dir, hcxdumptool_dir, cowpatty_dir, chroot_dir,
            enable_monMode, disable_monMode, custom_chroot_cmd;
    static int deauthWait, band, airodump_update_interval;
    static boolean show_notif, show_details, airOnStartup, debug, show_client_count,
            monstart, always_cap, cont_on_fail, watchdog, target_deauth, enable_on_airodump, update_on_startup;

    WatchdogTask watchdogTask;
    // ReaverFragment instance removed as a field; navigation uses NavController to reach ReaverFragment
    DrawerLayout mDrawerLayout;
    NavigationView navigationView;
    // NavController for Jetpack Navigation
    private NavController navController;
    private NavHostFragment navHostFragment;
    // Public accessor for the NavController so other classes/fragments can navigate safely
    public NavController getNavController(){
        return navController;
    }

    // Safe getter for MainActivity instance that won't leak memory
    public static MainActivity getInstance() {
        return instanceRef != null ? instanceRef.get() : null;
    }

    // Ensure navController is initialized and wired to NavigationUI. Safe to call multiple times.
    private void ensureNavController() {
        try {
            if (navController == null && navHostFragment != null) {
                navController = navHostFragment.getNavController();
            }

            // Note: We use a custom NavigationItemSelectedListener set in onPreExecute,
            // so we don't call NavigationUI.setupWithNavController here as it would override it

            if (navController != null) {
                try {
                    navController.addOnDestinationChangedListener((controller, destination, args) -> {
                        currentFragment = destination.getId();
                        try { actionBar.setTitle(navTitlesMap.get(currentFragment)); } catch (Exception ignored) {}
                    });
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.w("HIJACKER/Navigation", "ensureNavController failed", e);
        }
    }

    private boolean askedAllFilesAccessDialogShown = false;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        instanceRef = new WeakReference<>(this);
        appContext = getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("HIJACKER/Uncaught", "Uncaught exception: " + throwable);
            StringBuilder stackTrace = new StringBuilder();
            stackTrace.append(throwable.getMessage()).append('\n');
            for(int i=0;i<throwable.getStackTrace().length;i++){
                stackTrace.append(throwable.getStackTrace()[i].toString()).append('\n');
            }

            Intent intent = new Intent();
            intent.setAction("com.hijacker.SendLogActivity");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("exception", stackTrace.toString());
            startActivity(intent);

            finish();
            // Removed System.exit(1) - let Android handle crash naturally for proper reporting
        });
        adapter = new MyListAdapter(); //ALWAYS BEFORE setContentView AND setup(), can't stress it enough...
        adapter.setNotifyOnChange(true);
        custom_action_adapter = new CustomActionAdapter();
        custom_action_adapter.setNotifyOnChange(true);
        file_explorer_adapter = new FileExplorerAdapter();
        file_explorer_adapter.setNotifyOnChange(true);

        setContentView(R.layout.activity_main);

        // Apply window insets for Android 15+ edge-to-edge support
        if (Build.VERSION.SDK_INT >= 35) { // Android 15+
            View rootView = findViewById(android.R.id.content);
            if (rootView != null) {
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                    androidx.core.graphics.Insets insets = windowInsets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    );
                    // Apply top inset to the toolbar
                    View myToolbar = findViewById(R.id.my_toolbar);
                    if (myToolbar != null) {
                        android.view.ViewGroup.MarginLayoutParams params =
                            (android.view.ViewGroup.MarginLayoutParams) myToolbar.getLayoutParams();
                        params.topMargin = insets.top;
                        myToolbar.setLayoutParams(params);
                    }
                    // Apply bottom insets to the fragment container
                    View fragmentContainer = findViewById(R.id.fragment1);
                    if (fragmentContainer != null) {
                        fragmentContainer.setPadding(
                            fragmentContainer.getPaddingLeft(),
                            fragmentContainer.getPaddingTop(),
                            fragmentContainer.getPaddingRight(),
                            insets.bottom
                        );
                    }
                    return windowInsets;
                });
            }
        }

        // Set click listeners explicitly for toolbar icons and counts
        findViewById(R.id.ap_icon).setOnClickListener(this::onAPStats);
        findViewById(R.id.ap_count).setOnClickListener(this::onAPStats);
        findViewById(R.id.st_icon).setOnClickListener(this::onAPStats);
        findViewById(R.id.st_count).setOnClickListener(this::onAPStats);
        // Initialize FragmentManager early so background setup tasks can show dialogs safely
        mFragmentManager = getSupportFragmentManager();

        // Ensure NavHostFragment is added to the container
        try {
            if (mFragmentManager.findFragmentById(R.id.fragment1) == null) {
                NavHostFragment navHost = NavHostFragment.create(R.navigation.nav_graph);
                mFragmentManager.beginTransaction()
                    .replace(R.id.fragment1, navHost)
                    .setPrimaryNavigationFragment(navHost)
                    .commitNow();
            }
            navHostFragment = (NavHostFragment) mFragmentManager.findFragmentById(R.id.fragment1);
        } catch (Exception e) {
            Log.w("HIJACKER/NavSetup", "Failed to add NavHostFragment", e);
        }

        // Initialize NotificationManager early to avoid NPE when notifications are used before setup finishes
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create notification channel for Android O+ so notifications posted by this app will appear
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mNotificationManager != null) {
            String channelId = getString(R.string.DEFAULT_CHANNEL_ID);
            CharSequence name = getString(R.string.notification_title); // readable channel name
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(getString(R.string.notification_channel_description));
            mNotificationManager.createNotificationChannel(channel);
        }

        // Register OnBackPressedCallback for modern back navigation handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Handle back press with Navigation Component or drawer
                if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    // Close drawer if open
                    mDrawerLayout.closeDrawer(GravityCompat.START);
                } else if (mFragmentManager != null && mFragmentManager.getBackStackEntryCount() > 1) {
                    // Pop back stack if not at root
                    mFragmentManager.popBackStackImmediate();
                } else {
                    // At root destination, show exit dialog
                    CustomDialog customDialog = new CustomDialog();
                    customDialog.setTitle(getString(R.string.exit_dialog_title));
                    customDialog.setMessage(getString(R.string.exit_dialog_message));
                    customDialog.setPositiveButton(getString(R.string.exit), () -> {
                        show_notif = false;
                        finish();
                    });
                    customDialog.setNegativeButton(getString(R.string.cancel), null);
                    customDialog.show(mFragmentManager, "CustomDialog for exit");
                }
            }
        });

        new SetupTask().start();
    }
    private class SetupTask {
        LoadingDialog loadingDialog;
        ErrorDialog errorDialog;
        CustomDialog customDialog;
        // Handler to post progress updates to the main thread (replacement for publishProgress)
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private java.util.concurrent.ExecutorService executor;

        private void postProgress(final String... progress){
            mainHandler.post(() -> {
                if(progress != null){
                    if(progress.length > 0 && progress[0] != null){
                        // Update loading dialog text
                        if(loadingDialog != null) loadingDialog.setText(progress[0]);
                    }else if(progress.length > 1 && progress[1] != null){
                        // Show error via ErrorDialog
                        if(errorDialog != null){
                            errorDialog.setMessage(progress[1]);
                            errorDialog.setTitle(progress.length == 3 ? progress[2] : null);
                            errorDialog.show(mFragmentManager, "ErrorDialog");
                        }
                    }
                }
            });
        }
        @SuppressLint("CommitPrefEdits")
        protected void onPreExecute(){
            pref = MainActivity.this.getSharedPreferences(MainActivity.this.getPackageName() + "_preferences", Context.MODE_PRIVATE);
            pref_edit = pref.edit();

            errorDialog = new ErrorDialog();
            loadingDialog = new LoadingDialog();
            loadingDialog.setInitText(getString(R.string.starting_hijacker));
            loadingDialog.show(mFragmentManager, "LoadingDialog");

            if(!pref.getBoolean("disclaimerAccepted", false)){
                //First start
                customDialog = new CustomDialog();
                customDialog.setCancelable(false);
                customDialog.setTitle(getString(R.string.disclaimer_title));
                customDialog.setMessage(getString(R.string.disclaimer));
                customDialog.setPositiveButton(getString(R.string.agree), () -> {
                    pref_edit.putBoolean("disclaimerAccepted", true);
                    pref_edit.apply();
                });
                // Exit
                customDialog.setNeutralButton(getString(R.string.not_agree), MainActivity.this::finish);

                customDialog.show(mFragmentManager, "CustomDialog for disclaimer");
            }

            //Initialize the drawer
            mDrawerLayout = findViewById(R.id.drawer_layout);
            navigationView = findViewById(R.id.nav_view);
            // Keep first item checked by default; actual navigation handling is wired later with NavController
            navigationView.getMenu().getItem(0).setChecked(true);

            //Initialize toolbar
            toolbar = findViewById(R.id.my_toolbar);
            setSupportActionBar(toolbar);
            toolbar.setOverflowIcon(overflow[0]);

            ActionBar actionbar = getSupportActionBar();
            if(actionbar!=null){
                actionbar.setDisplayHomeAsUpEnabled(true);
                actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);
            }else{
                Log.e("HIJACKER/SetupPreEx", "actionbar is null");
            }

            // Setup NavController and NavigationUI
            try{
                navHostFragment = (NavHostFragment) mFragmentManager.findFragmentById(R.id.fragment1);
                if(navHostFragment!=null){
                    navController = navHostFragment.getNavController();

                    // Keep currentFragment in sync with nav destination so other code remains compatible
                    navController.addOnDestinationChangedListener((controller, destination, args) -> {
                        currentFragment = destination.getId();
                        try{ actionBar.setTitle(navTitlesMap.get(currentFragment)); }catch(Exception ignored){}
                    });

                    // Intercept nav_mylist to choose between mylist and isolated dynamically
                    navigationView.setNavigationItemSelectedListener(menuItem -> {
                        // Always mark the item as checked to keep UI state
                        menuItem.setChecked(true);
                        int id = menuItem.getItemId();
                        if(id == R.id.nav_mylist){
                            if(navController != null){
                                int dest = (is_ap==null) ? R.id.nav_mylist : R.id.nav_isolated;
                                navController.navigate(dest);
                            }
                        } else {
                            // Default behavior: navigate directly
                            if(navController != null){
                                navController.navigate(id);
                            }
                        }
                        mDrawerLayout.closeDrawers();
                        return true;
                    });
                }
            }catch(Exception e){
                Log.w("HIJACKER/NavSetup", "Navigation setup failed", e);
            }
        }
        protected Boolean doInBackground(Void... params) {
            Looper.prepare();

            // Reference params to avoid "parameter 'params' is never used" warnings from static analysis.
            // The method intentionally ignores incoming params today, but we keep a harmless read
            // so tools know the parameter is intentionally unused.
            if (params != null && params.length > 0) {
                // no-op: parameter intentionally unused
                Object _unused = params[0];
                if (_unused == null) { /* nothing to do */ }
            }

            //Wait for disclaimer to be accepted
            if(customDialog!=null){
                customDialog._wait();
            }

            //Initialize managers
            postProgress(getString(R.string.loading_device_information));
            PackageManager manager = MainActivity.this.getPackageManager();
            PackageInfo info = null;
            try{
                info = manager.getPackageInfo(MainActivity.this.getPackageName(), 0);
                assert info.versionName != null;
                versionName = info.versionName.replace(" ", "_");
                // Use PackageInfoCompat to obtain the versionCode in a forward-compatible way
                versionCode = (int) PackageInfoCompat.getLongVersionCode(info);
            }catch(PackageManager.NameNotFoundException e){
                Log.e("HIJACKER/SetupTask", "PackageInfo error", e);
            }
            deviceModel = Build.MODEL;
            if(!deviceModel.startsWith(Build.MANUFACTURER)) deviceModel = Build.MANUFACTURER + " " + deviceModel;
            deviceModel = deviceModel.replace(" ", "_");
            //devChipset is set later because busybox needs to be extracted
            arch = System.getProperty("os.arch");

            //Find views
            postProgress(getString(R.string.init_views));
            ap_count = findViewById(R.id.ap_count);
            st_count = findViewById(R.id.st_count);
            progress = findViewById(R.id.progressBar);
            rootView = findViewById(R.id.fragment1);
            overflow[0] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow0);
            overflow[1] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow1);
            overflow[2] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow2);
            overflow[3] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow3);
            overflow[4] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow4);
            overflow[5] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow5);
            overflow[6] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow6);
            overflow[7] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow7);
            actionBar = getSupportActionBar();

            //Load defaults
            postProgress(getString(R.string.loading_defaults));
            iface = getString(R.string.iface);
            // Default to empty prefix — do not LD_PRELOAD unless user explicitly configures it
            prefix = "";
             enable_monMode = getString(R.string.enable_monMode);
             disable_monMode = getString(R.string.disable_monMode);
             enable_on_airodump = Boolean.parseBoolean(getString(R.string.enable_on_airodump));
            deauthWait = Integer.parseInt(getString(R.string.deauthWait));
            show_notif = Boolean.parseBoolean(getString(R.string.show_notif));
            show_details = Boolean.parseBoolean(getString(R.string.show_details));
            airOnStartup = Boolean.parseBoolean(getString(R.string.airOnStartup));
            debug = Boolean.parseBoolean(getString(R.string.debug));
            always_cap = Boolean.parseBoolean(getString(R.string.always_cap));
            chroot_dir = getString(R.string.chroot_dir);
            monstart = Boolean.parseBoolean(getString(R.string.monstart));
            custom_chroot_cmd = "";
            cont_on_fail = Boolean.parseBoolean(getString(R.string.cont_on_fail));
            watchdog = Boolean.parseBoolean(getString(R.string.watchdog));
            target_deauth = Boolean.parseBoolean(getString(R.string.target_deauth));
            update_on_startup = Boolean.parseBoolean(getString(R.string.auto_update));
            band = Integer.parseInt(getString(R.string.band));
            show_client_count = Boolean.parseBoolean(getString(R.string.show_client_count));

            //Load preferences
            postProgress(getString(R.string.loading_preferences));
            migratePreferences(); // Migrate preferences before loading
            loadPreferences();

            //Initialize paths
            postProgress(getString(R.string.init_files));
            path = getFilesDir().getAbsolutePath();
            cap_tmp_path = path + "/cap_tmp";
            data_path = Environment.getExternalStorageDirectory() + "/Hijacker";
            actions_path = data_path + "/actions";
            wl_path = data_path + "/wordlists";
            cap_path = data_path + "/capture_files";
            reaver_sess_path = data_path + "/reaver_sessions";
            firm_backup_file = data_path + "/fw_bcmdhd.orig.bin";
            manufDBFile = path + "/manuf.db";
            ArrayList<File> dirs = new ArrayList<>();
            dirs.add(new File(cap_tmp_path));
            dirs.add(new File(data_path));
            dirs.add(new File(actions_path));
            dirs.add(new File(wl_path));
            dirs.add(new File(cap_path));
            dirs.add(new File(reaver_sess_path));
            for(File dir : dirs){
                if(!dir.exists()){
                    dir.mkdir();
                }
            }
            //cap file directory used to be set by the user, so move everything to the new location
            if(pref.contains("cap_dir")){
                //Move capture files to new directory
                String old_dir = pref.getString("cap_dir", null);
                Toast.makeText(MainActivity.this, "Moving cap files from " + old_dir + " to " + cap_path, Toast.LENGTH_SHORT).show();
                runOne(" mv " + old_dir + "/* " + cap_path + "/ && rmdir " + old_dir);

                pref_edit.remove("cap_dir");
                pref_edit.apply();
            }else{
                //cap directory was never changed so there may be files in /sdcard/cap/
                File old_dir = new File("/sdcard/cap");
                if(old_dir.exists() && old_dir.isDirectory()){
                    File[] files = old_dir.listFiles();
                    if(files!=null){
                        Toast.makeText(MainActivity.this, "Moving cap files from " + old_dir.getAbsolutePath() + " to " + cap_path, Toast.LENGTH_LONG).show();
                        for(File f : Objects.requireNonNull(old_dir.listFiles())){
                            //Move all the files to the new directory
                            f.renameTo(new File(cap_path, f.getName()));
                        }
                    }
                    old_dir.delete();
                }
            }

            //Initialize notifications
            postProgress(getString(R.string.init_notifications));
                //Create intents
            Intent cancel_intent = new Intent(MainActivity.this, DismissReceiver.class);
            Intent stop_intent = new Intent(MainActivity.this, StopReceiver.class);
            Intent notificationIntent = new Intent(MainActivity.this, MainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            PendingIntent click_intent = PendingIntent.getActivity(MainActivity.this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

                // Get a channel ID
            String channelID;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                channelID = NotificationChannel.DEFAULT_CHANNEL_ID;
            }else{
                channelID = getString(R.string.DEFAULT_CHANNEL_ID);
            }

             //Create 'running' notification
             notif = new NotificationCompat.Builder(MainActivity.this.getApplicationContext(), channelID);
            notif.setContentTitle(getString(R.string.notification_title));
            notif.setContentText(" ");
            notif.setSmallIcon(R.drawable.ic_notification);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                notif.setColor(getColor(R.color.colorAccent));
            }
            notif.setDeleteIntent(PendingIntent.getBroadcast(MainActivity.this.getApplicationContext(), 0, cancel_intent, PendingIntent.FLAG_IMMUTABLE));
            notif.addAction(R.drawable.stop_drawable, getString(R.string.stop_attacks), PendingIntent.getBroadcast(MainActivity.this.getApplicationContext(), 0, stop_intent, PendingIntent.FLAG_IMMUTABLE));
            notif.setContentIntent(click_intent);

                //Create 'error' notification (used by watchdog)
            error_notif = new NotificationCompat.Builder(MainActivity.this.getApplicationContext(), channelID);
            error_notif.setSmallIcon(R.drawable.ic_notification);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                error_notif.setColor(getColor(android.R.color.holo_red_dark));
            }
            error_notif.setContentIntent(click_intent);
            error_notif.setVibrate(new long[]{500, 500});

                //Create 'handshake captured' notification (used by wpa_thread)
            handshake_notif = new NotificationCompat.Builder(MainActivity.this.getApplicationContext(), channelID);
            handshake_notif.setContentTitle(getString(R.string.handshake_captured));
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                handshake_notif.setColor(getColor(android.R.color.holo_green_dark));
            }
            handshake_notif.setSmallIcon(R.drawable.ic_notification);
            handshake_notif.setContentIntent(click_intent);
            handshake_notif.setVibrate(new long[]{500, 500});

            //Load strings for when they cannot be retrieved with getString or R.string
            postProgress(getString(R.string.loading_strings));
            ST.not_connected = getString(R.string.not_connected);
            ST.paired = getString(R.string.paired) + ' ';

            //Check permissions
            postProgress(getString(R.string.checking_permissions));
            // Request only the dangerous runtime permissions using the centralized helper.
            final String[] perms = PermissionUtils.getDangerousPermissions();
            runOnUiThread(() -> PermissionUtils.requestMissingPermissions(MainActivity.this, perms, 0));
            // Also request notification permission on Android 13+
            final String notifPerm = PermissionUtils.getNotificationPermission();
            if(notifPerm!=null){
                runOnUiThread(() -> PermissionUtils.requestMissingPermissions(MainActivity.this, new String[]{notifPerm}, 1));
            }
            // Wait briefly for user to respond to permission dialogs but do not block indefinitely.
            try{
                synchronized(MainActivity.this){
                    MainActivity.this.wait(5000);
                }
            }catch(InterruptedException ignored){}

            //Check for su
            postProgress(getString(R.string.checking_su));
            int exitCode = 1;
            try{
                Process su_proc = ProcessExecutor.execute("which", "su");
                su_proc.waitFor();
                exitCode = su_proc.exitValue();
            }catch(IOException | InterruptedException e){
                Log.e("HIJACKER/Setup", "which su failed", e);
            }
            if(exitCode != 0){
                Log.e("HIJACKER/Setup", "'which su' failed with code " + exitCode);
                postProgress(null, getString(R.string.su_notfound), getString(R.string.su_notfound_title));
                errorDialog._wait();
                return false;
            }

            //Check for root access
            postProgress(getString(R.string.requesting_root_access));
            exitCode = 1;
            try{
                Process su_proc = ProcessExecutor.executeAsRoot("id");
                su_proc.waitFor();
                exitCode = su_proc.exitValue();
            }catch(IOException | InterruptedException e){
                Log.e("HIJACKER/Setup", "su -c id failed", e);
            }
            if(exitCode != 0){
                Log.e("HIJACKER/Setup", "'su -c id' failed with code " + exitCode);
                postProgress(null, getString(R.string.no_root_access), getString(R.string.no_root_access_title));
                errorDialog._wait();
                return false;
            }

            //Setup tools (requires root)
            Log.d("HIJACKER/SetupTask", "Entering tools installation block");
            postProgress(getString(R.string.setting_up_tools));
            if(arch.equals("armv7l") || arch.equals("aarch64")) {
                String tools_location = path + "/bin/";
                String lib_location = path + "/lib/";

                //Create directories
                File bin = new File(path + "/bin");
                File lib = new File(path + "/lib");
                if(!bin.exists()){
                    if(!bin.mkdir()){
                        postProgress(null, getString(R.string.bin_not_created));
                        errorDialog._wait();
                        return false;
                    }
                }
                if(!lib.exists()){
                    if(!lib.mkdir()){
                        postProgress(null, getString(R.string.lib_not_created));
                        errorDialog._wait();
                        return false;
                    }
                }

                //Extract busybox (use extractWithLog so we can time out if it blocks)
                Log.d("HIJACKER/SetupTask", "About to extract busybox");
                extractWithLog("busybox", tools_location);
                Log.d("HIJACKER/SetupTask", "Finished extracting busybox, continuing setup");
                busybox = path + "/bin/busybox";

                //Extract curl
                Log.d("HIJACKER/SetupTask", "About to extract curl");
                extractWithLog("curl", tools_location);
                Log.d("HIJACKER/SetupTask", "Finished extracting curl");
                curl = path + "/bin/curl";

                //Extract tools
                boolean install = true;
                if(Objects.requireNonNull(bin.list()).length==21 && Objects.requireNonNull(lib.list()).length==2 && info!=null){
                    // Compare long version codes for accuracy on modern platforms
                    if(PackageInfoCompat.getLongVersionCode(info) == pref.getLong("tools_version", 0L)){
                        if(debug) Log.d("HIJACKER/SetupTask", "Tools already installed");
                        install = false;
                    }else{
                        File manufDB = new File(manufDBFile);
                        if(manufDB.exists()) manufDB.delete();
                    }
                }
                if(install){
                    Log.d("HIJACKER/SetupTask", "Starting extraction of tools (install=true)");
                    extractWithLog("airbase-ng", tools_location);
                    extractWithLog("aircrack-ng", tools_location);
                    extractWithLog("aireplay-ng", tools_location);
                    extractWithLog("airodump-ng", tools_location);
                    extractWithLog("besside-ng", tools_location);
                    extractWithLog("ivstools", tools_location);
                    extractWithLog("iw", tools_location);
                    extractWithLog("iwconfig", tools_location);
                    extractWithLog("iwlist", tools_location);
                    extractWithLog("iwpriv", tools_location);
                    extractWithLog("kstats", tools_location);
                    extractWithLog("makeivs-ng", tools_location);
                    extractWithLog("mdk4", tools_location);
                    extractWithLog("nc", tools_location);
                    extractWithLog("packetforge-ng", tools_location);
                    extractWithLog("reaver", tools_location);
                    extractWithLog("reaver-wash", tools_location);
                    extractWithLog("hcxdumptool", path);
                    extractWithLog("cowpatty", tools_location);
                    extractWithLog("pixiewps", tools_location);
                    extractWithLog("wesside-ng", tools_location);
                    extractWithLog("wpaclean", tools_location);
                    extractWithLog("libfakeioctl.so", lib_location);
                    extractWithLog("libnexmon.so", lib_location);

                    runOne("cd " + path + "/bin; mv mdk4 mdk4bf; cp mdk4bf mdk4dos");
                    // NOTE: ABI check for mdk4 removed — always use extracted packaged binaries.
                    Log.d("HIJACKER/SetupTask", "Skipping ABI check for extracted mdk4bf; using packaged binaries.");
                    mdk4bf_dir = path + "/bin/mdk4bf";
                    mdk4dos_dir = path + "/bin/mdk4dos";

                    if(info!=null){
                        pref_edit.putLong("tools_version", PackageInfoCompat.getLongVersionCode(info));
                        pref_edit.apply();
                    }
                    Log.d("HIJACKER/SetupTask", "Tool extraction complete");
                }

                //Detect device chipset
                postProgress(getString(R.string.detecting_device_chipset));
                Log.d("HIJACKER/SetupTask", "Detecting device chipset - starting shell operations");
                Shell shell = getFreeShell();

                String firmwarePath = findFirmwarePath(shell);
                if(firmwarePath!=null){
                    //Get chipset from firmware file
                    shell.run("strings " + firmwarePath + " | " + busybox + " grep \"FWID:\"; echo ENDOFSTRINGS");
                    devChipset = getLastLine(shell.getShell_out(), "ENDOFSTRINGS");
                    int index = devChipset.indexOf('-');
                    if(index != -1){
                        devChipset = devChipset.substring(0, index);
                    }
                }
                Log.i("HIJACKER/DetectDev", "devChipset is " + devChipset);
                Log.d("HIJACKER/SetupTask", "Device chipset detection complete: " + devChipset);
                shell.done();

                // Set directories
                // Respect explicit user-configured prefix if present (including an explicit empty string to disable LD_PRELOAD).
                if(pref.contains("prefix")){
                    prefix = pref.getString("prefix", "");
                    Log.d("HIJACKER/SetupTask", "Using user-configured prefix from preferences: '" + prefix + "'");
                } else {
                    // No user-configured prefix: do NOT enable LD_PRELOAD automatically. Leave prefix empty.
                    prefix = "";
                    Log.d("HIJACKER/SetupTask", "No user prefix configured; leaving prefix empty (no LD_PRELOAD)");
                }

                airodump_dir = path + "/bin/airodump-ng";
                aireplay_dir = path + "/bin/aireplay-ng";
                aircrack_dir = path + "/bin/aircrack-ng";
                mdk4bf_dir = path + "/bin/mdk4bf";
                mdk4dos_dir = path + "/bin/mdk4dos";
                reaver_dir = path + "/bin/reaver";
                pixiewps_dir = path + "/bin/pixiewps";
                hcxdumptool_dir = path + "/hcxdumptool";
                cowpatty_dir = path + "/bin/cowpatty";
            } else {
                Log.e("HIJACKER/onCreate", "Device not armv7l or aarch64, can't install tools");
                busybox = "busybox";
                curl = "curl";
                postProgress(null, getString(R.string.not_arm));
                errorDialog._wait();

                // Respect explicit user-configured prefix even on non-ARM devices; otherwise leave empty
                if(pref.contains("prefix")) {
                    prefix = pref.getString("prefix", "");
                } else {
                    prefix = "";
                }
                airodump_dir = "airodump-ng";
                aireplay_dir = "aireplay-ng";
                aircrack_dir = "aircrack-ng";
                mdk4bf_dir = "mdk4";
                mdk4dos_dir = "mdk4";
                reaver_dir = "reaver";
                pixiewps_dir = "pixiewps";
                hcxdumptool_dir = "hcxdumptool";
                cowpatty_dir = "cowpatty";
            }

            //Initialize RootFile (requires root) and Airodump
            postProgress(getString(R.string.init_rootFile_airodump));
            RootFile.init();
            // Use new API-21-safe CapFileObserver implementation
            Airodump.capFileObserver = new AirodumpCapFileObserver(cap_tmp_path, 0);
            Log.d("HIJACKER/SetupTask", "RootFile and Airodump initialized");

            //Initialize threads
            postProgress(getString(R.string.init_threads));
            wpa_runnable = () -> {
                if(debug) Log.d("HIJACKER/wpa_thread", "Started wpa_thread");

                Thread counter_thread = new Thread(() -> {
                    if(debug) Log.d("HIJACKER/wpa_subthread", "wpa_subthread started");
                    progress_int = 0;
                    final java.util.concurrent.ScheduledExecutorService counterExec = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "wpa-counter"));
                    final java.util.concurrent.CountDownLatch counterLatch = new java.util.concurrent.CountDownLatch(1);
                    try{
                        final java.util.concurrent.ScheduledFuture<?> sf = counterExec.scheduleWithFixedDelay(() -> {
                            if(!wpacheckcont || progress_int > deauthWait){
                                counterLatch.countDown();
                                return;
                            }
                            progress_int++;
                            runInHandler(() -> progress.setProgress(progress_int));
                            if(progress_int > deauthWait) counterLatch.countDown();
                        }, 1, 1, TimeUnit.SECONDS);

                        // Wait until either counter completes or timeout
                        try{
                            counterLatch.await((long)deauthWait + 5, TimeUnit.SECONDS);
                        }catch(InterruptedException ie){ Thread.currentThread().interrupt(); }

                        sf.cancel(true);

                        if(wpacheckcont){
                            runInHandler(() -> {
                                if(!background) Snackbar.make(findViewById(R.id.fragment1), getString(R.string.stopped_to_capture), Snackbar.LENGTH_SHORT).show();
                                else Toast.makeText(MainActivity.this, getString(R.string.stopped_to_capture), Toast.LENGTH_SHORT).show();
                                progress.setProgress(deauthWait);
                                progress.setIndeterminate(true);
                            });
                        }
                    }finally{
                        try{ counterExec.shutdownNow(); }catch(Exception ignored){}
                        stop(PROCESS_AIREPLAY);
                    }
                    if(debug) Log.d("HIJACKER/wpa_subthread", "wpa_subthread finished");
                });

                final String capfile = Airodump.getCapFile();
                // Make handshakeFound visible to the finally block so we can act on detection after polling.
                final java.util.concurrent.atomic.AtomicBoolean handshakeFound = new java.util.concurrent.atomic.AtomicBoolean(false);
                Shell shell = getFreeShell();
                try{
                    if(capfile==null){
                        if(debug) Log.d("HIJACKER/wpa_thread", "cap file not found, airodump is probably not running...");
                    }else{
                        if(debug) Log.d("HIJACKER/wpa_thread", capfile);
                        wpacheckcont = true;
                        counter_thread.start();

                        // Poll for handshake using a scheduled task instead of sleeping in a loop
                        final java.util.concurrent.ScheduledExecutorService checkExec = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "wpa-checker"));
                        final java.util.concurrent.CountDownLatch checkLatch = new java.util.concurrent.CountDownLatch(1);
                        final java.util.concurrent.ScheduledFuture<?> checkFuture = checkExec.scheduleWithFixedDelay(() -> {
                            if(!wpacheckcont) {
                                checkLatch.countDown();
                                return;
                            }
                            try{
                                if(debug) Log.d("HIJACKER/wpa_thread", "Checking cap file...");
                                shell.run(aircrack_dir + " " + capfile + "; echo ENDOFAIR");
                                String line;
                                BufferedReader br = shell.getShell_out();
                                while((line = br.readLine()) != null){
                                    if("ENDOFAIR".equals(line)) break;
                                    if(line.length()>=56){
                                        char c = line.charAt(56);
                                        if(c=='1' || c=='2' || c=='3'){
                                            // handshake captured
                                            handshakeFound.set(true);
                                            wpacheckcont = false; // stop counter
                                            runInHandler(() -> {
                                                Button crack_btn = findViewById(R.id.crack);
                                                if(crack_btn!=null) crack_btn.setText(getString(R.string.crack));
                                                if(!background){
                                                    Snackbar s = Snackbar.make(findViewById(R.id.fragment1), getString(R.string.handshake_captured) + ' ' + capfile, Snackbar.LENGTH_LONG);
                                                    s.setAction(R.string.crack, v -> {
                                                        // Navigate to crack fragment - user can select the capfile from file explorer
                                                        // CrackFragment now uses ViewModel for state management
                                                        try{ NavHostFragment navHost = (NavHostFragment) mFragmentManager.findFragmentById(R.id.fragment1);
                                                            if(navHost!=null){ NavController nav = navHost.getNavController(); nav.navigate(R.id.nav_crack); }
                                                        }catch(Exception ex){ Log.w("HIJACKER/Navigation", "Failed to navigate to crack", ex); }
                                                    });
                                                    s.show();
                                                } else {
                                                    String notifChannel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? NotificationChannel.DEFAULT_CHANNEL_ID : MainActivity.this.getString(R.string.DEFAULT_CHANNEL_ID);
                                                    NotificationCompat.Builder nb = new NotificationCompat.Builder(appContext, notifChannel)
                                                            .setContentTitle(getString(R.string.handshake_captured))
                                                            .setContentText(getString(R.string.saved_in_file) + ' ' + capfile)
                                                            .setSmallIcon(R.drawable.ic_notification)
                                                            .setAutoCancel(true)
                                                            .setPriority(NotificationCompat.PRIORITY_HIGH);
                                                    if (mNotificationManager != null) mNotificationManager.notify(2, nb.build());
                                                }
                                            });
                                             checkLatch.countDown();
                                             break;
                                         }
                                     }
                                 }
                             }catch(Exception e){
                                 Log.e("HIJACKER/Exception", "Caught Exception in wpa_thread polling", e);
                             }
                         }, 0, 700, TimeUnit.MILLISECONDS);
                        // Wait for detection or termination
                        try{
                            checkLatch.await((long)deauthWait + 5, TimeUnit.SECONDS);
                        }catch(InterruptedException ie){ Thread.currentThread().interrupt(); }
                        try{ checkFuture.cancel(true); }catch(Exception ignored){}
                        try{ checkExec.shutdownNow(); }catch(Exception ignored){}
                    }
                } finally {
                    wpacheckcont = false;
                    try{ if(!counter_thread.isInterrupted()) counter_thread.interrupt(); }catch(Exception ignored){}
                    shell.done();
                    try{
                        if(handshakeFound.get()) Airodump.startClean(is_ap);
                    }catch(Exception ignored){}
                     runInHandler(() -> {
                         Button crack_btn = findViewById(R.id.crack);
                         if(crack_btn!=null){
                             //We are in IsolatedFragment
                             crack_btn.setText(getString(R.string.crack));
                         }

                        if(handshakeFound.get()){
                              progress.setIndeterminate(false);
                         }
                         if(debug) Log.d("HIJACKER/wpa_thread", "wpa_thread finished");
                     });
                }
            };
            wpa_thread = new Thread(wpa_runnable);
            watchdogTask = new WatchdogTask(MainActivity.this);

            //Start background service so the app won't get killed if it goes to the background
            postProgress(getString(R.string.starting_pers_service));
            startService(new Intent(MainActivity.this, PersistenceService.class));

            //Load manufacturer HashMap
            postProgress(getString(R.string.loading_manuf_db));
            manufHashMap = new HashMap<>();
            File db = new File(manufDBFile);
            if(!db.exists()){
                //Database has not been created
                try{
                    //Create the database
                    if(!db.createNewFile()){
                        Log.e("HIJACKER/SetupTask", "Error creating database file");
                    }else{
                        if(debug) Log.d("HIJACKER/SetupTask", "Creating manufacturer database...");
                        postProgress(getString(R.string.building_manuf_db));
                        BufferedReader out = new BufferedReader(new InputStreamReader(getResources().getAssets().open("oui.txt")));
                        FileWriter in = new FileWriter(db);

                        //Load data from oui.txt and write to the new database file
                        String buffer = out.readLine();
                        while(buffer!=null){
                            if(buffer.length()<18 || !buffer.contains("(base 16)")){
                                buffer = out.readLine();
                                continue;
                            }

                            String mac = buffer.substring(0, 6);
                            String manuf = buffer.substring(22);
                            if(manufHashMap.get(mac)==null){
                                //Write to file only if it was added to the HashMap (it's unique)
                                manufHashMap.put(mac, manuf);
                                in.write(mac + ";" + manuf + '\n');
                            }

                            buffer = out.readLine();
                        }
                        in.close();
                        out.close();
                        if(debug) Log.d("HIJACKER/SetupTask", "Manufacturer database built");
                    }
                }catch(IOException e){
                    Log.e("HIJACKER/SetupTask", "Error creating manuf DB", e);
                    manufHashMap = null;
                }
            }else{
                //Load database on the HashMap
                try{
                    //Database format:
                    //01B256;Manufacturer co.\n
                    BufferedReader out = new BufferedReader(new FileReader(db));

                    String buffer = out.readLine();
                    while(buffer!=null){
                        String mac = buffer.substring(0, buffer.indexOf(';'));
                        String manuf = buffer.substring(buffer.indexOf(';')+1);
                        manufHashMap.put(mac, manuf);
                        buffer = out.readLine();
                    }
                }catch(IOException e){
                    Log.e("HIJACKER/SetupTask", "Error reading manuf DB", e);
                    manufHashMap = null;
                }
            }

            //Load navigation titles to HashMap
            navTitlesMap.put(R.id.nav_mylist, getString(R.string.nav_airodump));
            navTitlesMap.put(R.id.nav_mdk4, getString(R.string.nav_mdk4));
            navTitlesMap.put(R.id.nav_reaver, getString(R.string.nav_reaver));
            navTitlesMap.put(R.id.nav_crack, getString(R.string.nav_crack));
            navTitlesMap.put(R.id.nav_custom_actions, getString(R.string.nav_custom_actions));
            navTitlesMap.put(R.id.nav_settings, getString(R.string.nav_settings));

            //Load custom actions and aliases (requires storage access)
            if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED){
                postProgress(getString(R.string.loading_custom_actions));
                CustomAction.load();

                postProgress(getString(R.string.loading_aliases));
                loadAliases();
            }

            //Check for updates
            if(update_on_startup){
                if(internetAvailable()){
                    postProgress(getString(R.string.checking_for_updates));
                    checkForUpdate(false);
                } else {
                    //Spawn new thread to wait for internet connection
                    //This should be changed to a broadcast receiver
                    new Thread(() -> {
                        try{
                            while(!internetAvailable()) {
                                Thread.sleep(1000);
                            }
                            checkForUpdate(false);
                        } catch (InterruptedException ignored) { }
                    }).start();
                }
            }

            //Delete old report, it's not needed if no exception is thrown up to this point (requires storage access)
            postProgress(getString(R.string.deleting_bug_report));
            File report = new File(Environment.getExternalStorageDirectory() + "/report.txt");
            if (report.exists()) report.delete();

            //Show FirstRunDialog
            if (customDialog!=null) {
                FirstRunDialog frDialog = new FirstRunDialog();
                frDialog.show(mFragmentManager, "FirstRunDialog");
                Log.d("HIJACKER/SetupTask", "FirstRunDialog shown; continuing without waiting");
                frDialog._wait();
            }

            Log.d("HIJACKER/SetupTask", "doInBackground reached end - returning true");

            return true;
        }
        protected void onPostExecute(final Boolean success){
            Log.d("HIJACKER/SetupTask", "onPostExecute entered (success=" + success + ")");
            try{
                if(!success){
                    // Initialization incomplete, can't continue!!!
                    Log.e("HIJACKER/SetupTask", "SetupTask reported failure in doInBackground");
                    MainActivity activity = getInstance();
                    if (activity != null) {
                        activity.finish();
                    }
                    return;
                }

                if(loadingDialog!=null){
                    loadingDialog.setText(getString(R.string.starting_hijacker));
                }else{
                    Log.w("HIJACKER/SetupTask", "loadingDialog is null in onPostExecute");
                }

                if(watchdog){
                    watchdogTask.start();
                }

                //Load default fragment (airodump)
                try{
                    Log.d("HIJACKER/SetupTask", "NavHostFragment will show startDestination; skipping manual default fragment transaction");
                }catch(Exception e){
                    Log.e("HIJACKER/SetupTask", "Exception while loading default fragment", e);
                    // Show error dialog if possible
                    postProgress(null, getString(R.string.unknown_error), e.toString());
                }

                try{
                    if(loadingDialog!=null) loadingDialog.dismissAllowingStateLoss();
                }catch(Exception e){
                    Log.e("HIJACKER/SetupTask", "Exception while dismissing loading dialog", e);
                }

                if(customDialog!=null){
                    try{
                        mDrawerLayout.openDrawer(GravityCompat.START);
                    }catch(Exception e){
                        Log.e("HIJACKER/SetupTask", "Exception opening drawer", e);
                    }
                }

                // Ensure NavController is initialized after setup completes so menu actions can navigate reliably.
                ensureNavController();

                 //Start process cleanup/initial state
                 try{
                     runOne(enable_monMode);
                     stop(PROCESS_AIRODUMP);
                     stop(PROCESS_AIREPLAY);
                     stop(PROCESS_MDK_BF);
                     stop(PROCESS_MDK_DOS);
                     stop(PROCESS_AIRCRACK);
                     stop(PROCESS_REAVER);
                     if(airOnStartup) Airodump.startClean();
                }catch(Exception e){
                    Log.e("HIJACKER/SetupTask", "Exception during post-setup start/stop operations", e);
                }
                Log.d("HIJACKER/SetupTask", "onPostExecute completed successfully");
            }catch(Throwable t){
                Log.e("HIJACKER/SetupTask", "Unhandled exception in onPostExecute", t);
                try{ if(loadingDialog!=null) loadingDialog.dismissAllowingStateLoss(); }catch(Exception ignored){}
            }
         }

        // Start the setup: run onPreExecute on main thread, then doInBackground in background,
        // post onPostExecute back on main thread. A watchdog ensures we don't hang forever.
        void start(){
            if(executor!=null) return;
            executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "SetupTaskThread"));
            final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);

            // Ensure onPreExecute runs on main thread before background work starts.
            final Runnable startWork = () -> executor.submit(() -> {
                Boolean result = doInBackground((Void)null);
                finished.set(true);
                mainHandler.post(() -> onPostExecute(result));
                return null;
            });

            if(Looper.myLooper() == Looper.getMainLooper()){
                onPreExecute();
                startWork.run();
            }else{
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                mainHandler.post(() -> { try{ onPreExecute(); } finally { latch.countDown(); } });
                executor.submit(() -> {
                    try{ latch.await(); }catch(InterruptedException ignored){}
                    Boolean result = doInBackground((Void)null);
                    finished.set(true);
                    mainHandler.post(() -> onPostExecute(result));
                    return null;
                });
            }

            // Watchdog: fail-safe if setup hangs for more than 2 minutes
            mainHandler.postDelayed(() -> {
                if(!finished.get()){
                    Log.e("HIJACKER/SetupTask", "SetupTask watchdog triggered: forcing onPostExecute(false)");
                    try{ executor.shutdownNow(); }catch(Exception ignored){}
                    finished.set(true);
                    mainHandler.post(() -> onPostExecute(false));
                }
            }, 120_000);
        }
    }

    void extract(String filename, String out_dir){
         File f = new File(out_dir, filename);
         if(f.exists()) f.delete();          //Delete file in case it's outdated
         try{
             InputStream in = getResources().getAssets().open(filename);
             FileOutputStream out = new FileOutputStream(f);

             byte[] buf = new byte[BUFFER_SIZE];
             int len;
             while((len = in.read(buf))>0){
                 out.write(buf, 0, len);
             }
             in.close();
             out.close();
             runOne("chmod 755 " + out_dir + "/" + filename);
         }catch(IOException e){
             Log.e("HIJACKER/FileProvider", "Exception copying from assets", e);
         }
     }

    // Wrapper to log start and end of asset extraction; useful to trace long/blocked extractions
    void extractWithLog(String filename, String out_dir){
        Log.d("HIJACKER/SetupTask", "extractWithLog: starting " + filename);
        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "extract-" + filename));
        Future<?> fut = exec.submit(() -> extract(filename, out_dir));
        try{
            fut.get(20, TimeUnit.SECONDS);
            Log.d("HIJACKER/SetupTask", "extractWithLog: finished " + filename);
        }catch(java.util.concurrent.TimeoutException te){
            Log.e("HIJACKER/SetupTask", "extractWithLog: timeout while extracting " + filename);
            fut.cancel(true);
        }catch(Exception e){
            Log.e("HIJACKER/SetupTask", "extractWithLog failed for " + filename, e);
        }finally{
            try{ exec.shutdownNow(); }catch(Exception ignored){}
        }
    }

     public static void _startAireplay(final String str){
         try{
            String trimmedPrefix = (prefix==null) ? "" : prefix.trim();

            // Build command using ProcessExecutor
            List<String> envVars = new ArrayList<>();
            if (!trimmedPrefix.isEmpty()) {
                envVars.add(trimmedPrefix);
            }

            String[] command = (aireplay_dir + " -D --ignore-negative-one " + str + " " + iface).split("\\s+");

            if(debug) Log.d("HIJACKER/_startAireplay", "Starting aireplay: " + Arrays.toString(command));

            if (envVars.isEmpty()) {
                ProcessExecutor.executeAsRoot(command);
            } else {
                ProcessExecutor.executeAsRootWithEnv(command, envVars.toArray(new String[0]));
            }

            last_action = System.currentTimeMillis();
         }catch(IOException e){ Log.e("HIJACKER/Exception", "Caught Exception in _startAireplay() start block: " + e); }
         runInHandler(() -> {
             menu.getItem(3).setEnabled(true);       //Enable 'Stop aireplay' button
             refreshState();
             notification();
         });
     }
    public static void startAireplay(String mac){
        //Disconnect all clients from mac
        aireplay_running = AIREPLAY_DEAUTH;
        _startAireplay("--deauth 0 -a " + mac);
    }
    public static void startAireplay(String target, String client){
        //Disconnect client client from ap target
        aireplay_running = AIREPLAY_DEAUTH;
        _startAireplay("--deauth 0 -a " + target + " -c " + client);
    }
    public static void startAireplayWEP(AP ap){
        //Increase IV generation from ap mac to crack a wep network
        aireplay_running = AIREPLAY_WEP;
        _startAireplay("--fakeauth 0 -a " + ap.mac + (ap.isHidden() ? "" : " -e " + ap.getESSID()));
    }

    //There are 2 mdk4 binaries with different names, so the app can easily stop each one separately
    public static void startBeaconFlooding(String str){
        try{
                 // No ABI preflight for packaged mdk4 binaries; use what was installed in setup.
             try{
                // Minimal no-op to avoid empty try block; keeps placeholder semantics for future ABI checks.
                Thread.yield();
            }catch(Exception e){
                if(debug) Log.w("HIJACKER", "Ignored exception during ABI preflight placeholder", e);
            }
             String trimmedPrefix = (prefix==null) ? "" : prefix.trim();
             String prefPart = trimmedPrefix.isEmpty() ? "" : (trimmedPrefix + " ");
             String cmd = "su -c " + prefPart + mdk4bf_dir + " " + iface + " b -m ";
              if(str!=null) cmd += str;
              if(debug) Log.d("HIJACKER/mdk4", cmd);
              last_mdk = cmd;
              // Build fallback command without LD_PRELOAD prefix
             String bareCmd = "su -c " + mdk4bf_dir + " " + iface + " b -m " + (str==null?"":str);
              runToolWithPrefixFallback(cmd, bareCmd, "mdk4bf");
         }catch(Exception e){ Log.e("HIJACKER/startBF", e.toString()); }
         last_action = System.currentTimeMillis();
         MDKFragment.bf = true;

         runInHandler(() -> {
             refreshState();
             notification();
         });
     }
     public static void startAdos(String str) {
         try{
                // No ABI preflight for packaged mdk4dos binary; use what was installed in setup.
            try{
                // Minimal no-op to avoid empty try block; keeps placeholder semantics for future ABI checks.
                Thread.yield();
            }catch(Exception e){
                if(debug) Log.w("HIJACKER", "Ignored exception during ABI preflight placeholder", e);
            }
              String trimmedPrefix2 = (prefix==null) ? "" : prefix.trim();
              String prefPart2 = trimmedPrefix2.isEmpty() ? "" : (trimmedPrefix2 + " ");
              String cmd = "su -c " + prefPart2 + mdk4dos_dir + " " + iface + " a -m";
               cmd += str==null ? "" : " -i " + str;
               if(debug) Log.d("HIJACKER/MDK4", cmd);
               last_mdk = cmd;
               String bareCmd = "su -c " + mdk4dos_dir + " " + iface + " a -m" + (str==null?"":" -i " + str);
               runToolWithPrefixFallback(cmd, bareCmd, "mdk4dos");
           }catch(Exception e){ Log.e("HIJACKER/startAdos", e.toString()); }
          last_action = System.currentTimeMillis();
          MDKFragment.ados = true;

          runInHandler(() -> {
              refreshState();
              notification();
          });
      }

    // Run a tool command that is normally prefixed with LD_PRELOAD; if the prefixed run doesn't start (no PID), retry without the prefix.
    public static Process runToolWithPrefixFallback(final String cmdWithPrefix, final String cmdWithoutPrefix, final String processName) {
         try{
             if(debug) Log.d("HIJACKER/RunTool", "Trying prefixed command: " + cmdWithPrefix);
             Process p = Runtime.getRuntime().exec(cmdWithPrefix);
             // Poll briefly (up to 1.5s) to see if the process appears and stays alive
             final int maxWaitMs = 1500;
             final int intervalMs = 200;
             final java.util.concurrent.ScheduledExecutorService pollExec = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "runToolPoll"));
             final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);
             final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
             final java.util.concurrent.ScheduledFuture<?> sf = pollExec.scheduleWithFixedDelay(() -> {
                 try{
                     ArrayList<Integer> pids = getPIDs(processName);
                     if(pids!=null && !pids.isEmpty()){
                         started.set(true);
                         latch.countDown();
                     }
                 }catch(Exception ignored){}
             }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
             try{
                 boolean ok = latch.await(maxWaitMs, TimeUnit.MILLISECONDS);
                 if(!ok){
                     // timed out without starting
                 }
             }catch(InterruptedException ignored){}
             try{ sf.cancel(true); }catch(Exception ignored){}
             try{ pollExec.shutdownNow(); }catch(Exception ignored){}
             if(!started.get()){
                 Log.e("HIJACKER/RunTool", "Prefixed command did not start process " + processName + " within " + maxWaitMs + "ms, retrying without prefix");
                 if(debug) Log.d("HIJACKER/RunTool", "Trying fallback command: " + cmdWithoutPrefix);
                 try{ p.destroy(); }catch(Exception ignored){}
                 p = Runtime.getRuntime().exec(cmdWithoutPrefix);
                 // No further fallback attempts
              } else {
                 // Process appeared — wait a short while and verify it's still running (didn't crash immediately)
                 try{
                     Thread.sleep(300);
                 }catch(InterruptedException ignored){}
                 ArrayList<Integer> pids2 = getPIDs(processName);
                 if(pids2==null || pids2.isEmpty()){
                     // Process has exited quickly — assume crash when prefixed. Try without prefix.
                     Log.e("HIJACKER/RunTool", "Prefixed process " + processName + " exited quickly; retrying without prefix");
                     if(debug) Log.d("HIJACKER/RunTool", "Trying fallback command: " + cmdWithoutPrefix);
                     try{ p.destroy(); }catch(Exception ignored){}
                     p = Runtime.getRuntime().exec(cmdWithoutPrefix);
                 }
              }
              return p;
         }catch(IOException e){
             Log.e("HIJACKER/RunTool", "IOException running tool: " + e);
             try{ return Runtime.getRuntime().exec(cmdWithoutPrefix);}catch(IOException ex){ Log.e("HIJACKER/RunTool", "Fallback exec failed: " + ex); return null; }
         }
     }

    // Check whether a network interface is already in monitor mode.
    // Tries app-bundled `iw`/`iwconfig` (path + "/bin/iw"), then falls back to system `iw`/`iwconfig` and looks for monitor/type indications.
    public static boolean isInterfaceInMonitor(String ifaceName) {
        if(ifaceName==null || ifaceName.isEmpty()) return false;
        // Normalize ifaceName
        ifaceName = ifaceName.trim();
        String marker1 = "ENDOFIW" + System.currentTimeMillis();
        String marker2 = "ENDOFIWCFG" + System.currentTimeMillis();
        String[] iwCandidates;
        String[] iwconfigCandidates;
        if(path!=null && !path.isEmpty()){
            iwCandidates = new String[]{path + "/bin/iw", "/system/bin/iw", "iw"};
            iwconfigCandidates = new String[]{path + "/bin/iwconfig", "/system/bin/iwconfig", "iwconfig"};
        }else{
            iwCandidates = new String[]{"/system/bin/iw", "iw"};
            iwconfigCandidates = new String[]{"/system/bin/iwconfig", "iwconfig"};
        }
        try{
            for(String iwCmd : iwCandidates){
                try{
                    Shell sh = getFreeShell();
                    // run: <iwCmd> dev iface info 2>&1; echo MARK
                    sh.run(iwCmd + " dev " + ifaceName + " info 2>&1; echo " + marker1);
                    String out = getLastLine(sh.getShell_out(), marker1);
                    sh.done();
                    if(out!=null){
                        String o = out.toLowerCase();
                        if(o.contains("type monitor") || o.contains("type\tmonitor") || o.contains("mode monitor")) return true;
                        // sometimes iw prints 'Interface <iface>' followed by 'type monitor' on another line; check full output via contains
                        if(o.contains("monitor")){
                            // ensure it's not just the word 'monitor' in unrelated context by checking for 'type' or 'mode' nearby
                            if(o.contains("type") || o.contains("mode")) return true;
                        }
                    }
                }catch(Exception ignored){ /* try next candidate */ }
            }
            // Fallback to iwconfig candidates
            for(String iwcfg : iwconfigCandidates){
                try{
                    Shell sh = getFreeShell();
                    sh.run(iwcfg + " " + ifaceName + " 2>&1; echo " + marker2);
                    String out = getLastLine(sh.getShell_out(), marker2);
                    sh.done();
                    if(out!=null){
                        String o = out.toLowerCase();
                        if(o.contains("mode:monitor") || o.contains("mode monitor") || o.contains("monitor")) return true;
                    }
                }catch(Exception ignored){ /* try next */ }
            }
        }catch(Exception e){
            Log.e("HIJACKER/isMon", "Exception while checking interface mode: " + e);
        }
        return false;
    }

    public static ArrayList<Integer> getPIDs(String process_name) {
         if(process_name==null) return null;

         Shell shell = getFreeShell();
         ArrayList<Integer> list = new ArrayList<>();
         shell.run(busybox + " pidof " + process_name + "; echo ENDOFPIDOF");
         BufferedReader out = shell.getShell_out();
         String lastline, buffer = null;
         try{
             // Read until we get at least one non-null line
             while(buffer==null) buffer = out.readLine();
             lastline = buffer;
             // Read until the sentinel line 'ENDOFPIDOF' or EOF
             while(buffer != null && !"ENDOFPIDOF".equals(buffer)){
                 lastline = buffer;
                 buffer = out.readLine();
             }
             // Parse lastline (should contain pid list like "1234 5678") into integers
             if(!lastline.trim().isEmpty() && !"ENDOFPIDOF".equals(lastline)){
                 String[] parts = lastline.trim().split("\\s+");
                 for(String p : parts){
                     try{
                         list.add(Integer.parseInt(p));
                     }catch(NumberFormatException nfe){ /* ignore tokens that aren't numbers */ }
                 }
             }
         }catch(IOException e){
             Log.e("HIJACKER/getPIDs", "Exception while reading pidof", e);
             list = null;
         }
         shell.done();
         return list;
     }
    public static ArrayList<Integer> getPIDs(int pr) {
        switch(pr){
            case PROCESS_AIRODUMP:
                return getPIDs("airodump-ng");
            case PROCESS_AIREPLAY:
                return getPIDs("aireplay-ng");
            case PROCESS_MDK_BF:
                return getPIDs("mdk4bf");
            case PROCESS_MDK_DOS:
                return getPIDs("mdk3dos");
            case PROCESS_AIRCRACK:
                return getPIDs("aircrack-ng");
            case PROCESS_REAVER:
                return getPIDs("reaver");
            case PROCESS_HCXDUMPTOOL:
                return getPIDs("hcxdumptool");
            default:
                Log.e("HIJACKER/getPIDs", "Method called with invalid pr code");
                throw new UnsupportedOperationException("getPIDs() called with invalid pr code");
            }
    }

    public static boolean isProcessRunning(int pr) {
        ArrayList<Integer> pids = getPIDs(pr);
        return pids != null && !pids.isEmpty();
    }

    public static void startHcxdumptool(String outputFile, String channel, String timeout) {
        try {
            if (debug) Log.d("HIJACKER/hcxdumptool", "Starting hcxdumptool");

            // Build command
            String cmdStr = getString(outputFile, channel, timeout);
            if (debug) Log.d("HIJACKER/hcxdumptool", cmdStr);

            Runtime.getRuntime().exec(cmdStr);

        } catch (Exception e) {
            Log.e("HIJACKER/startHcx", e.toString());
        }

        last_action = System.currentTimeMillis();

        runInHandler(() -> {
            refreshState();
            notification();
            MainActivity _inst = getInstance();
            if(_inst != null) _inst.refreshHcxdumpFragmentUI();
        });
    }

    private static String getString(String outputFile, String channel, String timeout) {
        StringBuilder cmd = new StringBuilder("su -c ");
        cmd.append(hcxdumptool_dir);
        cmd.append(" -i ").append(iface);
        cmd.append(" -w ").append(outputFile);

        if (channel != null && !channel.trim().isEmpty() && !channel.equals("0")) {
            cmd.append(" -c ").append(channel);
        }

        cmd.append(" --rds=3");

        if (timeout != null && !timeout.trim().isEmpty()) {
            cmd.append(" & sleep ").append(timeout).append(" && ");
            cmd.append(busybox).append(" kill $(").append(busybox).append(" pidof hcxdumptool)");
        }

        return cmd.toString();
    }

    public static void stop(int pr){
        if(debug) Log.d("HIJACKER/stop", "stop(" + pr + ") called");
        last_action = System.currentTimeMillis();
        switch(pr){
            case PROCESS_AIRODUMP:
                Airodump.stop();
                return;
            case PROCESS_AIREPLAY:
                runInHandler(() -> {
                    if(menu!=null) menu.getItem(3).setEnabled(false);
                });
                progress_int = deauthWait;
                runOne(busybox + " kill $(" + busybox + " pidof aireplay-ng)");
                if(is_ap==null && aireplay_running==AIREPLAY_DEAUTH && Airodump.isRunning()){
                    //Aireplay was just deauthenticating so airodump has locked a channel, no more needed
                    Airodump.startClean();
                }
                AP.currentTargetDeauth.clear();
                aireplay_running = 0;
                break;
            case PROCESS_MDK_BF:
                if(currentFragment==FRAGMENT_MDK){
                    runInHandler(() -> MDKFragment.bf_switch.setChecked(false));
                }

                MDKFragment.bf = false;
                runOne(busybox + " kill $(" + busybox + " pidof mdk3bf)");
                break;
            case PROCESS_MDK_DOS:
                if(currentFragment==FRAGMENT_MDK){
                    runInHandler(() -> MDKFragment.ados_switch.setChecked(false));
                }

                MDKFragment.ados = false;
                runOne(busybox + " kill $(" + busybox + " pidof mdk3dos)");
            case PROCESS_AIRCRACK:
                CrackFragment.stopCracking();
                runOne(busybox + " kill $(" + busybox + " pidof aircrack-ng)");
                break;
            case PROCESS_REAVER:
                ReaverFragment.stopReaver();
                runOne(busybox + " kill $(" + busybox + " pidof reaver)");
                break;
            case PROCESS_HCXDUMPTOOL:
                runOne(busybox + " kill $(" + busybox + " pidof hcxdumptool)");
                runInHandler(() -> { MainActivity _inst = getInstance(); if(_inst != null) _inst.refreshHcxdumpFragmentUI(); });
                break;
            default:
                runOne(busybox + " kill " + pr);
                break;
        }
        runInHandler(() -> {
            refreshState();
            notification();
        });
    }
    public static void stopWPA(){
        wpacheckcont = false;
        if(wpa_thread!=null) wpa_thread.interrupt();
    }

    public static final Handler handler = new Handler(Looper.getMainLooper());
    public static void runInHandler(Runnable runnable){
        handler.post(runnable);
    }

    // UI-safe helper methods to avoid exposing Activity views as public static fields.
    // Callers should use these to update UI elements from other classes.
    public static void setProgressIndeterminate(final boolean ind){
        final MainActivity inst = getInstance();
        if(inst==null) return;
        inst.runOnUiThread(() -> {
            if(inst.progress!=null) inst.progress.setIndeterminate(ind);
        });
    }

    public static void setProgressValue(final int value){
        final MainActivity inst = getInstance();
        if(inst==null) return;
        inst.runOnUiThread(() -> {
            if(inst.progress!=null) inst.progress.setProgress(value);
        });
    }

    public static void setProgressMax(final int max) {
        final MainActivity inst = getInstance();
        if(inst==null) return;
        inst.runOnUiThread(() -> {
            if(inst.progress!=null) inst.progress.setMax(max);
        });
    }

    public static void updateCounts() {
        final MainActivity inst = getInstance();
        if(inst==null) return;
        inst.runOnUiThread(() -> {
            if(inst.ap_count!=null) inst.ap_count.setText(String.format(Locale.getDefault(), "%d", is_ap==null ? Tile.i : 1));
            if(inst.st_count!=null) inst.st_count.setText(String.format(Locale.getDefault(), "%d", Tile.tiles.size() - Tile.i));
        });
    }

    /**
     * Migrate SharedPreferences to handle version changes
     * This allows smooth upgrades when preference structure changes
     */
    static void migratePreferences() {
        final String PREF_VERSION_KEY = "pref_version";
        final int CURRENT_PREF_VERSION = 2; // Update this when adding new migrations

        int currentVersion = pref.getInt(PREF_VERSION_KEY, 0);

        if (currentVersion >= CURRENT_PREF_VERSION) {
            // Already up to date
            if (debug) Log.d("HIJACKER/Migrate", "Preferences already at version " + CURRENT_PREF_VERSION);
            return;
        }

        Log.i("HIJACKER/Migrate", "Migrating preferences from version " + currentVersion + " to " + CURRENT_PREF_VERSION);

        // Migration from version 0 to 1
        if (currentVersion < 1) {
            Log.d("HIJACKER/Migrate", "Applying migration v0 -> v1");

            // Example: Rename old preference keys
            if (pref.contains("old_key_name")) {
                String value = pref.getString("old_key_name", "");
                pref_edit.putString("new_key_name", value);
                pref_edit.remove("old_key_name");
            }

            // Example: Convert boolean to string preference
            if (pref.contains("always_cap")) {
                try {
                    boolean value = pref.getBoolean("always_cap", false);
                    // Keep as boolean, but ensure it's the right type
                    pref_edit.putBoolean("always_cap", value);
                } catch (ClassCastException e) {
                    // Was stored as wrong type, reset to default
                    pref_edit.putBoolean("always_cap", false);
                }
            }

            // Example: Set new default values for new preferences
            if (!pref.contains("airodump_update_interval")) {
                pref_edit.putString("airodump_update_interval", "1000");
            }

            currentVersion = 1;
        }

        // Migration from version 1 to 2
        if (currentVersion < 2) {
            Log.d("HIJACKER/Migrate", "Applying migration v1 -> v2");

            // Example: Clean up prefix preference if it was set to a deprecated value
            if (pref.contains("prefix")) {
                String prefix = pref.getString("prefix", "");
                // If prefix contains old library path, remove it
                if (prefix.contains("libfakeioctl.so") && prefix.contains("LD_PRELOAD=")) {
                    Log.d("HIJACKER/Migrate", "Removing deprecated LD_PRELOAD prefix");
                    pref_edit.remove("prefix");
                }
            }

            // Example: Ensure band preference is valid
            if (pref.contains("band")) {
                try {
                    int band = Integer.parseInt(pref.getString("band", "3"));
                    if (band < 1 || band > 3) {
                        Log.d("HIJACKER/Migrate", "Invalid band value, resetting to BAND_BOTH");
                        pref_edit.putString("band", "3");
                    }
                } catch (NumberFormatException e) {
                    pref_edit.putString("band", "3");
                }
            }

            // Example: Add new preferences with defaults
            if (!pref.contains("show_client_count")) {
                pref_edit.putBoolean("show_client_count", true);
            }

            currentVersion = 2;
        }

        // Save the current version
        pref_edit.putInt(PREF_VERSION_KEY, CURRENT_PREF_VERSION);
        pref_edit.apply();

        Log.i("HIJACKER/Migrate", "Preferences migration complete. Now at version " + CURRENT_PREF_VERSION);
    }

    static void loadPreferences() {
        //Load Preferences
        Log.d("HIJACKER/load", "Loading preferences...");

        iface = pref.getString("iface", iface);
        // Do NOT set `prefix` here — SetupTask will decide between user-configured value
        // (pref.contains("prefix") == true) and auto-selected LD_PRELOAD based on chipset.
        deauthWait = Integer.parseInt(pref.getString("deauthWait", Integer.toString(deauthWait)));
        chroot_dir = pref.getString("chroot_dir", chroot_dir);
        monstart = pref.getBoolean("monstart", monstart);
        enable_monMode = pref.getString("enable_monMode", enable_monMode);
        disable_monMode = pref.getString("disable_monMode", disable_monMode);
        enable_on_airodump = pref.getBoolean("enable_on_airodump", enable_on_airodump);
        show_notif = pref.getBoolean("show_notif", show_notif);
        show_details = pref.getBoolean("show_details", show_details);
        airOnStartup = pref.getBoolean("airOnStartup", airOnStartup);
        debug = pref.getBoolean("debug", debug);
        watchdog = pref.getBoolean("watchdog", watchdog);
        target_deauth = pref.getBoolean("target_deauth", target_deauth);
        try{
            always_cap = pref.getBoolean("always_cap", always_cap);
        }catch(ClassCastException e){
            pref_edit.putBoolean("always_cap", false);
            pref_edit.apply();
        }
        custom_chroot_cmd = pref.getString("custom_chroot_cmd", custom_chroot_cmd);
        cont_on_fail = pref.getBoolean("cont_on_fail", cont_on_fail);
        update_on_startup = pref.getBoolean("update_on_startup", update_on_startup);
        band = Integer.parseInt(pref.getString("band", Integer.toString(band)));
        show_client_count = pref.getBoolean("show_client_count", show_client_count);
        airodump_update_interval = Integer.parseInt(pref.getString("airodump_update_interval", "1000"));

        // Use UI-safe helpers
        setProgressMax(deauthWait);
        setProgressValue(deauthWait);
    }
    static void loadAliases() {
        aliases_file = new File(data_path + "/aliases.txt");
        try{
            if(!aliases_file.exists()) {
                aliases_file.createNewFile();
            }else{
                if(debug) Log.d("HIJACKER/loadAliases", "Reading aliases file...");
                try{
                    BufferedReader aliases_out = new BufferedReader(new FileReader(aliases_file));
                    String buffer = aliases_out.readLine();
                    while(buffer!=null){
                        //Line format: 00:11:22:33:44:55 Alias
                        if(buffer.charAt(17)==' ' && buffer.length()>18){
                            String mac = buffer.substring(0, 17);
                            String alias = buffer.substring(18);
                            aliases.put(mac, alias);
                        }else{
                            Log.e("HIJACKER/loadAliases", "Aliases file format error: " + buffer);
                        }
                        buffer = aliases_out.readLine();
                    }
                    aliases_out.close();
                }catch(IOException e){
                    Log.e("HIJACKER/loadAliases1", e.toString());
                }
            }
            aliases_in = new FileWriter(aliases_file, true);
        }catch(Exception e){
            Log.e("HIJACKER/loadAliases2", e.toString());
            aliases_in = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item){
        // Handle toolbar menu items (not navigation destinations)
        int id = item.getItemId();
        if(id == android.R.id.home){
            mDrawerLayout.openDrawer(GravityCompat.START);
            return true;
        }else if(id == R.id.reset){
             boolean flag = Airodump.isRunning();
             if(flag) stop(PROCESS_AIRODUMP);
             Tile.clear();
             Tile.onCountsChanged();
             if(flag) Airodump.start();
             return true;
         }else if(id == R.id.stop_run){
             if(Airodump.isRunning()) stop(PROCESS_AIRODUMP);
             else Airodump.start();
             return true;
         }else if(id == R.id.stop_aireplay){
             stop(PROCESS_AIREPLAY);
             return true;
         }else if(id == R.id.filter){
             new FiltersDialog().show(mFragmentManager, "FiltersDialog");
             return true;
         }else if(id == R.id.settings){
             // Navigate to settings via NavController when available
             try {
                 if (navController != null) {
                     navController.navigate(R.id.nav_settings);
                 } else {
                     if (currentFragment != FRAGMENT_SETTINGS) {
                         Log.w("HIJACKER/Navigation", "NavController not available: cannot navigate to SettingsFragment");
                     }
                 }
             } catch (Exception e) {
                 if (currentFragment != FRAGMENT_SETTINGS) {
                     Log.w("HIJACKER/Navigation", "Exception handling navigation to settings", e);
                 }
             }
             return true;
         }else if(id == R.id.export){
             new ExportDialog().show(mFragmentManager, "ExportDialog");
             return true;
         }else if(id == R.id.copy_airodump){
             if(last_airodump==null){
                 Toast.makeText(this, getString(R.string.no_last_command_available), Toast.LENGTH_SHORT).show();
             }else{
                 copy(last_airodump, rootView);
             }
             return true;
         }

         return super.onOptionsItemSelected(item);
     }

     @Override
     public boolean onSupportNavigateUp() {
         ensureNavController();
         try{
             if(navController!=null){
                 return NavigationUI.navigateUp(navController, mDrawerLayout) || super.onSupportNavigateUp();
             }
         }catch(Exception e){
            Log.w("HIJACKER/Navigation", "onSupportNavigateUp failed", e);
         }
         return super.onSupportNavigateUp();
    }

    @Override
    protected void onResume(){
        super.onResume();
        notif_on = false;
        background = false;
        if(mNotificationManager!=null) mNotificationManager.cancelAll();

        // Prompt user for All files access on Android 11+ (non-blocking) if we don't have it.
        if (!askedAllFilesAccessDialogShown && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            askedAllFilesAccessDialogShown = true;
            if (!Environment.isExternalStorageManager()) {
                // Show explanation dialog and offer to open Settings to grant MANAGE_EXTERNAL_STORAGE
                CustomDialog d = new CustomDialog();
                d.setTitle(getString(R.string.all_files_access_title));
                d.setMessage(getString(R.string.all_files_access_message));
                d.setPositiveButton(getString(R.string.grant_all_files_access), () -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        // Fallback to generic All files access settings
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivity(intent);
                    }
                });
                d.setNegativeButton(getString(R.string.continue_without_access), null);
                d.show(mFragmentManager, "AllFilesAccessDialog");
            }
        }
    }
    @Override
    protected void onPause(){
        super.onPause();
        background = true;
    }
    @Override
    protected void onStop(){
        super.onStop();
        if(show_notif){
            notif_on = true;
            notification();
        }
    }
    @Override
    protected void onDestroy(){
        notif_on = false;
        if(mNotificationManager!=null) mNotificationManager.cancelAll();
        CustomAction.save();
         if(watchdogTask!=null) watchdogTask.requestStop();
         try{
             stop(PROCESS_AIRODUMP);
             stop(PROCESS_AIREPLAY);
             stop(PROCESS_MDK_BF);
             stop(PROCESS_MDK_DOS);
             stop(PROCESS_AIRCRACK);
             stop(PROCESS_REAVER);
             runOne(disable_monMode);
         }catch(Exception e){
             Log.e("HIJACKER/onDestroy", "Exception while stopping processes", e);
         }
         RootFile.finish();
         Shell.exitAll();
         stopService(new Intent(this, PersistenceService.class));

         // Clear the WeakReference to prevent memory leaks
         instanceRef = null;

         super.onDestroy();
         // Removed System.exit(0) - let Android handle lifecycle naturally
     }
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MainActivity.menu = menu;
        getMenuInflater().inflate(R.menu.toolbar, menu);
        // Set the Run/Start icon according to the actual running state of airodump.
        // Menu can be recreated (for example after dialogs); prefer runtime state over the preference flag.
        try {
            if (Airodump.isRunning()) {
                menu.getItem(1).setIcon(R.drawable.stop_drawable);
                menu.getItem(1).setTitle(R.string.stop);
            } else if ( airOnStartup) {
                // If configured to run on startup but not currently running, show stop to indicate it will run
                menu.getItem(1).setIcon(R.drawable.stop_drawable);
                menu.getItem(1).setTitle(R.string.stop);
            } else {
                menu.getItem(1).setIcon(R.drawable.start_drawable);
                menu.getItem(1).setTitle(R.string.start);
            }
        } catch (Exception e) {
            // Defensive: if menu indexing changes, just ignore and leave menu as inflated.
            Log.w("HIJACKER/OptMenu", "Failed to set run icon based on state", e);
        }
         menu.getItem(3).setEnabled(false);
         return true;
     }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==0){
            //The one and only request this app sends
            synchronized(this){
                this.notify();
            }
        }else if(requestCode==1){
            // Notification permission request
            boolean granted = false;
            for(int result : grantResults){
                if(result == PackageManager.PERMISSION_GRANTED){
                    granted = true;
                    break;
                }
            }
            if(granted){
                // Permission granted, you can show a message or take action if needed
                Log.i("HIJACKER/Permissions", "Notification permission granted");
            }else{
                // Permission denied, handle accordingly
                Log.i("HIJACKER/Permissions", "Notification permission denied");
            }
        }
    }
    @SuppressLint("MissingSuperCall")
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        //No call for super(), avoid IllegalStateException on FragmentManagerImpl.checkStateLoss.
    }

    class MyListAdapter extends ArrayAdapter<Tile> {
        MyListAdapter() {
            super(MainActivity.this, R.layout.listitem);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            // get a view to work with
            View itemview = convertView;
            if(itemview==null) {
                itemview = getLayoutInflater().inflate(R.layout.listitem, parent, false);
            }

            // find the item to work with
            Tile current = Tile.tiles.get(position);

            TextView upperLeft = itemview.findViewById(R.id.upperLeft);
            upperLeft.setText(current.device.upperLeft);
            upperLeft.setTextColor(ContextCompat.getColor(getContext(), current.device.isMarked ? R.color.colorAccent : android.R.color.white));

            TextView lowerLeft = itemview.findViewById(R.id.lowerLeft);
            lowerLeft.setText(current.device.lowerLeft);

            TextView lowerRight = itemview.findViewById(R.id.lowerRight);
            lowerRight.setText(current.device.lowerRight);

            TextView upperRight = itemview.findViewById(R.id.upperRight);
            upperRight.setText(current.device.upperRight);

            //Image and count views
            ImageView iv = itemview.findViewById(R.id.iv);
            TextView icon_count_view = itemview.findViewById(R.id.icon_count_view);
            if (current.device instanceof AP) {
                if (((AP)current.device).isHidden) iv.setImageResource(R.drawable.ap_hidden);
                else iv.setImageResource(R.drawable.ap2);

                if (show_client_count) {
                    icon_count_view.setText(String.format(Locale.getDefault(), "%d", ((AP) (current.device)).clients.size()));
                    icon_count_view.setVisibility(View.VISIBLE);
                } else {
                    icon_count_view.setVisibility(View.GONE);
                }
            } else {
                iv.setImageResource(R.drawable.st2);

                icon_count_view.setVisibility(View.GONE);
            }

            return itemview;
        }

        @Override
        public int getCount(){
            return Tile.tiles.size();
        }
    }
    class CustomActionAdapter extends ArrayAdapter<CustomAction>{
        CustomActionAdapter(){
            super(MainActivity.this, R.layout.listitem);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent){
            // get a view to work with
            View itemview = convertView;
            if(itemview==null){
                itemview = getLayoutInflater().inflate(R.layout.listitem, parent, false);
            }

            // find the item to work with
            CustomAction currentItem = CustomAction.cmds.get(position);

            TextView upperLeft = itemview.findViewById(R.id.upperLeft);
            upperLeft.setText(currentItem.getTitle());

            TextView lowerLeft = itemview.findViewById(R.id.lowerLeft);
            lowerLeft.setText(currentItem.getStartCmd());

            TextView lowerRight = itemview.findViewById(R.id.lowerRight);
            lowerRight.setText("");

            TextView upperRight = itemview.findViewById(R.id.upperRight);
            upperRight.setText("");

            TextView icon_count_view = itemview.findViewById(R.id.icon_count_view);
            icon_count_view.setVisibility(View.GONE);

            //Image
            ImageView iv = itemview.findViewById(R.id.iv);
            if(currentItem.getType()==TYPE_ST){
                iv.setImageResource(R.drawable.st2);
            }else{
                iv.setImageResource(R.drawable.ap2);
            }

            return itemview;
        }

        @Override
        public int getCount(){
            return CustomAction.cmds.size();
        }
    }
    class FileExplorerAdapter extends ArrayAdapter<RootFile>{
        FileExplorerAdapter(){
            super(MainActivity.this, R.layout.explorer_item);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent){
            // get a view to work with
            View itemview = convertView;
            if(itemview==null){
                itemview = getLayoutInflater().inflate(R.layout.explorer_item, parent, false);
            }

            // find the item to work with
            RootFile currentItem = FileExplorerDialog.list.get(position);

            TextView firstText = itemview.findViewById(R.id.explorer_item_tv);
            firstText.setText(currentItem.getName());

            //Image
            if(currentItem.isFile()){
                firstText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.file, 0, 0, 0);
            }else{
                firstText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.folder, 0, 0, 0);
            }

            return itemview;
        }

        @Override
        public int getCount(){
            return FileExplorerDialog.list.size();
        }
    }
    public void onAPStats(View v){ new StatsDialog().show(mFragmentManager, "StatsDialog"); }
    public void onCrack(View v){
        //Clicked crack with isolated ap
        if(wpa_thread.isAlive()){
            //Clicked stop
            stopWPA();
            Airodump.startClean(is_ap);
        }else{
            //Clicked crack
            is_ap.crack();
            ((TextView)v).setText(R.string.stop);
        }
    }
    public void onDisconnect(View v){
        //Clicked disconnect all with isolated ap
        stop(PROCESS_AIREPLAY);
        startAireplay(is_ap.mac);
        Toast.makeText(this, R.string.disconnect_started, Toast.LENGTH_SHORT).show();
    }
    public void onDos(View v){
        //Clicked dos with isolated ap
        if(MDKFragment.ados){
            ((TextView)v).setText(R.string.dos);
            stop(PROCESS_MDK_DOS);
            MDKFragment.ados = false;
        }else{
            ((TextView)v).setText(R.string.stop);
            startAdos(is_ap.mac);
        }
    }
    public void onCopy(View v){
        copy(((TextView)v).getText().toString(), v);
    }
    static void copy(String str, View view){
        // Don't rely on the static clipboard field (may not be initialized yet).
        Context ctx = null;
        if(view!=null) ctx = view.getContext();
        if(ctx==null) ctx = appContext;

        if(ctx!=null){
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null){
                cm.setPrimaryClip(ClipData.newPlainText("label", str));
                // cache for later use
                clipboard = cm;
                if(view!=null) Toast.makeText(ctx, ctx.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
            }else{
                Log.e("HIJACKER/Copy", "ClipboardManager not available");
                if(view!=null) Toast.makeText(ctx, ctx.getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
            }
        }else{
            Log.e("HIJACKER/Copy", "No context available to access clipboard");
        }
    }
    static void notification(){
        final MainActivity inst = getInstance();
        if(inst==null){
            if (mNotificationManager != null) mNotificationManager.cancel(0);
            return;
        }
        if(notif_on && show_notif && inst.notif!=null){
            if(show_details){
                String str;
                if(is_ap==null) str = "APs: " + Tile.i + " | STs: " + (Tile.tiles.size() - Tile.i);
                else str = is_ap.getESSID() + " | STs: " + (Tile.tiles.size() - Tile.i);

                if(aireplay_running==AIREPLAY_DEAUTH) str += " | Aireplay deauthenticating...";
                else if(aireplay_running==AIREPLAY_WEP) str += " | Aireplay replaying for wep...";
                if(wpa_thread!=null){
                    if(wpa_thread.isAlive()) str += " | WPA cracking...";
                }
                if(MDKFragment.bf) str += " | MDK3 Beacon Flooding...";
                if(MDKFragment.ados) str += " | MDK3 Authentication DoS...";
                if(ReaverFragment.isRunning()) str += " | Reaver running...";
                if(CrackFragment.isRunning()) str += " | Cracking .cap file...";
                if(CustomActionFragment.isRunning()) str += " | Running action " + CustomActionFragment.selectedAction.getTitle() + "...";

                inst.notif.setContentText(str);
            }else inst.notif.setContentText(null);
            if (mNotificationManager != null) mNotificationManager.notify(0, inst.notif.build());
        }else{
            if (mNotificationManager != null) mNotificationManager.cancel(0);
        }
    }
    static void isolate(String mac) {
        is_ap = getAPByMac(mac);
        if(is_ap!=null){
            IsolatedFragment.exit_on = mFragmentManager.getBackStackEntryCount();
            try{
                // Try to navigate using the stored navController instance
                final MainActivity inst = getInstance();
                if(inst!=null){
                    inst.runOnUiThread(() -> {
                        try{
                            inst.ensureNavController();
                            if(inst.getNavController()!=null){
                                inst.getNavController().navigate(R.id.nav_isolated);
                            }else{
                                Log.w("HIJACKER/Navigation", "NavController not available: cannot navigate to IsolatedFragment");
                            }
                        }catch(Exception e){
                            Log.w("HIJACKER/Navigation", "Failed to navigate to crack", e);
                        }
                    });
                }
            }catch(Exception ignored){}
        }
        Tile.filter();
        if(debug){
            if(is_ap==null) Log.d("HIJACKER/Main", "No AP isolated");
            else Log.d("HIJACKER/Main", "AP with MAC " + mac + " isolated");
        }
    }
    static void refreshState() {
        //refresh overflow icon to show what is running
        final int state = (Airodump.isRunning() ? 1 : 0)
                + (aireplay_running!=0 ? 2 : 0)
                + ((MDKFragment.bf || MDKFragment.ados) ? 4 : 0);
        final MainActivity inst = getInstance();
        if(inst==null) return;
        inst.runOnUiThread(() -> {
            if(inst.toolbar!=null) inst.toolbar.setOverflowIcon(overflow[state]);
            if(!(ReaverFragment.isRunning() || CrackFragment.isRunning() || (wpa_thread!=null && wpa_thread.isAlive()))){
                if(inst.progress!=null){
                    inst.progress.setIndeterminate(false);
                    inst.progress.setProgress(deauthWait);
                }
            }
        });
    }

    // Public helper to synchronize the Run/Start menu icon with the real airodump state.
    // Call this after UI events where the menu may have been recreated or when returning from dialogs.
    public static void updateRunMenuIcon() {
        final MainActivity inst = getInstance();
        if (inst == null) return;
        inst.runOnUiThread(() -> {
            try{
                if(menu == null) return;
                MenuItem mi = menu.findItem(R.id.stop_run);
                if(mi == null) return;
                if(Airodump.isRunning() || airOnStartup){
                    mi.setIcon(R.drawable.stop_drawable);
                    mi.setTitle(R.string.stop);
                } else {
                    mi.setIcon(R.drawable.start_drawable);
                    mi.setTitle(R.string.start);
                }
            }catch(Exception e){
                Log.w("HIJACKER/OptMenu", "Failed updating run menu icon", e);
            }
        });
    }
    void refreshDrawer(){
        navigationView.getMenu().findItem(currentFragment).setChecked(true);
        actionBar.setTitle(navTitlesMap.get(currentFragment));
    }

    // Dismiss any shown DialogFragments to ensure the main fragment is visible
    void dismissAllDialogs(){
        try{
            for(androidx.fragment.app.Fragment f : mFragmentManager.getFragments()){
                if(f instanceof androidx.fragment.app.DialogFragment){
                    ((androidx.fragment.app.DialogFragment)f).dismissAllowingStateLoss();
                }
            }
        }catch(Exception ignored){}
    }

    static String getManuf(String mac){
        mac = trimMac(mac);
        if(manufHashMap==null) return "Unknown Manufacturer";
        String manuf = manufHashMap.get(mac);
        if(manuf==null) return "Unknown Manufacturer";
        else return manuf;
    }
    static String getLastLine(BufferedReader out, String end){
        //Returns the last line printed in out BEFORE end. If no other line is present, end is returned.
        String lastline = null;
        try{
            String buffer;
            while((buffer = out.readLine()) != null){
                if(end != null && end.equals(buffer)) break;
                lastline = buffer;
            }
        }catch(IOException e){
            Log.e("HIJACKER/Exception", "Exception in getLastLine: " + e);
        }

        // If no lines were read before the sentinel, return the sentinel to match previous behavior.
        return lastline == null ? end : lastline;
    }
   static String getLastSeen(long lastseen){
        String str = "";
        long diff = System.currentTimeMillis() - lastseen;
        if(diff < 1000) return "Just now";
        diff = diff/1000; //diff is now seconds
        if(diff/60>0){
            //minutes = diff/60
            str += diff/60 + " minute" + (diff/60 > 1 ? "s " : " ");
            diff = diff%60;
        }
        if(diff > 0){
            str += diff + " second" + (diff > 1 ? "s " : " ");
        }
        str += "ago";
        return str;
    }
   static String getFixed(String text, int size){
        /*Returns a string of fixed length (size) that contains spaces followed by a text
           <--  size  -->
          |     ...  text|
        */
        if(text==null) return null;
        if(text.length() > size){
            text = text.substring(0, size);
        }
        StringBuilder str = new StringBuilder();
        for(int i=0;i < size-text.length();i++){
            str.append(" ");
        }
        return str + text;
    }
    static int checkChroot(){
        boolean bin = false, dir;

        Shell shell = getFreeShell();
        shell.run("echo $PATH; echo ENDOFPATH");
        String path = getLastLine(shell.getShell_out(), "ENDOFPATH");
        shell.done();
        String[] paths = path.split(":");
        for(String temp : paths){
            if(new RootFile(temp + "/bootkali_init").exists()){
                bin = true;
                bootkali_init_bin = temp + "/bootkali_init";
                break;
            }
        }
        if(!bin) {
            if(new RootFile(NETHUNTER_BOOTKALI_BASH).exists()) {
                bin = true;
                bootkali_init_bin = NETHUNTER_BOOTKALI_BASH;
            }
        }

        dir = new RootFile(chroot_dir).exists() && new RootFile(chroot_dir + "/bin/bash").exists();

        if(bin && dir) return CHROOT_FOUND;
        else if(!bin && !dir) return CHROOT_BOTH_MISSING;
        else if(dir) return CHROOT_BIN_MISSING;
        else return CHROOT_DIR_MISSING;
    }
    void checkForUpdate(final boolean showMessages) {
        //Can be called from any thread, blocks until the job is finished
        if(showMessages){
            runInHandler(() -> progress.setIndeterminate(true));
        }

        try{
            HttpsURLConnection connection = (HttpsURLConnection) (new URL(RELEASES_LINK).openConnection());
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            JsonReader reader = new JsonReader(new InputStreamReader(connection.getInputStream()));
            reader.beginArray();
            if(!reader.hasNext()) {
                //No releases
                Log.e("HIJACKER/UpdateCheck", "No releases found");
                throw new Exception();
            }

            String latestName = null, latestLink = null, latestBody = null;

            reader.beginObject();
            //Run through all the names in the release array
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "tag_name":
                        latestName = reader.nextString();
                        break;
                    case "body":
                        latestBody = reader.nextString();
                        if (latestBody.isEmpty())
                            latestBody = null;
                        break;
                    case "assets":
                        //assets is an array
                        reader.beginArray();
                        reader.beginObject();
                        //Run through all the names in the 'assets' array
                        while (reader.hasNext()) {
                            field = reader.nextName();
                            if (field.equals("browser_download_url")) {
                                latestLink = reader.nextString();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                        reader.endArray();
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.close();

            if (!versionName.equals(latestName) && latestLink!=null) {
                String text = getString(R.string.update_text) + "\n\n";
                text += getString(R.string.latest_version) + " " + latestName + "\n";
                text += getString(R.string.current_version) + " " + versionName + "\n";
                if (latestBody!=null) {
                    text += "\nExtra Information:\n" + latestBody;
                }
                final String link = latestLink;

                final CustomDialog customDialog = new CustomDialog();
                customDialog.setTitle(getString(R.string.update_title));
                customDialog.setMessage(text);
                customDialog.setPositiveButton(getString(R.string.download), () -> {
                    String filename = link.substring(link.lastIndexOf('/') + 1);

                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(link));
                    request.setTitle(filename);
                    // allowScanningByMediaScanner is deprecated; call it only on older Android versions to retain behavior
                    allowScanningByMediaScannerIfNeeded(request);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

                    DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if(manager!=null) manager.enqueue(request);
                });
                customDialog.setNeutralButton(getString(R.string.cancel), null);

                runInHandler(() -> customDialog.show(mFragmentManager, "CustomDialog for update"));
            } else {
                if (showMessages) Snackbar.make(rootView, getString(R.string.already_on_latest), Snackbar.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("HIJACKER/update", e.toString());
            if(showMessages) Snackbar.make(rootView, getString(R.string.unknown_error), Snackbar.LENGTH_SHORT).show();
        } finally {
            if (showMessages) runInHandler(() -> progress.setIndeterminate(false));
        }
    }
    boolean internetAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connectivityManager==null) return false;
        // Use modern APIs on API 23+; fall back to older method on older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) return false;
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            // Deprecated on newer APIs but necessary for older devices (minSdk 21).
            @SuppressWarnings("deprecation")
            Network[] networks = connectivityManager.getAllNetworks();
            for (Network n : networks) {
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(n);
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true;
            }
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static void allowScanningByMediaScannerIfNeeded(DownloadManager.Request request) {
        // allowScanningByMediaScanner() is deprecated on newer SDKs. Only call it on older SDKs
        // where the behavior (making the downloaded file visible to MediaStore) was done via this call.
        if (request == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ){
            request.allowScanningByMediaScanner();
        }
    }

    static String findFirmwarePath(Shell shell) {
        // Blocking function, don't run on main thread. Returns the first matching firmware path or null.
        boolean createdShell = false;
        if (shell==null) {
            createdShell = true;
            shell = getFreeShell();
        }

        String[] dirs = {"/system", "/vendor", "/system/etc"};
        String[] fw_names = {"fw_bcmdhd.bin", "bcmdhd_sta.bin"};

        String firmware = null;
        for (int i=0;i<dirs.length && firmware==null;i++) {
            for (String fw_name : fw_names) {
                shell.run(busybox + " find " + dirs[i] + " -type f -name \"" + fw_name + "\"; echo ENDOFFIND");
                BufferedReader out = shell.getShell_out();
                try {
                    String result = out.readLine();
                    while (result!=null) {
                        if (result.equals("ENDOFFIND")) break;
                        // Skip obvious backup paths
                        if (!result.contains("/bac/") && !result.contains("backup")) {
                            firmware = result;
                            break;
                        }
                        result = out.readLine();
                    }
                } catch(IOException e) {
                    Log.e("HIJACKER/FFPATH", "Exception while searching for firmware", e);
                }
                if (firmware!=null) break;
            }
        }

        if (createdShell) {
            shell.done();
        }
        return firmware;
    }

    static boolean isArchValid() {
        // arch is set earlier from System.getProperty("os.arch").
        if (arch==null) return false;
        return arch.matches("(.*)arm(.*)") || arch.matches("aarch64");
    }

    static {
        System.loadLibrary("native-lib");
    }

    static boolean createReport(File report, String filesPath, String stackTrace){
        // Create a simple text report containing app/device info, optional stack trace, a directory listing
        // and a short logcat dump. Returns true on success.
        if(report == null) return false;
        try (PrintWriter pw = new PrintWriter(new FileWriter(report))){
            pw.println("Hijacker report - " + new Date());
            pw.println("Version: " + (versionName == null ? "unknown" : versionName) + " (" + versionCode + ")");
            pw.println("Device: " + (deviceModel == null ? "unknown" : deviceModel));
            pw.println("Arch: " + (arch == null ? "unknown" : arch));
            pw.println("Busybox: " + (busybox == null ? "unknown" : busybox));
            pw.println();

            if(stackTrace != null){
                pw.println("---- Stack trace ----");
                pw.println(stackTrace);
                pw.println();
            }

            if(filesPath != null){
                pw.println("---- Files in: " + filesPath + " ----");
                try{
                    File dir = new File(filesPath);
                    if(dir.exists() && dir.isDirectory()){
                        String[] list = dir.list();
                        if(list!=null){
                            for(String f : list){
                                pw.println(f);
                            }
                        }else{
                            pw.println("(empty)");
                        }
                    }else{
                        pw.println("(path not found)");
                    }
                }catch(Exception e){
                    pw.println("(error listing files: " + e + ")");
                }
                pw.println();
            }

            pw.println("---- Recent logcat (truncated) ----");
            try{
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time"});
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                int lines = 0;
                // limit the amount of logcat written to keep report reasonable
                while((line = br.readLine()) != null && lines < 1000){
                    pw.println(line);
                    lines++;
                }
                br.close();
                p.destroy();
            }catch(Exception e){
                pw.println("(failed to collect logcat: " + e + ")");
            }

            pw.flush();
            return true;
        }catch(IOException e){
            Log.e("HIJACKER/SetupTask", "Exception creating report", e);
            return false;
        }
    }

    // helper method to call HcxdumptoolFragment.updateButtonState() on instances safely
    void refreshHcxdumpFragmentUI() {
        try {
            for (androidx.fragment.app.Fragment f : mFragmentManager.getFragments()) {
                if (f instanceof HcxdumptoolFragment) {
                    ((HcxdumptoolFragment) f).updateButtonState();
                }
            }
        } catch (Exception e) {
            Log.e("HIJACKER/RefreshUI", "Error refreshing HcxdumptoolFragment UI", e);
        }
    }
}
