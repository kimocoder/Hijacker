package com.hijacker;

/*
    Copyright (C) 2019  Christos Kyriakopoulos
    Copyright (C) 2024  Christian <kimocoder> Bremvaag

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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import android.view.View;

import static com.hijacker.MainActivity.FRAGMENT_SETTINGS;
import static com.hijacker.MainActivity.arch;
import static com.hijacker.MainActivity.isArchValid;
import static com.hijacker.MainActivity.loadPreferences;
import static com.hijacker.MainActivity.pref_edit;
import static com.hijacker.MainActivity.versionName;
import static com.hijacker.MainActivity.watchdog;
import static com.hijacker.MainActivity.currentFragment;

public class SettingsFragment extends Fragment {
    static boolean allow_prefix = false;
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle savedInstanceState){
        // Create a container and host a PreferenceFragmentCompat as a child
        android.widget.FrameLayout fl = new android.widget.FrameLayout(requireContext());
        int id = View.generateViewId();
        fl.setId(id);
        // Add the preference fragment
        getChildFragmentManager().beginTransaction().replace(id, new InnerPrefFragment()).commitNowAllowingStateLoss();
        return fl;
    }

    public static class InnerPrefFragment extends PreferenceFragmentCompat {
        SharedPreferences.OnSharedPreferenceChangeListener listener;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            if(!isArchValid()){
                Preference pref = findPreference("install_nexmon");
                if(pref!=null){
                    pref.setSummary(requireContext().getString(R.string.incorrect_arch) + ' ' + arch);
                    pref.setEnabled(false);
                }
                Preference p = findPreference("prefix");
                if(p!=null) p.setEnabled(true);
            }
            if(allow_prefix) {
                Preference p = findPreference("prefix");
                if(p!=null) p.setEnabled(true);
            }

            Preference testTools = findPreference("test_tools");
            if(testTools!=null) testTools.setOnPreferenceClickListener(preference -> {
                new TestDialog().show(requireActivity().getSupportFragmentManager(), "TestDialog");
                return false;
            });

            Preference resetPref = findPreference("reset_pref");
            if(resetPref!=null) resetPref.setOnPreferenceClickListener(preference -> {
                CustomDialog dialog = new CustomDialog();
                dialog.setTitle(requireContext().getString(R.string.reset_dialog_title));
                dialog.setMessage(requireContext().getString(R.string.reset_dialog_message));
                dialog.setPositiveButton(requireContext().getString(R.string.yes), () -> {
                    pref_edit.putString("iface", requireContext().getString(R.string.iface));
                    pref_edit.putString("prefix", requireContext().getString(R.string.prefix));
                    pref_edit.putString("enable_monMode", requireContext().getString(R.string.enable_monMode));
                    pref_edit.putString("disable_monMode", requireContext().getString(R.string.disable_monMode));
                    pref_edit.putBoolean("enable_on_airodump", Boolean.parseBoolean(requireContext().getString(R.string.enable_on_airodump)));
                    pref_edit.putString("deauthWait", requireContext().getString(R.string.deauthWait));
                    pref_edit.putBoolean("show_notif", Boolean.parseBoolean(requireContext().getString(R.string.show_notif)));
                    pref_edit.putBoolean("show_details", Boolean.parseBoolean(requireContext().getString(R.string.show_details)));
                    pref_edit.putBoolean("airOnStartup", Boolean.parseBoolean(requireContext().getString(R.string.airOnStartup)));
                    pref_edit.putBoolean("debug", Boolean.parseBoolean(requireContext().getString(R.string.debug)));
                    pref_edit.putBoolean("always_cap", Boolean.parseBoolean(requireContext().getString(R.string.always_cap)));
                    pref_edit.putString("chroot_dir", requireContext().getString(R.string.chroot_dir));
                    pref_edit.putBoolean("monstart", Boolean.parseBoolean(requireContext().getString(R.string.monstart)));
                    pref_edit.putString("custom_chroot_cmd", "");
                    pref_edit.putBoolean("cont_on_fail", Boolean.parseBoolean(requireContext().getString(R.string.cont_on_fail)));
                    pref_edit.putBoolean("watchdog", Boolean.parseBoolean(requireContext().getString(R.string.watchdog)));
                    pref_edit.putBoolean("target_deauth", Boolean.parseBoolean(requireContext().getString(R.string.target_deauth)));
                    pref_edit.putBoolean("update_on_startup", Boolean.parseBoolean(requireContext().getString(R.string.auto_update)));
                    pref_edit.apply();
                    loadPreferences();
                });
                dialog.setNegativeButton(requireContext().getString(R.string.cancel), null);
                dialog.show(requireActivity().getSupportFragmentManager(), "CustomDialog for reset confirmation");
                return false;
            });

            Preference copySample = findPreference("copy_sample_button");
            if(copySample!=null) copySample.setOnPreferenceClickListener(preference -> {
                new CopySampleDialog().show(requireActivity().getSupportFragmentManager(), "CopySampleDialog");
                return false;
            });

            Preference installPref = findPreference("install_nexmon");
            if(installPref!=null) installPref.setOnPreferenceClickListener(preference -> {
                new InstallFirmwareDialog().show(requireActivity().getSupportFragmentManager(), "InstallFirmwareDialog");
                return false;
            });

            Preference feedback = findPreference("send_feedback");
            if(feedback!=null) feedback.setOnPreferenceClickListener(preference -> {
                new FeedbackDialog().show(requireActivity().getSupportFragmentManager(), "FeedbackDialog");
                return false;
            });

            Preference github = findPreference("github");
            if(github!=null) github.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://github.com/chrisk44/Hijacker"));
                startActivity(intent);
                return false;
            });

            Preference chrootDir = findPreference("chroot_dir");
            if(chrootDir!=null) chrootDir.setOnPreferenceClickListener(preference -> {
                final FileExplorerDialog dialog = new FileExplorerDialog();
                dialog.setToSelect(FileExplorerDialog.SELECT_DIR);
                dialog.setStartingDir(new RootFile("/data/local/"));
                dialog.setOnSelect(() -> {
                    pref_edit.putString("chroot_dir", dialog.result.getAbsolutePath());
                    pref_edit.apply();
                    loadPreferences();
                });
                dialog.show(requireActivity().getSupportFragmentManager(), "FileExplorerDialog");
                return false;
            });

            Preference updateOnStartup = findPreference("update_on_startup");
            if(updateOnStartup!=null) updateOnStartup.setOnPreferenceChangeListener((preference, newValue) -> {
                if((boolean)newValue){
                    new Thread(() -> {
                        Looper.prepare();
                        ((MainActivity)requireActivity()).checkForUpdate(true);
                    }).start();
                }
                return true;
            });

            Preference version = findPreference("version");
            if(version!=null) version.setSummary(versionName);
        }

        @Override
        public void onResume() {
            super.onResume();
            currentFragment = FRAGMENT_SETTINGS;
            final MainActivity mainActivity = (MainActivity)requireActivity();
            mainActivity.refreshDrawer();
            listener = (sharedPreferences, key) -> {
                loadPreferences();
                boolean running = mainActivity.watchdogTask != null && mainActivity.watchdogTask.isRunning();
                if (watchdog && !running) {
                    // Need to start watchdog if not running
                    if (mainActivity.watchdogTask == null || !mainActivity.watchdogTask.isRunning()) {
                        mainActivity.watchdogTask = new WatchdogTask(requireActivity());
                        mainActivity.watchdogTask.start();
                    }
                } else if (!watchdog && running) {
                    // Need to stop watchdog if running
                    if (mainActivity.watchdogTask != null) {
                        cancelWatchdog(mainActivity);
                    }
                }
            };
            androidx.preference.PreferenceManager pm = getPreferenceManager();
            if (pm != null) {
                SharedPreferences sp = pm.getSharedPreferences();
                if (sp != null) sp.registerOnSharedPreferenceChangeListener(listener);
            }
        }

        @Override
        public void onPause() {
            androidx.preference.PreferenceManager pm = getPreferenceManager();
            if (pm != null) {
                SharedPreferences sp = pm.getSharedPreferences();
                if (sp != null) sp.unregisterOnSharedPreferenceChangeListener(listener);
            }
             super.onPause();
         }

         private void cancelWatchdog(final MainActivity mainActivity) {
            if (mainActivity.watchdogTask != null) {
                // Use the non-deprecated requestStop() to signal the WatchdogTask to stop.
                mainActivity.watchdogTask.requestStop();
                mainActivity.watchdogTask = null;
            }
        }
     }
 }
