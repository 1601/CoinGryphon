package com.example.coingryphon.serial;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GryphonSerialManager handles the RS232 communication with the Gryphon device.
 * Communication parameters: 9600 baud rate, 1 start bit, 8 data bits, Mark parity bit, 1 stop bit
 */
public class GryphonSerialManager {
    private static final String TAG = "GryphonSerialManager";
    private static final String ACTION_USB_PERMISSION = "com.example.coingryphon.USB_PERMISSION";
    
    // Communication parameters
    private static final int BAUD_RATE = 9600;
    private static final int DATA_BITS = UsbSerialPort.DATABITS_8;
    private static final int STOP_BITS = UsbSerialPort.STOPBITS_1;
    private static final int PARITY = UsbSerialPort.PARITY_MARK; // Mark parity bit
    private static final int TIMEOUT = 1000; // Read/write timeout in ms
    
    // Singleton instance
    private static GryphonSerialManager instance;
    
    private final Context context;
    private final UsbManager usbManager;
    private final ExecutorService executor;
    
    private UsbSerialPort serialPort;
    private SerialInputOutputManager ioManager;
    private UsbDevice usbDevice;
    private UsbSerialDriver driver;
    private UsbDeviceConnection connection;
    
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
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.executor = Executors.newSingleThreadExecutor();
        registerUsbReceiver();
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
     * Register the USB permission broadcast receiver
     */
    private void registerUsbReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        
        context.registerReceiver(usbReceiver, filter);
    }

    /**
     * Scan for available USB serial devices and request permission
     */
    public boolean connectToDevice() {
        // Find available drivers
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "No USB serial devices found");
            return false;
        }

        // Open first available driver
        driver = availableDrivers.get(0);
        usbDevice = driver.getDevice();

        // Request permission if needed
        if (!usbManager.hasPermission(usbDevice)) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(usbDevice, permissionIntent);
            return false; // Connection will be completed in broadcast receiver
        } else {
            return openConnection();
        }
    }
    
    /**
     * Open the serial connection with the specified parameters
     */
    private boolean openConnection() {
        try {
            connection = usbManager.openDevice(usbDevice);
            if (connection == null) {
                Log.e(TAG, "Failed to open USB device connection");
                return false;
            }
            
            serialPort = driver.getPorts().get(0); // Assuming single-port device
            serialPort.open(connection);
            serialPort.setParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY);
            
            // Start the I/O manager to handle incoming data
            ioManager = new SerialInputOutputManager(serialPort, serialIOListener);
            executor.submit(ioManager);
            
            isConnected = true;
            if (listener != null) {
                listener.onConnectionEstablished();
            }
            
            Log.d(TAG, "Serial connection established with Gryphon");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error opening serial connection", e);
            if (listener != null) {
                listener.onError(e);
            }
            closeConnection();
            return false;
        }
    }
    
    /**
     * Close the serial connection and cleanup resources
     */
    public void closeConnection() {
        isConnected = false;
        
        if (ioManager != null) {
            ioManager.stop();
            ioManager = null;
        }
        
        if (serialPort != null) {
            try {
                serialPort.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing serial port", e);
            }
            serialPort = null;
        }
        
        if (connection != null) {
            connection = null;
        }
        
        Log.d(TAG, "Serial connection closed");
    }

    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * Send data to the Gryphon device
     * @param data byte array to send
     * @return true if data was sent successfully
     */
    public boolean sendData(byte[] data) {
        if (!isConnected || serialPort == null) {
            Log.e(TAG, "Cannot send data. Device not connected.");
            return false;
        }
        
        try {
            serialPort.write(data, TIMEOUT);
            Log.d(TAG, "Data sent: " + bytesToHexString(data));
            return true;
        } catch (IOException e) {
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
     * Handle incoming serial data
     */
    private final SerialInputOutputManager.Listener serialIOListener = new SerialInputOutputManager.Listener() {
        @Override
        public void onNewData(byte[] data) {
            Log.d(TAG, "Data received: " + bytesToHexString(data));
            if (listener != null) {
                listener.onReceiveData(data);
            }
        }

        @Override
        public void onRunError(Exception e) {
            Log.e(TAG, "Serial communication error", e);
            if (listener != null) {
                listener.onError(e);
                listener.onConnectionLost();
            }
            isConnected = false;
        }
    };
    
    /**
     * USB permission and device attachment/detachment broadcast receiver
     */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // Permission granted, open the connection
                            usbDevice = device;
                            openConnection();
                        }
                    } else {
                        Log.d(TAG, "USB permission denied for device " + device);
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                // Attempt to connect when new device is attached
                connectToDevice();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.equals(usbDevice)) {
                    // Close connection if the device is detached
                    if (listener != null) {
                        listener.onConnectionLost();
                    }
                    closeConnection();
                }
            }
        }
    };

    /**
     * Unregister receivers when the app is closing
     */
    public void destroy() {
        try {
            context.unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
        closeConnection();
        executor.shutdownNow();
    }
}
