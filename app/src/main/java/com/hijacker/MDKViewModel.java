package com.hijacker;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * ViewModel to hold MDKFragment state across configuration changes.
 * Uses LiveData so the fragment can observe changes and keep UI in sync.
 */
public class MDKViewModel extends ViewModel {
    // LiveData-backed state
    private final MutableLiveData<AP> adosAp = new MutableLiveData<>(null);
    private final MutableLiveData<String> customMac = new MutableLiveData<>(null);
    private final MutableLiveData<String> ssidFile = new MutableLiveData<>(null);

    private final MutableLiveData<Boolean> managed = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> adhoc = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> opn = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> wep = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> tkip = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> aes = new MutableLiveData<>(false);

    private final MutableLiveData<Boolean> bf = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> ados = new MutableLiveData<>(false);

    // Accessors for LiveData
    public LiveData<AP> getAdosAp() { return adosAp; }
    public void setAdosAp(AP ap) { adosAp.postValue(ap); }

    public LiveData<String> getCustomMac() { return customMac; }
    public void setCustomMac(String mac) { customMac.postValue(mac); }

    public LiveData<String> getSsidFile() { return ssidFile; }
    public void setSsidFile(String file) { ssidFile.postValue(file); }

    public LiveData<Boolean> getManaged() { return managed; }
    public void setManaged(boolean v) { managed.postValue(v); }

    public LiveData<Boolean> getAdhoc() { return adhoc; }
    public void setAdhoc(boolean v) { adhoc.postValue(v); }

    public LiveData<Boolean> getOpn() { return opn; }
    public void setOpn(boolean v) { opn.postValue(v); }

    public LiveData<Boolean> getWep() { return wep; }
    public void setWep(boolean v) { wep.postValue(v); }

    public LiveData<Boolean> getTkip() { return tkip; }
    public void setTkip(boolean v) { tkip.postValue(v); }

    public LiveData<Boolean> getAes() { return aes; }
    public void setAes(boolean v) { aes.postValue(v); }

    public LiveData<Boolean> getBf() { return bf; }
    public void setBf(boolean v) { bf.postValue(v); }

    public LiveData<Boolean> getAdos() { return ados; }
    public void setAdos(boolean v) { ados.postValue(v); }

    // Convenience: snapshot getters (null-safe)
    public AP getAdosApValue(){ return adosAp.getValue(); }
    public String getCustomMacValue(){ return customMac.getValue(); }
    public String getSsidFileValue(){ return ssidFile.getValue(); }
    public boolean isManagedValue(){ Boolean v = managed.getValue(); return v != null ? v : false; }
    public boolean isAdhocValue(){ Boolean v = adhoc.getValue(); return v != null ? v : false; }
    public boolean isOpnValue(){ Boolean v = opn.getValue(); return v != null ? v : false; }
    public boolean isWepValue(){ Boolean v = wep.getValue(); return v != null ? v : false; }
    public boolean isTkipValue(){ Boolean v = tkip.getValue(); return v != null ? v : false; }
    public boolean isAesValue(){ Boolean v = aes.getValue(); return v != null ? v : false; }
    public boolean isBfValue(){ Boolean v = bf.getValue(); return v != null ? v : false; }
    public boolean isAdosValue(){ Boolean v = ados.getValue(); return v != null ? v : false; }
}
