package com.hijacker;

/*
    Copyright (C) 2019  Christos Kyriakopoulos

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
import androidx.fragment.app.FragmentTransaction;
import androidx.annotation.NonNull;

import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
import static com.hijacker.MainActivity.mFragmentManager;

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
                        //Open editor for this
                        CustomActionEditorFragment fragment = new CustomActionEditorFragment();
                        fragment.action = CustomAction.cmds.get(index);

                        FragmentTransaction ft = mFragmentManager.beginTransaction();
                        ft.replace(R.id.fragment1, fragment);
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                        ft.addToBackStack(null);
                        ft.commitAllowingStateLoss();
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
            //Open editor for new
            FragmentTransaction ft = mFragmentManager.beginTransaction();
            ft.replace(R.id.fragment1, new CustomActionEditorFragment());
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.addToBackStack(null);
            ft.commitAllowingStateLoss();
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
