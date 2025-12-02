package com.hijacker;
import androidx.lifecycle.ViewModel;
/**
 * ViewModel to hold CrackFragment state across configuration changes.
 */
public class CrackViewModel extends ViewModel {
    public static final int WPA = 2;
    public static final int WEP = 1;
    private String consoleText = "";
    private String capfileText = null;
    private String wordlistText = null;
    private int securityChecked = -1;
    private int wepChecked = -1;
    private boolean taskRunning = false;
    private int speedTestResult = -1;
    public String getConsoleText() { return consoleText; }
    public String getCapfileText() { return capfileText; }
    public String getWordlistText() { return wordlistText; }
    public int getSecurityChecked() { return securityChecked; }
    public int getWepChecked() { return wepChecked; }
    public boolean isTaskRunning() { return taskRunning; }
    public int getSpeedTestResult() { return speedTestResult; }
    public void setConsoleText(String text) { this.consoleText = text; }
    public void setCapfileText(String text) { this.capfileText = text; }
    public void setWordlistText(String text) { this.wordlistText = text; }
    public void setSecurityChecked(int checked) { this.securityChecked = checked; }
    public void setWepChecked(int checked) { this.wepChecked = checked; }
    public void setTaskRunning(boolean running) { this.taskRunning = running; }
    public void setSpeedTestResult(int result) { this.speedTestResult = result; }
    public void appendConsoleText(String text) { this.consoleText += text; }
    public void clearConsole() { this.consoleText = ""; }
    @Override
    protected void onCleared() {
        super.onCleared();
    }
}
