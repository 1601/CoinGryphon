package com.example.coingryphon.serial;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GryphonSerialManager handles the RS232 communication with the Gryphon device.
 * Communication parameters: 9600 baud rate, 1 start bit, 8 data bits, Mark parity bit, 1 stop bit
 * 
 * This implementation uses direct serial port access (/dev/ttyS5).
 */
public class GryphonSerialManager {
    private static final String TAG = "GryphonSerialManager";
    
    // Serial port path
    private static final String SERIAL_PORT_PATH = "/dev/ttyS4"; //
    
    // Communication parameters
    private static final int BAUD_RATE = 9600;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = 1;
    private static final int PARITY_MARK = 3; // Mark parity bit (typically value 3)
    private static final int PARITY_ODD = 1;  // Alternative: some devices use odd parity (1)
    
    // Singleton instance
    private static GryphonSerialManager instance;
    
    private final Context context;
    private final ExecutorService executor;
    
    // Serial port connection
    private Object serialPort; // This will hold the SerialPort instance
    private InputStream inputStream;
    private OutputStream outputStream;
    private SerialPortReader portReader;
    private boolean readerRunning = false;
    
    private boolean isConnected = false;
    private GryphonSerialListener listener;
    
    /**
     * Interface for serial communication callbacks
     */
    public interface GryphonSerialListener {
        void onReceiveData(byte[] data);
        void onConnectionEstablished();
        void onConnectionLost();
        void onError(Exception e);
    }

    private GryphonSerialManager(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static synchronized GryphonSerialManager getInstance(Context context) {
        if (instance == null) {
            instance = new GryphonSerialManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public void setListener(GryphonSerialListener listener) {
        this.listener = listener;
    }

    /**
     * Connect to the serial port device (/dev/ttyS5)
     */
    public boolean connectToDevice() {
        return openConnection();
    }
    
    /**
     * Open the serial connection with the specified parameters
     */
    private boolean openConnection() {
        try {
            // Create the serial port file
            File device = new File(SERIAL_PORT_PATH);
            
            if (!device.exists()) {
                Log.e(TAG, "Serial port " + SERIAL_PORT_PATH + " does not exist");
                return false;
            }
            
            // Try to set permissions
            try {
                Process process = Runtime.getRuntime().exec("chmod 666 " + SERIAL_PORT_PATH);
                process.waitFor();
                Log.d(TAG, "Set permissions for " + SERIAL_PORT_PATH);
            } catch (Exception e) {
                Log.w(TAG, "Could not set permissions: " + e.getMessage());
                // Continue anyway - permissions might already be correct
            }
            
            // Try to configure serial port parameters with stty
            try {
                // Try multiple configurations for maximum compatibility
                
                // Mark parity
                String sttyCommand1 = "stty -F " + SERIAL_PORT_PATH + " " + 
                        BAUD_RATE + " cs8 -cstopb parenb parmrk -crtscts -ixon -ixoff raw";
                Process process1 = Runtime.getRuntime().exec(sttyCommand1);
                process1.waitFor();
                
                // Odd parity (some devices interpret mark as odd)
                String sttyCommand2 = "stty -F " + SERIAL_PORT_PATH + " " + 
                        BAUD_RATE + " cs8 -cstopb parenb parodd -crtscts -ixon -ixoff raw";
                Process process2 = Runtime.getRuntime().exec(sttyCommand2);
                process2.waitFor();
                
                // No parity (fallback)
                String sttyCommand3 = "stty -F " + SERIAL_PORT_PATH + " " + 
                        BAUD_RATE + " cs8 -cstopb -parenb -crtscts -ixon -ixoff raw";
                Process process3 = Runtime.getRuntime().exec(sttyCommand3);
                process3.waitFor();
                
                Log.d(TAG, "Configured serial port parameters");
            } catch (Exception e) {
                Log.w(TAG, "Could not configure serial port: " + e.getMessage());
                // Continue anyway - parameters might be set correctly at the system level
            }
            
            // Try to use the SerialPort API if available
            try {
                // Load the SerialPort class
                Class<?> SerialPortClass = Class.forName("android_serialport_api.SerialPort");
                
                // Get constructor
                java.lang.reflect.Constructor<?> constructor = SerialPortClass.getConstructor(
                        File.class, int.class, int.class);
                
                // Create instance
                int flags = (DATA_BITS | (STOP_BITS << 8) | (PARITY_MARK << 16));
                serialPort = constructor.newInstance(device, BAUD_RATE, flags);
                
                // Get streams
                Method getInputStreamMethod = SerialPortClass.getMethod("getInputStream");
                Method getOutputStreamMethod = SerialPortClass.getMethod("getOutputStream");
                
                inputStream = (InputStream) getInputStreamMethod.invoke(serialPort);
                outputStream = (OutputStream) getOutputStreamMethod.invoke(serialPort);
                
                Log.d(TAG, "Opened serial port with SerialPort API");
            } catch (Exception e) {
                Log.w(TAG, "SerialPort API not available, using direct file I/O: " + e.getMessage());
                
                // Fall back to direct file I/O
                try {
                    inputStream = new FileInputStream(device);
                    outputStream = new FileOutputStream(device);
                    Log.d(TAG, "Opened serial port with direct file I/O");
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to open serial port with direct file I/O", ex);
                    return false;
                }
            }
            
            // Start reader thread
            portReader = new SerialPortReader();
            readerRunning = true;
            executor.submit(portReader);
            
            isConnected = true;
            if (listener != null) {
                listener.onConnectionEstablished();
            }
            
            Log.d(TAG, "Serial connection established");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error opening serial connection", e);
            closeConnection();
            if (listener != null) {
                listener.onError(e);
            }
            return false;
        }
    }
    
    /**
     * Close the serial connection and cleanup resources
     */
    public void closeConnection() {
        isConnected = false;
        readerRunning = false;
        
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing input stream", e);
        }
        
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing output stream", e);
        }
        
        // Close the SerialPort instance if available
        if (serialPort != null) {
            try {
                Method closeMethod = serialPort.getClass().getMethod("close");
                closeMethod.invoke(serialPort);
            } catch (Exception e) {
                Log.e(TAG, "Error closing serial port", e);
            }
            serialPort = null;
        }
        
        Log.d(TAG, "Serial connection closed");
    }

    /**
     * Check if the device is connected
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * Send data to the serial port
     * @param data Data to send
     * @return true if sent successfully
     */
    public boolean sendData(byte[] data) {
        if (!isConnected || outputStream == null) {
            Log.e(TAG, "Cannot send data - port not open");
            return false;
        }

        Log.d(TAG, "Sending data: " + bytesToHexString(data));
        boolean success = false;
        
        try {
            // First clear any pending input
            try {
                if (inputStream != null && inputStream.available() > 0) {
                    byte[] buffer = new byte[inputStream.available()];
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead > 0) {
                        Log.d(TAG, "Cleared input buffer: " + bytesToHexString(buffer));
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to clear input buffer: " + e.getMessage());
            }
            
            // Method 1: Terminal command (echo) - working based on logs
            try {
                StringBuilder hexString = new StringBuilder();
                for (byte b : data) {
                    hexString.append(String.format("\\\\x%02X", b));
                }
                
                String command = "echo -n -e '" + hexString + "' > " + SERIAL_PORT_PATH;
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                int result = process.waitFor();
                
                if (result == 0) {
                    Log.d(TAG, "Data sent via terminal command");
                    success = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Terminal command failed: " + e.getMessage());
                // Continue to next method
            }
            
            // Method 2: Java OutputStream with byte-by-byte and delays
            if (!success) {
                try {
                    // Add slightly longer delay between bytes for more reliable communication
                    for (byte b : data) {
                        outputStream.write(b);
                        outputStream.flush();
                        Thread.sleep(30); // Increased delay for hardware to process
                    }
                    // Add final delay and flush to ensure all data is sent
                    Thread.sleep(20);
                    outputStream.flush();
                    Log.d(TAG, "Data sent via OutputStream (byte by byte)");
                    success = true;
                } catch (Exception e) {
                    Log.w(TAG, "OutputStream byte-by-byte send failed: " + e.getMessage());
                    // Continue to next method
                }
            }
            
            // Method 3: Java OutputStream all at once (last resort)
            if (!success) {
                try {
                    outputStream.write(data);
                    outputStream.flush();
                    // Add a small delay after sending for better reliability
                    Thread.sleep(10);
                    Log.d(TAG, "Data sent via OutputStream (all at once)");
                    success = true;
                } catch (Exception e) {
                    Log.e(TAG, "OutputStream all-at-once send failed: " + e.getMessage());
                    // No more methods to try
                }
            }
            
            // Report failure if all methods failed
            if (!success) {
                Log.e(TAG, "Failed to send data through any method");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error sending data", e);
            if (listener != null) {
                listener.onError(e);
            }
            return false;
        }
    }
    
    /**
     * Convert byte array to hex string for debugging
     */
    public static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
    
    /**
     * Serial port reader thread
     */
    private class SerialPortReader implements Runnable {
        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int len;
            
            while (readerRunning) {
                try {
                    if (inputStream != null && inputStream.available() > 0) {
                        len = inputStream.read(buffer);
                        if (len > 0) {
                            byte[] data = Arrays.copyOf(buffer, len);
                            Log.d(TAG, "Data received: " + bytesToHexString(data));
                            
                            if (listener != null) {
                                listener.onReceiveData(data);
                            }
                        }
                    }
                    
                    // Sleep to avoid high CPU usage
                    Thread.sleep(10);
                } catch (Exception e) {
                    Log.e(TAG, "Error reading from serial port", e);
                    if (listener != null) {
                        listener.onError(e);
                        listener.onConnectionLost();
                    }
                    isConnected = false;
                    readerRunning = false;
                    break;
                }
            }
        }
    }
    
    /**
     * Clean up resources when the app is closing
     */
    public void destroy() {
        closeConnection();
        executor.shutdownNow();
    }
}