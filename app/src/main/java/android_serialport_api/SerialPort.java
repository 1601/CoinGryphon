/*
 * Copyright 2009 Cedric Priscal
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */

package android_serialport_api;

import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * SerialPort class for Android
 * Provides access to the serial port with the specified parameters
 * Uses JNI to configure the serial port parameters
 */
public class SerialPort {

    private static final String TAG = "SerialPort";

    /*
     * Serial Port Parameters
     */
    public static final int DATABITS_5 = 5;
    public static final int DATABITS_6 = 6;
    public static final int DATABITS_7 = 7;
    public static final int DATABITS_8 = 8;

    public static final int STOPBITS_1 = 1;
    public static final int STOPBITS_2 = 2;
    public static final int STOPBITS_15 = 3; // 1.5 stop bits

    public static final int PARITY_NONE = 0;
    public static final int PARITY_ODD = 1;
    public static final int PARITY_EVEN = 2;
    public static final int PARITY_MARK = 3;
    public static final int PARITY_SPACE = 4;

    private FileDescriptor mFd;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;

    /**
     * Set up the SerialPort class
     * Note: We are no longer trying to load the native library as we're using a pure Java implementation
     */
    static {
        // We're using a pure Java implementation, no need to load native libraries
        Log.i(TAG, "Using pure Java implementation for serial port access");
    }

    /**
     * Pure Java implementation for serial port access without relying on native code
     * This completely replaces the native methods to avoid JNI errors
     */
    private static FileDescriptor open(String path, int baudrate, int flags) throws IOException {
        // Create a direct file descriptor using standard Java file I/O
        File device = new File(path);
        if (!device.exists()) {
            throw new IOException("Device file does not exist: " + path);
        }
        
        // Configure the port using stty commands before opening it
        try {
            configureSerialPort(path, baudrate, flags);
        } catch (Exception e) {
            Log.w(TAG, "Failed to configure serial port with stty: " + e.getMessage());
            // Continue anyway as basic I/O might still work
        }
        
        // Open the file and get its file descriptor
        FileInputStream fis = new FileInputStream(device);
        FileOutputStream fos = new FileOutputStream(device);
        return fis.getFD();
    }
    
    private static void close(FileDescriptor fd) {
        // Nothing to do in Java implementation as streams will be closed separately
    }
    
    /**
     * Configure the serial port parameters using system commands
     */
    private static void configureSerialPort(String path, int baudrate, int flags) throws Exception {
        int dataBits = flags & 0xFF;
        int stopBits = (flags >> 8) & 0xFF;
        int parity = (flags >> 16) & 0xFF;
        
        String parityStr;
        switch (parity) {
            case PARITY_NONE: parityStr = "-parenb"; break;
            case PARITY_ODD: parityStr = "parenb parodd"; break;
            case PARITY_EVEN: parityStr = "parenb -parodd"; break;
            case PARITY_MARK: parityStr = "parenb parmrk"; break;
            case PARITY_SPACE: parityStr = "parenb"; break; // Approximation
            default: parityStr = "-parenb"; break;
        }
        
        String stopBitsStr = stopBits == STOPBITS_1 ? "-cstopb" : "cstopb";
        String dataBitsStr = "cs" + dataBits;
        
        String[] commands = {
            // Mark parity (typical for coin acceptors)
            "stty -F " + path + " " + baudrate + " " + dataBitsStr + " " + stopBitsStr + 
                " parenb parmrk -crtscts -ixon -ixoff raw",
            // Odd parity
            "stty -F " + path + " " + baudrate + " " + dataBitsStr + " " + stopBitsStr + 
                " parenb parodd -crtscts -ixon -ixoff raw",
            // Plain configuration
            "stty -F " + path + " " + baudrate + " " + dataBitsStr + " " + stopBitsStr + 
                " " + parityStr + " -crtscts -ixon -ixoff raw"
        };
        
        // Try multiple configuration commands for maximum compatibility
        Exception lastException = null;
        for (String cmd : commands) {
            try {
                Process process = Runtime.getRuntime().exec(cmd);
                int result = process.waitFor();
                if (result == 0) {
                    Log.d(TAG, "Successfully configured serial port with: " + cmd);
                    return; // Success
                }
            } catch (Exception e) {
                lastException = e;
            }
        }
        
        if (lastException != null) {
            throw lastException;
        }
    }

    /**
     * Create a new serial port using the specified parameters
     * 
     * @param device The serial port device file
     * @param baudrate The baud rate to use
     * @param flags The configuration flags (data bits, stop bits, parity)
     * @throws SecurityException
     * @throws IOException
     */
    public SerialPort(File device, int baudrate, int flags) throws SecurityException, IOException {
        // Check if the device exists and is accessible
        if (!device.exists()) {
            throw new IOException("Device " + device.getAbsolutePath() + " does not exist");
        }
        if (!device.canRead() || !device.canWrite()) {
            try {
                // Try to chmod the device
                Process su = Runtime.getRuntime().exec("/system/bin/su");
                String cmd = "chmod 666 " + device.getAbsolutePath() + "\n" + "exit\n";
                su.getOutputStream().write(cmd.getBytes());
                if ((su.waitFor() != 0) || !device.canRead() || !device.canWrite()) {
                    throw new SecurityException("Failed to get read/write permission to " + device.getAbsolutePath());
                }
            } catch (Exception e) {
                throw new SecurityException("Failed to get read/write permission to " + device.getAbsolutePath());
            }
        }

        try {
            // Use our pure Java implementation
            mFd = open(device.getAbsolutePath(), baudrate, flags);
            Log.d(TAG, "Serial port opened with pure Java implementation");
        } catch (IOException e) {
            Log.e(TAG, "Failed to open serial port: " + e.getMessage());
            throw e;
        }

        // Even in fallback mode, we still need to set up the streams
        mFileInputStream = new FileInputStream(device);
        mFileOutputStream = new FileOutputStream(device);

        Log.i(TAG, "Serial port opened: " + device.getAbsolutePath() + 
              " with baudrate " + baudrate + " and flags " + flags);
    }

    /**
     * Get the input stream for reading from the serial port
     */
    public InputStream getInputStream() {
        return mFileInputStream;
    }

    /**
     * Get the output stream for writing to the serial port
     */
    public OutputStream getOutputStream() {
        return mFileOutputStream;
    }

    /**
     * Close the serial port
     */
    public void close() {
        // No need to call native close method as we're using pure Java implementation
        mFd = null;
        
        try {
            if (mFileInputStream != null) {
                mFileInputStream.close();
                mFileInputStream = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to close input stream", e);
        }
        try {
            if (mFileOutputStream != null) {
                mFileOutputStream.close();
                mFileOutputStream = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to close output stream", e);
        }
        Log.i(TAG, "Serial port closed");
    }
}
