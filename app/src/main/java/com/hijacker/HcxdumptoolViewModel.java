package com.hijacker;
import androidx.lifecycle.ViewModel;

/**
 * ViewModel to hold HcxdumptoolFragment state across configuration changes.
 */
public class HcxdumptoolViewModel extends ViewModel {
    private String consoleText = "";
    private String filterMode = "2";
    private boolean enableStatus = false;
    private boolean taskRunning = false;
    // Persist last used output file so UI can restore it across rotations
    private String defaultOutputFile = null;
    public String getDefaultOutputFile() { return defaultOutputFile; }
    public void setDefaultOutputFile(String path) { this.defaultOutputFile = path; }
    public String getConsoleText() { return consoleText; }
    public String getFilterMode() { return filterMode; }
    public boolean isEnableStatus() { return enableStatus; }
    public boolean isTaskRunning() { return taskRunning; }
    public void setConsoleText(String text) { this.consoleText = text; }
    public void setFilterMode(String mode) { this.filterMode = mode; }
    public void setEnableStatus(boolean enabled) { this.enableStatus = enabled; }
    public void setTaskRunning(boolean running) { this.taskRunning = running; }
    public void appendConsoleText(String text) { this.consoleText += text; }
    public void clearConsole() { this.consoleText = ""; }
    @Override
    protected void onCleared() {
        super.onCleared();
    }
}
