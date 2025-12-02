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

import java.io.File;
import java.util.regex.Pattern;

/**
 * Utility class for input validation to prevent command injection and other security issues
 */
public class InputValidator {

    // MAC address patterns
    private static final Pattern MAC_COLON_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");
    private static final Pattern MAC_DASH_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}-){5}[0-9A-Fa-f]{2}$");

    // SSID validation (max 32 bytes for WiFi)
    private static final int MAX_SSID_LENGTH = 32;

    // Channel validation (WiFi channels 1-14 for 2.4GHz, 36-165 for 5GHz)
    private static final int MIN_CHANNEL_24GHZ = 1;
    private static final int MAX_CHANNEL_24GHZ = 14;
    private static final int MIN_CHANNEL_5GHZ = 36;
    private static final int MAX_CHANNEL_5GHZ = 165;

    /**
     * Validates MAC address format
     * @param mac MAC address string
     * @return true if valid MAC address format
     */
    public static boolean isValidMac(String mac) {
        if (mac == null || mac.trim().isEmpty()) {
            return false;
        }
        String trimmed = mac.trim();
        return MAC_COLON_PATTERN.matcher(trimmed).matches() ||
               MAC_DASH_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Sanitizes MAC address by converting to standard format (uppercase with colons)
     * @param mac MAC address string
     * @return sanitized MAC address
     * @throws IllegalArgumentException if MAC address is invalid
     */
    public static String sanitizeMac(String mac) {
        if (!isValidMac(mac)) {
            throw new IllegalArgumentException("Invalid MAC address format: " + mac);
        }
        return mac.trim().toUpperCase().replace("-", ":");
    }

    /**
     * Validates WiFi channel number
     * @param channel Channel number
     * @return true if valid channel
     */
    public static boolean isValidChannel(int channel) {
        return (channel >= MIN_CHANNEL_24GHZ && channel <= MAX_CHANNEL_24GHZ) ||
               (channel >= MIN_CHANNEL_5GHZ && channel <= MAX_CHANNEL_5GHZ);
    }

    /**
     * Validates SSID
     * @param ssid SSID string
     * @return true if valid SSID
     */
    public static boolean isValidSSID(String ssid) {
        if (ssid == null) {
            return false;
        }
        byte[] bytes = ssid.getBytes();
        return bytes.length > 0 && bytes.length <= MAX_SSID_LENGTH;
    }

    /**
     * Validates file path for security (prevents path traversal)
     * @param path File path
     * @return true if path appears safe
     */
    public static boolean isValidFilePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }

        // Check for path traversal attempts
        if (path.contains("..") || path.contains("~")) {
            return false;
        }

        // Check for null bytes
        return !path.contains("\0");
    }

    /**
     * Validates that file exists and is readable
     * @param path File path
     * @return true if file exists and is readable
     */
    public static boolean isValidAndReadableFile(String path) {
        if (!isValidFilePath(path)) {
            return false;
        }

        try {
            File file = new File(path);
            return file.exists() && file.isFile() && file.canRead();
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Sanitizes command argument by escaping shell metacharacters
     * @param arg Command argument
     * @return sanitized argument
     */
    public static String sanitizeCommandArg(String arg) {
        if (arg == null) {
            return "";
        }

        // Remove or escape potentially dangerous characters
        // This is a basic implementation - for production use a proper escaping library
        return arg.replace(";", "")
                  .replace("|", "")
                  .replace("&", "")
                  .replace("`", "")
                  .replace("$", "")
                  .replace("(", "")
                  .replace(")", "")
                  .replace("<", "")
                  .replace(">", "")
                  .replace("\n", "")
                  .replace("\r", "")
                  .replace("\0", "");
    }

    /**
     * Validates interface name (e.g., wlan0, wlan1)
     * @param iface Interface name
     * @return true if valid interface name
     */
    public static boolean isValidInterface(String iface) {
        if (iface == null || iface.trim().isEmpty()) {
            return false;
        }

        // Only allow alphanumeric and specific characters
        return iface.matches("^[a-zA-Z0-9_-]+$");
    }

    /**
     * Validates PIN (for WPS attacks)
     * @param pin PIN string
     * @return true if valid PIN format
     */
    public static boolean isValidPin(String pin) {
        if (pin == null) {
            return false;
        }

        // WPS PIN is 8 digits
        return pin.matches("^\\d{8}$");
    }

    /**
     * Validates delay value in seconds
     * @param delay Delay value
     * @return true if valid delay (0-3600 seconds)
     */
    public static boolean isValidDelay(int delay) {
        return delay >= 0 && delay <= 3600;
    }

    /**
     * Validates power level
     * @param power Power level (0-100)
     * @return true if valid power level
     */
    public static boolean isValidPowerLevel(int power) {
        return power >= 0 && power <= 100;
    }
}
