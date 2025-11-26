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
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.app.DownloadManager;
import androidx.fragment.app.DialogFragment;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.media.MediaScannerConnection;

import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.lang.ref.WeakReference;

import javax.net.ssl.HttpsURLConnection;

import static com.hijacker.MainActivity.WORDLISTS_LINK;
import static com.hijacker.MainActivity.wl_path;

public class WordlistDownloadDialog extends DialogFragment{
    View dialogView;
    ListView listView;
    ProgressBar progressBar;
    ExecutorService executor;
    WordlistAdapter adapter;
    ArrayList<Wordlist> wordlists = new ArrayList<>();
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        dialogView = requireActivity().getLayoutInflater().inflate(R.layout.wordlist_dialog, null);

        listView = dialogView.findViewById(R.id.wl_listview);
        progressBar = dialogView.findViewById(R.id.wl_pb);

        builder.setView(dialogView);
        builder.setTitle(R.string.wordlist_dialog_title);
        builder.setNeutralButton(R.string.cancel, (dialog, which) -> {});

        adapter = new WordlistAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            beginDownload(wordlists.get(i));
            dismissAllowingStateLoss();
        });


        // start background loader using an executor to avoid AsyncTask deprecation and leaks
        executor = Executors.newSingleThreadExecutor();
        executor.submit(new LoadTaskRunnable(this));

        return builder.create();
    }
    @Override
    public void onDestroyView(){
        super.onDestroyView();
        // shutdown executor if running
        if(executor!=null && !executor.isShutdown()) executor.shutdownNow();
    }

    void beginDownload(Wordlist wl){
        //Check for external storage and internet permission
        final String[] needed = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.INTERNET};
        if(!PermissionUtils.hasPermissions(requireActivity(), needed)){
            // Request missing permissions on UI thread and notify user
            requireActivity().runOnUiThread(() -> PermissionUtils.requestMissingPermissions(requireActivity(), needed, 0));
            Toast.makeText(getActivity(), getString(R.string.no_permissions), Toast.LENGTH_SHORT).show();
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(wl.download_url));
        request.setTitle(wl.filename);
        // allowScanningByMediaScanner() is deprecated. Instead, enqueue the download and
        // scan the file with MediaScannerConnection when the file appears on disk.
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        final File destFile = new File(wl_path, wl.filename);
        request.setDestinationUri(Uri.fromFile(destFile));

        DownloadManager manager = (DownloadManager) requireActivity().getSystemService(Context.DOWNLOAD_SERVICE);
        if(manager!=null){
            manager.enqueue(request);

            // Background task: wait briefly for the file to appear and then scan it so it's visible
            // to media providers (replacement for the deprecated allowScanningByMediaScanner()).
            java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
                try{
                    int waited = 0;
                    while(!destFile.exists() && waited < 60_000){
                        Thread.sleep(500);
                        waited += 500;
                    }
                    if(destFile.exists()){
                        Context ctx = null;
                        try{ ctx = requireActivity().getApplicationContext(); }catch(Throwable ignored){}
                        if(ctx!=null) MediaScannerConnection.scanFile(ctx, new String[]{destFile.getAbsolutePath()}, null, null);
                    }
                }catch(InterruptedException ignored){ }
            });
        }else{
            Toast.makeText(getActivity(), getString(R.string.cant_start_download), Toast.LENGTH_SHORT).show();
        }
    }

    // Replaced deprecated AsyncTask with a static Runnable to avoid leaking the fragment
    private static class LoadTaskRunnable implements Runnable{
        private final WeakReference<WordlistDownloadDialog> ref;
        LoadTaskRunnable(WordlistDownloadDialog dialog){
            this.ref = new WeakReference<>(dialog);
        }
        @Override
        public void run(){
            WordlistDownloadDialog dialog = ref.get();
            if(dialog==null) return;
            boolean success = true;
            try{
                HttpsURLConnection connection = (HttpsURLConnection) (new URL(WORDLISTS_LINK).openConnection());
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                JsonReader reader = new JsonReader(new InputStreamReader(connection.getInputStream()));
                reader.beginArray();
                if(!reader.hasNext()){
                    Log.e("HIJACKER/WlLoadTask", "No files found");
                    success = false;
                } else {
                    // collect items into a local list first
                    ArrayList<Wordlist> tmp = new ArrayList<>();
                    while(reader.hasNext()){
                        reader.beginObject();
                        String filename = null, download_url = null;
                        int size = -1;
                        while(reader.hasNext()){
                            String field = reader.nextName();
                            switch(field){
                                case "name":
                                    filename = reader.nextString();
                                    break;
                                case "size":
                                    size = reader.nextInt();
                                    break;
                                case "download_url":
                                    download_url = reader.nextString();
                                    break;
                                default:
                                    reader.skipValue();
                                    break;
                            }
                        }
                        reader.endObject();
                        if(filename!=null && download_url!=null) tmp.add(new Wordlist(filename, size, download_url));
                    }
                    reader.endArray();
                    reader.close();
                    // swap into fragment's list
                    WordlistDownloadDialog finalDialog = ref.get();
                    if(finalDialog!=null){
                        finalDialog.wordlists.clear();
                        finalDialog.wordlists.addAll(tmp);
                    }
                }
            }catch(Exception e){
                Log.e("HIJACKER/WlLoadTask", e.toString());
                success = false;
            }

            // post UI updates on main thread
            WordlistDownloadDialog finalDialog = ref.get();
            if(finalDialog==null) return;
            final boolean successResult = success;
            finalDialog.requireActivity().runOnUiThread(() -> {
                ObjectAnimator pb_animator = ObjectAnimator.ofFloat(finalDialog.progressBar, "alpha", 1f, 0f);
                pb_animator.addListener(new Animator.AnimatorListener(){
                    @Override
                    public void onAnimationStart(@NonNull Animator animator){}
                    @Override
                    public void onAnimationEnd(@NonNull Animator animator){
                        if(finalDialog.progressBar!=null) finalDialog.progressBar.setIndeterminate(false);
                    }
                    @Override
                    public void onAnimationCancel(@NonNull Animator animator){}
                    @Override
                    public void onAnimationRepeat(@NonNull Animator animator){}
                });
                pb_animator.start();

                if(finalDialog.adapter!=null) finalDialog.adapter.notifyDataSetChanged();
                if(!successResult){
                    if(((MainActivity) finalDialog.requireActivity()).internetAvailable()){
                        Toast.makeText(finalDialog.getActivity(), finalDialog.getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                    }else{
                        Toast.makeText(finalDialog.getActivity(), finalDialog.getString(R.string.no_internet), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
    class WordlistAdapter extends ArrayAdapter<Tile>{
        WordlistAdapter(){
            super(WordlistDownloadDialog.this.requireActivity(), R.layout.two_line_selectable_item);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent){
            View itemview = convertView;
            if(itemview==null){
                itemview = requireActivity().getLayoutInflater().inflate(R.layout.two_line_selectable_item, parent, false);
            }

            Wordlist current = wordlists.get(position);

            TextView main_tv = itemview.findViewById(R.id.main_text_view);
            TextView secondary_tv = itemview.findViewById(R.id.secondary_text_view);

            main_tv.setText(current.filename);
            int sizeKb = current.size/1024;
            secondary_tv.setText(getString(R.string.size_kb_format, getString(R.string.size), sizeKb));

            return itemview;
        }

        @Override
        public int getCount(){
            return wordlists.size();
        }
    }
    private static class Wordlist{
        int size;
        String filename, download_url;
        Wordlist(String filename, int size, String url){
            this.filename = filename;
            this.size = size;
            this.download_url = url;
        }
    }
}
