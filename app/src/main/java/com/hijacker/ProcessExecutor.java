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

import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

/**
 * Secure process execution utility using ProcessBuilder.
 * Replaces unsafe Runtime.exec() calls to prevent command injection.
 */
public class ProcessExecutor {
    private static final String TAG = "HIJACKER/ProcessExec";
    private static final long DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes

    /**
     * Execute a command with ProcessBuilder
     * @param command Command and arguments as separate strings
     * @return Process object
     * @throws IOException if execution fails
     */
    public static Process execute(String... command) throws IOException {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }

        // Validate command arguments
        for (String arg : command) {
            if (arg == null) {
                throw new IllegalArgumentException("Command argument cannot be null");
            }
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true); // Merge stderr into stdout

        if (MainActivity.debug) {
            Log.d(TAG, "Executing: " + Arrays.toString(command));
        }

        return pb.start();
    }

    /**
     * Execute a command with ProcessBuilder and custom environment
     * @param command Command and arguments
     * @param environment Environment variables to set
     * @return Process object
     * @throws IOException if execution fails
     */
    public static Process execute(String[] command, Map<String, String> environment) throws IOException {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        // Add custom environment variables
        if (environment != null && !environment.isEmpty()) {
            Map<String, String> env = pb.environment();
            env.putAll(environment);
        }

        if (MainActivity.debug) {
            Log.d(TAG, "Executing with env: " + Arrays.toString(command));
        }

        return pb.start();
    }

    /**
     * Execute a command in a specific working directory
     * @param command Command and arguments
     * @param workingDirectory Working directory for the command
     * @return Process object
     * @throws IOException if execution fails
     */
    public static Process execute(String[] command, File workingDirectory) throws IOException {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        if (workingDirectory != null && workingDirectory.exists() && workingDirectory.isDirectory()) {
            pb.directory(workingDirectory);
        }

        if (MainActivity.debug) {
            Log.d(TAG, "Executing in dir " + workingDirectory + ": " + Arrays.toString(command));
        }

        return pb.start();
    }

    /**
     * Execute a command as root using 'su -c'
     * @param command Command and arguments (will be executed via su)
     * @return Process object
     * @throws IOException if execution fails
     */
    public static Process executeAsRoot(String... command) throws IOException {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }

        // Build command as: su -c "command arg1 arg2 ..."
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add("su");
        fullCommand.add("-c");

        // Join command arguments into a single string for su -c
        StringBuilder cmdBuilder = new StringBuilder();
        for (int i = 0; i < command.length; i++) {
            if (i > 0) cmdBuilder.append(" ");
            // Quote arguments that contain spaces
            if (command[i].contains(" ")) {
                cmdBuilder.append("\"").append(command[i]).append("\"");
            } else {
                cmdBuilder.append(command[i]);
            }
        }
        fullCommand.add(cmdBuilder.toString());

        if (MainActivity.debug) {
            Log.d(TAG, "Executing as root: " + cmdBuilder);
        }

        return execute(fullCommand.toArray(new String[0]));
    }

    /**
     * Execute a command as root with environment variables
     *
     * @param command Command and arguments
     * @param envVars Environment variables (e.g., "PATH=/bin:/sbin", "LD_PRELOAD=/lib/lib.so")
     * @throws IOException if execution fails
     */
    public static void executeAsRootWithEnv(String[] command, String... envVars) throws IOException {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }

        // Build command as: su -c "export VAR=value && command args"
        StringBuilder cmdBuilder = getStringBuilder(command, envVars);

        String[] fullCommand = {"su", "-c", cmdBuilder.toString()};

        if (MainActivity.debug) {
            Log.d(TAG, "Executing as root with env: " + cmdBuilder);
        }

        execute(fullCommand);
    }

    private static StringBuilder getStringBuilder(String[] command, String[] envVars) {
        StringBuilder cmdBuilder = new StringBuilder();

        // Add environment variables
        if (envVars != null) {
            for (String envVar : envVars) {
                cmdBuilder.append("export ").append(envVar).append(" && ");
            }
        }

        // Add command
        for (int i = 0; i < command.length; i++) {
            if (i > 0) cmdBuilder.append(" ");
            if (command[i].contains(" ")) {
                cmdBuilder.append("\"").append(command[i]).append("\"");
            } else {
                cmdBuilder.append(command[i]);
            }
        }
        return cmdBuilder;
    }

    /**
     * Execute a command with timeout
     * @param timeoutSeconds Timeout in seconds
     * @param command Command and arguments
     * @return Process exit code
     * @throws IOException if execution fails
     * @throws TimeoutException if command times out
     * @throws InterruptedException if interrupted
     */
    public static int executeWithTimeout(long timeoutSeconds, String... command)
            throws IOException, TimeoutException, InterruptedException {
        Process process = execute(command);
        try {
            return waitForProcess(process, timeoutSeconds);
        } finally {
            // Ensure process resources are cleaned up
            destroyProcess(process);
        }
    }

    /**
     * Execute a command and capture output
     * @param command Command and arguments
     * @return Command output as string
     * @throws IOException if execution fails
     */
    public static String executeAndCapture(String... command) throws IOException {
        Process process = execute(command);

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        return output.toString();
    }

    /**
     * Execute a command as root and capture output
     * @param command Command and arguments
     * @return Command output as string
     * @throws IOException if execution fails
     */
    public static String executeAsRootAndCapture(String... command) throws IOException {
        Process process = executeAsRoot(command);

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        return output.toString();
    }

    /**
     * Check if a command exists
     * @param command Command name
     * @return true if command exists
     */
    public static boolean commandExists(String command) {
        try {
            Process process = execute("which", command);
            try {
                int exit = waitForProcess(process, 5);
                return exit == 0;
            } finally {
                destroyProcess(process);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Kill a process by name
     * @param processName Process name to kill
     * @return true if kill command executed successfully
     */
    public static boolean killProcess(String processName) {
        try {
            Process process = executeAsRoot("killall", processName);
            int exitCode = waitForProcess(process, 5);
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to kill process: " + processName, e);
            return false;
        }
    }

    /**
     * Kill a process by PID
     * @param pid Process ID
     * @return true if kill command executed successfully
     */
    public static boolean killProcessByPid(int pid) {
        try {
            Process process = executeAsRoot("kill", "-9", String.valueOf(pid));
            int exitCode = waitForProcess(process, 5);
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to kill process PID: " + pid, e);
            return false;
        }
    }

    /**
     * Execute a shell script
     * @param scriptPath Path to shell script
     * @param args Script arguments
     * @return Process object
     * @throws IOException if execution fails
     */
    public static Process executeScript(String scriptPath, String... args) throws IOException {
        if (!InputValidator.isValidFilePath(scriptPath)) {
            throw new IllegalArgumentException("Invalid script path");
        }

        File scriptFile = new File(scriptPath);
        if (!scriptFile.exists() || !scriptFile.canExecute()) {
            throw new IOException("Script does not exist or is not executable: " + scriptPath);
        }

        List<String> command = new ArrayList<>();
        command.add("sh");
        command.add(scriptPath);
        if (args != null) {
            command.addAll(Arrays.asList(args));
        }

        return execute(command.toArray(new String[0]));
    }

    /**
     * Safely destroy a process
     * @param process Process to destroy
     */
    public static void destroyProcess(Process process) {
        if (process == null) return;

        try {
            try {
                process.exitValue(); // Check if the process has already terminated
            } catch (IllegalThreadStateException e) {
                process.destroy();
                boolean destroyed = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    destroyed = process.waitFor(2, TimeUnit.SECONDS);
                }
                if (!destroyed) {
                    process.destroy(); // Fallback for forcibly destroying the process
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try {
                process.exitValue();
            } catch (IllegalThreadStateException ex) {
                process.destroy(); // Fallback for forcibly destroying the process
            }
        }
    }

    /**
     * Read process output safely
     * @param process Process to read from
     * @return Output as string
     * @throws IOException if reading fails
     */
    public static String readOutput(Process process) throws IOException {
        if (process == null) {
            throw new IllegalArgumentException("Process cannot be null");
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        return output.toString();
    }

    /**
     * Wait for process with timeout
     * @param process Process to wait for
     * @param timeoutSeconds Timeout in seconds
     * @return Exit code
     * @throws TimeoutException if timeout occurs
     * @throws InterruptedException if interrupted
     */
    public static int waitForProcess(Process process, long timeoutSeconds)
            throws TimeoutException, InterruptedException {
        if (process == null) {
            throw new IllegalArgumentException("Process cannot be null");
        }
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> new Thread(r, "waitForProcess"));
        Future<Integer> fut = null;
        try {
            fut = exec.submit(() -> {
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return process.exitValue();
            });

            try {
                return fut.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException ee) {
                throw new RuntimeException("Waiting for process failed", ee.getCause());
            } catch (java.util.concurrent.TimeoutException te) {
                destroyProcess(process);
                throw new TimeoutException("Process timed out after " + timeoutSeconds + " seconds");
            }
        } finally {
            try { if (fut != null) fut.cancel(true); } catch (Exception ignored) {}
            try { exec.shutdownNow(); } catch (Exception ignored) {}
        }
    }
}
