package com.hijacker;
import androidx.lifecycle.ViewModel;

/**
 * ViewModel to hold ReaverFragment state across configuration changes.
 */
public class ReaverViewModel extends ViewModel {
    private String consoleText = "";
    private String pinDelay = "1";
    private String lockedDelay = "60";
    private String customMac = null;
    private boolean pixieDustEnabled = true;
    private boolean pixieDust = false;
    private boolean ignoreLocked = false;
    private boolean eapFail = false;
    private boolean smallDh = false;
    private boolean noNack = false;
    private AP selectedAp = null;
    private boolean taskRunning = false;
    public String getConsoleText() { return consoleText; }
    public String getPinDelay() { return pinDelay; }
    public String getLockedDelay() { return lockedDelay; }
    public String getCustomMac() { return customMac; }
    public boolean isPixieDustEnabled() { return pixieDustEnabled; }
    public boolean isPixieDust() { return pixieDust; }
    public boolean isIgnoreLocked() { return ignoreLocked; }
    public boolean isEapFail() { return eapFail; }
    public boolean isSmallDh() { return smallDh; }
    public boolean isNoNack() { return noNack; }
    public AP getSelectedAp() { return selectedAp; }
    public boolean isTaskRunning() { return taskRunning; }
    public void setConsoleText(String text) { this.consoleText = text; }
    public void setPinDelay(String delay) { this.pinDelay = delay; }
    public void setLockedDelay(String delay) { this.lockedDelay = delay; }
    public void setCustomMac(String mac) { this.customMac = mac; }
    public void setPixieDustEnabled(boolean enabled) { this.pixieDustEnabled = enabled; }
    public void setPixieDust(boolean enabled) { this.pixieDust = enabled; }
    public void setIgnoreLocked(boolean enabled) { this.ignoreLocked = enabled; }
    public void setEapFail(boolean enabled) { this.eapFail = enabled; }
    public void setSmallDh(boolean enabled) { this.smallDh = enabled; }
    public void setNoNack(boolean enabled) { this.noNack = enabled; }
    public void setSelectedAp(AP ap) { this.selectedAp = ap; }
    public void setTaskRunning(boolean running) { this.taskRunning = running; }
    public void appendConsoleText(String text) { this.consoleText += text; }
    public void clearConsole() { this.consoleText = ""; }
    @Override
    protected void onCleared() {
        super.onCleared();
    }
}
