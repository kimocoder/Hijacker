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

import android.os.Bundle;
import android.os.Environment;
import com.google.android.material.snackbar.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import static com.hijacker.MainActivity.FRAGMENT_MDK;
import static com.hijacker.MainActivity.PROCESS_MDK_BF;
import static com.hijacker.MainActivity.PROCESS_MDK_DOS;
import static com.hijacker.MainActivity.currentFragment;
import static com.hijacker.MainActivity.runInHandler;
import static com.hijacker.MainActivity.startAdos;
import static com.hijacker.MainActivity.startBeaconFlooding;
import static com.hijacker.MainActivity.stop;

public class MDKFragment extends Fragment {
    View fragmentView;
    // ViewModel holds state that used to be kept via setRetainInstance(true) and static fields
    MDKViewModel viewModel;
    // Keep a static reference for ados_ap because other classes (AP.java) set it directly. We sync it with the ViewModel when available.
    static AP ados_ap = null;
    // Compatibility flags: some code references MDKFragment.ados and MDKFragment.bf — keep static flags synchronized with the ViewModel
    public static volatile boolean ados = false;
    public static volatile boolean bf = false;
    static SwitchCompat bf_switch, ados_switch; // UI references remain static for now; we'll keep as-is but only update state via viewModel
    EditText ssidView;
    CheckBox managed_cb, adhoc_cb, opn_cb, wep_cb, tkip_cb, aes_cb;
    Button select_button;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        // Use ViewModel to retain UI/state across configuration changes (replacement for setRetainInstance(true))
        viewModel = new ViewModelProvider(requireActivity()).get(MDKViewModel.class);
        fragmentView = inflater.inflate(R.layout.mdk_fragment, container, false);

        ssidView = fragmentView.findViewById(R.id.ssid_file);
        managed_cb = fragmentView.findViewById(R.id.managed);
        adhoc_cb = fragmentView.findViewById(R.id.adhoc);
        opn_cb = fragmentView.findViewById(R.id.opn);
        wep_cb = fragmentView.findViewById(R.id.wep);
        tkip_cb = fragmentView.findViewById(R.id.tkip);
        aes_cb = fragmentView.findViewById(R.id.aes);
        bf_switch = fragmentView.findViewById(R.id.bf_switch);
        ados_switch = fragmentView.findViewById(R.id.ados_switch);
        select_button = fragmentView.findViewById(R.id.select_ap_ados);

        // Observe ViewModel LiveData so UI is updated when state changes elsewhere
        viewModel.getBf().observe(getViewLifecycleOwner(), v -> {
            if(v==null) return;
            // avoid re-triggering listener by setting checked only when different
            if(bf_switch.isChecked() != v){
                bf_switch.setChecked(v);
            }
            bf = v; // keep static compatibility flag in sync
        });
        viewModel.getAdos().observe(getViewLifecycleOwner(), v -> {
            if(v==null) return;
            if(ados_switch.isChecked() != v){
                ados_switch.setChecked(v);
            }
            ados = v; // keep static compatibility flag in sync
        });
        viewModel.getSsidFile().observe(getViewLifecycleOwner(), s -> {
            if(s!=null) ssidView.setText(s);
        });
        viewModel.getManaged().observe(getViewLifecycleOwner(), v -> { if(v!=null) managed_cb.setChecked(v); });
        viewModel.getAdhoc().observe(getViewLifecycleOwner(), v -> { if(v!=null) adhoc_cb.setChecked(v); });
        viewModel.getOpn().observe(getViewLifecycleOwner(), v -> { if(v!=null) opn_cb.setChecked(v); });
        viewModel.getWep().observe(getViewLifecycleOwner(), v -> { if(v!=null) wep_cb.setChecked(v); });
        viewModel.getTkip().observe(getViewLifecycleOwner(), v -> { if(v!=null) tkip_cb.setChecked(v); });
        viewModel.getAes().observe(getViewLifecycleOwner(), v -> { if(v!=null) aes_cb.setChecked(v); });
        viewModel.getAdosAp().observe(getViewLifecycleOwner(), ap -> {
            if(ap!=null) select_button.setText(ap.toString());
        });
        viewModel.getCustomMac().observe(getViewLifecycleOwner(), mac -> {
            if(mac!=null) select_button.setText(mac);
        });

        fragmentView.findViewById(R.id.ssid_file_fe_btn).setOnClickListener(v -> {
            final FileExplorerDialog dialog = new FileExplorerDialog();
            dialog.setToSelect(FileExplorerDialog.SELECT_EXISTING_FILE);
            dialog.setStartingDir(new RootFile(Environment.getExternalStorageDirectory().toString()));
            dialog.setOnSelect(() -> {
                ssidView.setText(dialog.result.getAbsolutePath());
                ssidView.setError(null);
                viewModel.setSsidFile(dialog.result.getAbsolutePath());
            });
            dialog.show(requireActivity().getSupportFragmentManager(), "FileExplorerDialog");
        });

        // Update ViewModel when switches are toggled; keep static flags in sync
        bf_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            viewModel.setBf(b); // persist in ViewModel
            bf = b; // keep static compatibility flag in sync
            onBfSwitch(b);
        });
        ados_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            viewModel.setAdos(b);
            ados = b; // keep static compatibility flag in sync
            onDosSwitch(b);
        });
        select_button.setOnClickListener(this::onSelectClick);

        // Checkbox listeners update ViewModel
        managed_cb.setOnCheckedChangeListener((v, checked) -> viewModel.setManaged(checked));
        adhoc_cb.setOnCheckedChangeListener((v, checked) -> viewModel.setAdhoc(checked));
        opn_cb.setOnCheckedChangeListener((v, checked) -> viewModel.setOpn(checked));
        wep_cb.setOnCheckedChangeListener((v, checked) -> viewModel.setWep(checked));
        tkip_cb.setOnCheckedChangeListener((v, checked) -> viewModel.setTkip(checked));
        aes_cb.setOnCheckedChangeListener((v, checked) -> viewModel.setAes(checked));

        return fragmentView;
    }
    @Override
    public void onResume() {
        super.onResume();
        currentFragment = FRAGMENT_MDK;
        //Restore options
        // Set switches from ViewModel snapshots (avoid null issues)
        bf_switch.setChecked(viewModel.isBfValue());
        ados_switch.setChecked(viewModel.isAdosValue());

        // Prefer ViewModel-held values; fall back to the static ados_ap that may be set from other classes
        if(viewModel.getCustomMacValue()!=null) select_button.setText(viewModel.getCustomMacValue());
        else if(viewModel.getAdosApValue()!=null) select_button.setText(viewModel.getAdosApValue().toString());
        else if(ados_ap!=null) select_button.setText(ados_ap.toString());
        else if(!AP.marked.isEmpty()){
            viewModel.setAdosAp(AP.marked.get(AP.marked.size()-1));
            select_button.setText(viewModel.getAdosApValue().toString());
        }

        if(viewModel.getSsidFileValue()!=null) ssidView.setText(viewModel.getSsidFileValue());
        // Ensure checkboxes reflect ViewModel snapshot values and keep listeners already set earlier (they update ViewModel)
        managed_cb.setChecked(viewModel.isManagedValue());
        adhoc_cb.setChecked(viewModel.isAdhocValue());
        opn_cb.setChecked(viewModel.isOpnValue());
        wep_cb.setChecked(viewModel.isWepValue());
        tkip_cb.setChecked(viewModel.isTkipValue());
        aes_cb.setChecked(viewModel.isAesValue());
        ((MainActivity) requireActivity()).refreshDrawer();
    }
    @Override
    public void onPause(){
        super.onPause();
        //Save options
        viewModel.setSsidFile(ssidView.getText().toString());
        // individual checkboxes already update the ViewModel in their listeners; ensure snapshot values are set
        viewModel.setManaged(managed_cb.isChecked());
        viewModel.setAdhoc(adhoc_cb.isChecked());
        viewModel.setOpn(opn_cb.isChecked());
        viewModel.setWep(wep_cb.isChecked());
        viewModel.setTkip(tkip_cb.isChecked());
        viewModel.setAes(aes_cb.isChecked());
    }

    void onSelectClick(View view){
        PopupMenu popup = new PopupMenu(getActivity(), view);

        popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());
        int i = 0;
        for(AP ap : AP.APs){
            popup.getMenu().add(0, i, i, ap.toString());
            i++;
        }
        popup.getMenu().add(1, i, i, "Custom");
        popup.setOnMenuItemClickListener(item -> {
            //ItemId = i in for()
            if(item.getGroupId()==0){
                // Clear custom MAC and set the selected AP into ViewModel
                viewModel.setCustomMac(null);
                AP temp = AP.APs.get(item.getItemId());
                if(viewModel.getAdosApValue() != temp){
                    viewModel.setAdosAp(temp);
                    runInHandler(() -> {
                        ados_switch.setChecked(false);
                        stop(PROCESS_MDK_DOS);
                    });
                }
                AP current = viewModel.getAdosApValue();
                if(current != null) select_button.setText(current.toString());
             }else{
                 //Clcked custom
                 final EditTextDialog dialog = new EditTextDialog();
                 dialog.setTitle(getString(R.string.custom_ap_title));
                 dialog.setHint(getString(R.string.mac_address));
                 dialog.setRunnable(() -> {
                    viewModel.setAdosAp(null);
                    viewModel.setCustomMac(dialog.result);
                    select_button.setText(dialog.result);
                 });
                 dialog.show(requireActivity().getSupportFragmentManager(), "EditTextDialog");
             }
             return true;
         });
         popup.show();
    }
    void onBfSwitch(boolean b){
        if(b){
            ssidView.setError(null);
            String ssid_file = ssidView.getText().toString();
            boolean managed = viewModel.isManagedValue();
            boolean adhoc = viewModel.isAdhocValue();
            boolean opn = viewModel.isOpnValue();
            boolean wep = viewModel.isWepValue();
            boolean tkip = viewModel.isTkipValue();
            boolean aes = viewModel.isAesValue();
             String args = "";
             if(!managed && !adhoc){
                 Snackbar.make(fragmentView, getString(R.string.select_type), Snackbar.LENGTH_LONG).show();
                 bf_switch.setChecked(false);
                 return;
             }
             if(!(managed && adhoc)){
                if(managed) args += " -t 0";
                if(adhoc) args += " -t 1";
             }
             if(!(opn || wep || tkip || aes)){
                 Snackbar.make(fragmentView, getString(R.string.select_enc), Snackbar.LENGTH_LONG).show();
                 bf_switch.setChecked(false);
                 return;
             }
             if(!(ssid_file.isEmpty() || ssid_file.startsWith("/"))){
                ssidView.setError(getString(R.string.filename_invalid));
                ssidView.requestFocus();
                bf_switch.setChecked(false);
                return;
             }
             args += " -w ";
             if(opn) args += 'n';
             if(wep) args += 'w';
             if(tkip) args += 't';
             if(aes) args += 'a';
             if(!ssid_file.isEmpty()){
                RootFile ssidRootFile = new RootFile(ssid_file);
                if(!ssidRootFile.isFile()){
                    ssidView.setError(getString(R.string.not_file_or_exists));
                    ssidView.requestFocus();
                    bf_switch.setChecked(false);
                    return;
                }else{
                    args += " -f " + ssid_file;
                }
             }
             startBeaconFlooding(args);
        }else{
            viewModel.setBf(false);
            bf = false; // keep static compatibility flag in sync
            stop(PROCESS_MDK_BF);
        }
     }
     void onDosSwitch(boolean b){
         if(b){
            AP a = viewModel.getAdosApValue();
            String custom = viewModel.getCustomMacValue();
            startAdos(a == null ? custom : a.mac);
         }else{
            viewModel.setAdos(false);
             ados = false; // keep static compatibility flag in sync
             stop(PROCESS_MDK_DOS);
         }
     }
}
