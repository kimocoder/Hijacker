package com.hijacker;

/*
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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;

/**
 * Utility class for showing progress indicators during long-running operations
 */
public class ProgressIndicators {
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Show an indeterminate progress dialog
     * @param context Context
     * @param message Message to display
     * @return Dialog instance (call dismiss() when done)
     */
    public static Dialog showProgressDialog(Context context, String message) {
        return showProgressDialog(context, message, false, 0, 0);
    }

    /**
     * Show a progress dialog with cancel button
     * @param context Context
     * @param message Message to display
     * @param cancelable Whether the dialog can be cancelled
     * @param onCancel Runnable to execute on cancel (can be null)
     * @return Dialog instance
     */
    public static Dialog showProgressDialog(Context context, String message, boolean cancelable, Runnable onCancel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.progress_dialog, null);
        TextView messageView = dialogView.findViewById(R.id.progress_message);
        messageView.setText(message);

        builder.setView(dialogView);
        builder.setCancelable(cancelable);

        if (cancelable && onCancel != null) {
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
                onCancel.run();
                dialog.dismiss();
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        return dialog;
    }

    /**
     * Show a determinate progress dialog (with percentage)
     * @param context Context
     * @param message Message to display
     * @param showProgress Whether to show progress percentage
     * @param max Maximum progress value
     * @param current Current progress value
     * @return Dialog instance
     */
    public static Dialog showProgressDialog(Context context, String message, boolean showProgress, int max, int current) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        View dialogView = LayoutInflater.from(context).inflate(
            showProgress ? R.layout.progress_dialog_determinate : R.layout.progress_dialog,
            null
        );

        TextView messageView = dialogView.findViewById(R.id.progress_message);
        messageView.setText(message);

        if (showProgress) {
            ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
            TextView percentView = dialogView.findViewById(R.id.progress_percent);

            progressBar.setMax(max);
            progressBar.setProgress(current);

            int percent = max > 0 ? (current * 100 / max) : 0;
            percentView.setText(percent + "%");
        }

        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();

        return dialog;
    }

    /**
     * Update progress in a determinate progress dialog
     * @param dialog Dialog to update
     * @param message New message (can be null to keep current)
     * @param current Current progress value
     */
    public static void updateProgress(Dialog dialog, String message, int current) {
        if (!(dialog instanceof AlertDialog)) return;

        AlertDialog alertDialog = (AlertDialog) dialog;
        View dialogView = alertDialog.findViewById(R.id.progress_dialog_root);
        if (dialogView == null) return;

        if (message != null) {
            TextView messageView = dialogView.findViewById(R.id.progress_message);
            if (messageView != null) {
                messageView.setText(message);
            }
        }

        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        if (progressBar != null) {
            progressBar.setProgress(current);

            int max = progressBar.getMax();
            int percent = max > 0 ? (current * 100 / max) : 0;

            TextView percentView = dialogView.findViewById(R.id.progress_percent);
            if (percentView != null) {
                percentView.setText(percent + "%");
            }
        }
    }

    /**
     * Show a progress snackbar
     * @param view View to attach snackbar to
     * @param message Message to display
     * @return Snackbar instance
     */
    public static Snackbar showProgressSnackbar(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        return snackbar;
    }

    /**
     * Show a progress snackbar with action
     * @param view View to attach snackbar to
     * @param message Message to display
     * @param actionText Action button text
     * @param action Action to perform
     * @return Snackbar instance
     */
    public static Snackbar showProgressSnackbar(View view, String message, String actionText, Runnable action) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(actionText, v -> {
            if (action != null) action.run();
        });
        snackbar.show();
        return snackbar;
    }

    /**
     * Dismiss a progress indicator safely on main thread
     * @param dialog Dialog to dismiss (can be null)
     */
    public static void dismissDialog(Dialog dialog) {
        if (dialog == null) return;

        mainHandler.post(() -> {
            try {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            } catch (Exception e) {
                // Dialog was already dismissed or context destroyed
            }
        });
    }

    /**
     * Dismiss a snackbar safely
     * @param snackbar Snackbar to dismiss (can be null)
     */
    public static void dismissSnackbar(Snackbar snackbar) {
        if (snackbar == null) return;

        mainHandler.post(() -> {
            try {
                if (snackbar.isShown()) {
                    snackbar.dismiss();
                }
            } catch (Exception e) {
                // Snackbar was already dismissed
            }
        });
    }

    /**
     * Show progress on main thread
     * @param activity Activity context
     * @param message Message to display
     * @return Dialog instance
     */
    public static Dialog showProgressOnMainThread(Activity activity, String message) {
        final Dialog[] dialogHolder = new Dialog[1];

        if (Looper.myLooper() == Looper.getMainLooper()) {
            dialogHolder[0] = showProgressDialog(activity, message);
        } else {
            mainHandler.post(() -> {
                dialogHolder[0] = showProgressDialog(activity, message);
            });
        }

        return dialogHolder[0];
    }

    /**
     * Builder class for creating customized progress dialogs
     */
    public static class Builder {
        private final Context context;
        private String message = "";
        private boolean cancelable = false;
        private boolean determinate = false;
        private int max = 100;
        private int current = 0;
        private Runnable onCancel = null;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder setOnCancel(Runnable onCancel) {
            this.onCancel = onCancel;
            this.cancelable = true;
            return this;
        }

        public Builder setDeterminate(boolean determinate) {
            this.determinate = determinate;
            return this;
        }

        public Builder setMax(int max) {
            this.max = max;
            return this;
        }

        public Builder setProgress(int current) {
            this.current = current;
            return this;
        }

        public Dialog build() {
            if (determinate) {
                return showProgressDialog(context, message, true, max, current);
            } else if (cancelable) {
                return showProgressDialog(context, message, true, onCancel);
            } else {
                return showProgressDialog(context, message);
            }
        }

        public Dialog show() {
            Dialog dialog = build();
            dialog.show();
            return dialog;
        }
    }
}

