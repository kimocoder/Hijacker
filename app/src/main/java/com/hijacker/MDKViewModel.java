package com.hijacker;

import androidx.lifecycle.ViewModel;

/**
 * ViewModel to hold MDKFragment state across configuration changes.
 * This avoids using the deprecated setRetainInstance(true).
 */
public class MDKViewModel extends ViewModel {
    // lightweight public fields for simplicity; they mirror MDKFragment's previous "static" fields
    public AP ados_ap = null;
    public String custom_mac = null;
    public String ssid_file = null;
    public boolean managed = true, adhoc = true, opn = true, wep = true, tkip = true, aes = true;
    public boolean bf = false, ados = false;
}

