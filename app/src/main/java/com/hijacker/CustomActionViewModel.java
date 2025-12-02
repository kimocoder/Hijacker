package com.hijacker;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * ViewModel for CustomActionFragment to keep UI state across configuration changes.
 */
public class CustomActionViewModel extends ViewModel {
    private final MutableLiveData<CustomAction> selectedAction = new MutableLiveData<>(null);
    private final MutableLiveData<Device> targetDevice = new MutableLiveData<>(null);
    private final MutableLiveData<String> consoleText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> running = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> optionsHeight = new MutableLiveData<>(-1);
    public LiveData<CustomAction> getSelectedAction() { return selectedAction; }
    public void setSelectedAction(CustomAction a){ selectedAction.postValue(a); }
    public CustomAction getSelectedActionValue(){ return selectedAction.getValue(); }
    public LiveData<Device> getTargetDevice() { return targetDevice; }
    public void setTargetDevice(Device d){ targetDevice.postValue(d); }
    public Device getTargetDeviceValue(){ return targetDevice.getValue(); }
    public LiveData<String> getConsoleText() { return consoleText; }
    public void setConsoleText(String s){ consoleText.postValue(s); }
    public void appendConsole(String s){
        String cur = consoleText.getValue();
        if(cur==null) cur = "";
        consoleText.postValue(cur + s);
    }
    public String getConsoleTextValue(){
        String v = consoleText.getValue();
        return v == null ? "" : v;
    }

    public LiveData<Boolean> isRunning() { return running; }
    public void setRunning(boolean r){ running.postValue(r); }
    public boolean isRunningValue(){ Boolean v = running.getValue(); return v != null ? v : false; }
    public LiveData<Integer> getOptionsHeight(){ return optionsHeight; }
    public void setOptionsHeight(int h){ optionsHeight.postValue(h); }
    public int getOptionsHeightValue(){ Integer v = optionsHeight.getValue(); return v == null ? -1 : v; }
}
