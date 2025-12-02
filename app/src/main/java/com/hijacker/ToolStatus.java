package com.hijacker;

/**
 * Simple model representing a tool test row.
 */
public class ToolStatus {
    public String id; // e.g., "airodump"
    public String label;
    public int iconResId;
    public String statusText;

    public ToolStatus(String id, String label, int iconResId, String statusText){
        this.id = id;
        this.label = label;
        this.iconResId = iconResId;
        this.statusText = statusText;
    }
}
