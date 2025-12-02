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

import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;

import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.PopupMenu;

import java.io.File;

import static com.hijacker.CustomAction.cmds;
import static com.hijacker.MainActivity.FRAGMENT_CUSTOM;
import static com.hijacker.MainActivity.actions_path;
import static com.hijacker.MainActivity.currentFragment;
import static com.hijacker.MainActivity.custom_action_adapter;

public class CustomActionManagerFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.custom_action_manager, container, false);

        ListView list = v.findViewById(R.id.list);
        list.setAdapter(custom_action_adapter);
        list.setOnItemClickListener((adapterView, view, index, l) -> {
            PopupMenu popup = new PopupMenu(getActivity(), view);
            popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());

            //add(groupId, itemId, order, title)
            popup.getMenu().add(0, 0, 0, view.getContext().getString(R.string.edit));
            popup.getMenu().add(0, 1, 1, view.getContext().getString(R.string.delete));

            popup.setOnMenuItemClickListener(item -> {
                switch(item.getItemId()){
                    case 0:
                        //Open editor for this using NavController if available, otherwise fall back
                        try{
                            android.app.Activity act = getActivity();
                            if(act instanceof MainActivity){
                                MainActivity main = (MainActivity)act;
                                // Pass selected action title via Bundle when navigating with NavController
                                String title = CustomAction.cmds.get(index).getTitle();
                                if(main.getNavController()!=null){
                                    android.os.Bundle args = new android.os.Bundle();
                                    args.putString("action_title", title);
                                    main.getNavController().navigate(R.id.nav_custom_editor, args);
                                }else{
                                    Log.w("HIJACKER/Navigation", "NavController not available: can't navigate to CustomActionEditor");
                                }
                            }else{
                                Log.w("HIJACKER/Navigation", "Activity is not MainActivity; cannot navigate to CustomActionEditor");
                            }
                        }catch(Exception e){
                            Log.w("HIJACKER/Navigation", "Exception while navigating to CustomActionEditor", e);
                        }
                        break;
                    case 1:
                        //Delete action
                        CustomDialog dialog = new CustomDialog();
                        dialog.setTitle(view.getContext().getString(R.string.action_delete_title));
                        dialog.setMessage(view.getContext().getString(R.string.action_delete_message));
                        dialog.setPositiveButton(view.getContext().getString(R.string.delete), () -> {
                            new File(actions_path + "/" + cmds.get(index).getTitle() + ".action").delete();
                            cmds.remove(index);
                            custom_action_adapter.notifyDataSetChanged();
                        });
                        dialog.setNegativeButton(view.getContext().getString(R.string.cancel), null);
                        // Use parent fragment manager (non-deprecated) to show dialog
                        dialog.show(getParentFragmentManager(), "CustomDialog for action delete");
                        break;
                }
                return true;
            });
            popup.show();
        });

        FloatingActionButton fab = v.findViewById(R.id.floatingActionButton);
        fab.setOnClickListener(view -> {
            //Open editor for new (empty) action - prefer NavController navigation with fallback
            try{
                android.app.Activity act = getActivity();
                if(act instanceof MainActivity){
                    MainActivity main = (MainActivity)act;
                    if(main.getNavController()!=null){
                        android.os.Bundle args = new android.os.Bundle();
                        args.putString("action_title", null);
                        main.getNavController().navigate(R.id.nav_custom_editor, args);
                    }else{
                        Log.w("HIJACKER/Navigation", "NavController not available: can't navigate to CustomActionEditor (new)");
                    }
                }
            }catch(Exception ignored){
                Log.w("HIJACKER/Navigation", "Failed to navigate to CustomActionEditor via NavController and no fallback available");
            }
         });

         return v;
     }
    @Override
    public void onResume(){
        super.onResume();
        currentFragment = FRAGMENT_CUSTOM;
        // Avoid potential NPE: ensure activity is non-null and of expected type before calling
        android.app.Activity act = getActivity();
        if(act instanceof MainActivity){
            ((MainActivity)act).refreshDrawer();
        }
    }
}
