package com.example.coingryphon.serial;

import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * GryphonProtocol handles specific command protocols for the Gryphon coin acceptor.
 * Implements the RS232 protocol with parameters: 9600 baud rate, 1 start bit, 8 data bits, 
 * Mark parity bit, 1 stop bit
 */
public class GryphonProtocol {
    private static final String TAG = "GryphonProtocol";
    
    // Command constants as byte arrays
    private static final byte[] CMD_POLL = new byte[] {0x0B, 0x0B};
    private static final byte[] CMD_RESET = new byte[] {0x08, 0x08};
    private static final byte[] CMD_SETUP = new byte[] {0x09, 0x09};
    private static final byte[] CMD_COIN_TYPE = new byte[] {0x0C, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, 0x08};
    private static final byte[] CMD_TUBE_STATUS = new byte[] {0x0A, 0x0A};
    private static final byte[] CMD_SELF_TEST = new byte[] {0x0F, 0x05, 0x14}; // Diagnostic status
    
    // Response constants
    private static final byte RESP_ACK = 0x00;
    private static final byte[] RESP_POLL_COIN = new byte[] {0x51, 0x0E, 0x5F};
    
    // Command timeout in milliseconds - configurable based on hardware response time
    private static final long COMMAND_TIMEOUT = 1000;
    
    // Maximum number of automatic retries for each command
    private static final int MAX_RETRIES = 3;
    
    private final GryphonSerialManager serialManager;
    private GryphonResponseListener responseListener;
    private CommandType pendingCommand = null;
    private long lastCommandTime = 0;
    
    // Retry counters for each command type
    private int resetRetryCount = 0;
    private int pollRetryCount = 0;
    private int setupRetryCount = 0;
    private int coinTypeRetryCount = 0;
    private int dispenseRetryCount = 0;
    private int tubeStatusRetryCount = 0;
    private int selfTestRetryCount = 0;
    
    // Coin denomination values from setup response
    private Map<Integer, Double> coinDenominations = new HashMap<>();
    
    // Scaling factor from setup response
    private int scaleFactor = 1;
    
    /**
     * Command types for the Gryphon coin acceptor
     */
    public enum CommandType {
        POLL,
        RESET,
        SETUP,
        COIN_TYPE,
        DISPENSE,
        TUBE_STATUS,
        SELF_TEST
    }
    
    /**
     * Response status from Gryphon commands
     */
    public enum ResponseStatus {
        SUCCESS,
        ERROR,
        COIN_ACCEPTED,
        TIMEOUT,
        DISPENSED,
        TUBE_FULL,
        DIAGNOSTIC_STATUS
    }
    
    /**
     * Interface for command response callbacks
     */
    public interface GryphonResponseListener {
        void onCommandResponse(CommandType command, ResponseStatus status, byte[] responseData);
    }
    
    /**
     * Constructor initializes with serial manager
     */
    public GryphonProtocol(GryphonSerialManager serialManager) {
        this.serialManager = serialManager;
        
        // Set up serial listener to process responses
        this.serialManager.setListener(new GryphonSerialManager.GryphonSerialListener() {
            @Override
            public void onReceiveData(byte[] data) {
                processResponse(data);
            }
            
            @Override
            public void onConnectionEstablished() {
                Log.d(TAG, "Connection established with Gryphon device");
            }
            
            @Override
            public void onConnectionLost() {
                Log.d(TAG, "Connection lost with Gryphon device");
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Serial communication error", e);
            }
        });
    }
    
    /**
     * Set the response listener
     */
    public void setResponseListener(GryphonResponseListener listener) {
        this.responseListener = listener;
    }
    
    /**
     * Process response data from the serial device
     */
    private void processResponse(byte[] data) {
        if (data == null || data.length == 0) {
            Log.w(TAG, "Received empty response data");
            return;
        }

        if (pendingCommand == null) {
            Log.d(TAG, "Received unexpected data: " + serialManager.bytesToHexString(data));
            return;
        }
        
        Log.d(TAG, "Processing response for command: " + pendingCommand + ", data: " + serialManager.bytesToHexString(data));
        
        CommandType currentCommand = pendingCommand;
        // Reset the pending command timer before processing
        pendingCommand = null;
        lastCommandTime = 0;
        
        // Process based on the saved command type
        switch (currentCommand) {
            case POLL:
                processPollResponse(data);
                break;
                
            case RESET:
                processResetResponse(data);
                break;
                
            case SETUP:
                processSetupResponse(data);
                break;
                
            case COIN_TYPE:
                processCoinTypeResponse(data);
                break;
                
            case DISPENSE:
                processDispenseResponse(data);
                break;
                
            case TUBE_STATUS:
                processTubeStatusResponse(data);
                break;
                
            case SELF_TEST:
                processSelfTestResponse(data);
                break;
                
            default:
                Log.w(TAG, "Unknown command type: " + currentCommand);
                break;
        }
    }
    
    /**
     * Check for command timeouts
     */
    public void checkTimeouts() {
        // For more aggressive retries, use a shorter timeout
        final long shortTimeout = COMMAND_TIMEOUT / 2;  // 500ms instead of 1000ms
        
        if (pendingCommand != null && (System.currentTimeMillis() - lastCommandTime) > shortTimeout) {
            Log.w(TAG, "Command timeout for: " + pendingCommand);
            CommandType timedOutCommand = pendingCommand;
            
            // Reset state before notification to avoid reentrance issues
            pendingCommand = null;
            lastCommandTime = 0;
            
            boolean maxRetriesReached = false;
            
            // Check if we've reached the maximum retries for this command
            switch (timedOutCommand) {
                case POLL:
                    pollRetryCount++;
                    if (pollRetryCount > MAX_RETRIES) {
                        maxRetriesReached = true;
                        pollRetryCount = 0;
                    }
                    break;
                case RESET:
                    resetRetryCount++;
                    if (resetRetryCount > MAX_RETRIES) {
                        maxRetriesReached = true;
                        resetRetryCount = 0;
                    }
                    break;
                case SETUP:
                    setupRetryCount++;
                    if (setupRetryCount > MAX_RETRIES) {
                        maxRetriesReached = true;
                        setupRetryCount = 0;
                    }
                    break;
                case COIN_TYPE:
                    coinTypeRetryCount++;
                    if (coinTypeRetryCount > MAX_RETRIES) {
                        maxRetriesReached = true;
                        coinTypeRetryCount = 0;
                    }
                    break;
                case DISPENSE:
                    dispenseRetryCount++;
                    if (dispenseRetryCount > MAX_RETRIES) {
                        maxRetriesReached = true;
                        dispenseRetryCount = 0;
                    }
                    break;
                case TUBE_STATUS:
                    tubeStatusRetryCount++;
                    if (tubeStatusRetryCount > MAX_RETRIES) {
                        maxRetriesReached = true;
                        tubeStatusRetryCount = 0;
                    }
                    break;
                case SELF_TEST:
                    selfTestRetryCount++;
                    if (selfTestRetryCount > MAX_RETRIES) {
                        maxRetriesReached = true;
                        selfTestRetryCount = 0;
                    }
                    break;
            }
            
            if (maxRetriesReached) {
                Log.d(TAG, "Max retries reached for command: " + timedOutCommand + ", trying SETUP command");
                
                // When max retries are reached, try the SETUP command as it's the most important
                // for resolving 'NO VMC communication' errors
                if (timedOutCommand != CommandType.SETUP) {
                    sendSetup();
                    return;
                }
                
                // If even SETUP command is failing, try sending a complete initialization sequence
                tryCompleteInitSequence();
                
                // Notify listener about the timeout
                if (responseListener != null) {
                    responseListener.onCommandResponse(
                        timedOutCommand,
                        ResponseStatus.TIMEOUT,
                        new byte[0]
                    );
                }
                return;
            }
            
            // First try sending a reset command to clear any potential error state
            if (timedOutCommand != CommandType.RESET) {
                try {
                    // Directly send data without going through the pendingCommand system
                    serialManager.sendData(CMD_RESET);
                    Thread.sleep(100);  // Wait a bit for the reset to take effect
                } catch (Exception e) {
                    Log.w(TAG, "Failed to send reset before retry: " + e.getMessage());
                }
            }
            
            // Try sending the command again for better reliability
            boolean resent = false;
            switch (timedOutCommand) {
                case POLL:
                    resent = sendPoll();
                    break;
                case RESET:
                    resent = sendReset();
                    break;
                case SETUP:
                    resent = sendSetup();
                    break;
                case COIN_TYPE:
                    resent = sendCoinType();
                    break;
                case TUBE_STATUS:
                    resent = sendTubeStatus();
                    break;
                case SELF_TEST:
                    resent = sendSelfTest();
                    break;
                // Don't auto-resend dispense commands as they might dispense twice
                case DISPENSE:
                    // Don't resend, just notify about timeout
                    resent = false;
                    break;
            }
            
            if (resent) {
                Log.d(TAG, "Automatically resent command: " + timedOutCommand);
                return; // Don't notify about timeout since we retried
            }
            
            // If we couldn't resend, notify listener about timeout
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    timedOutCommand,
                    ResponseStatus.TIMEOUT,
                    new byte[0]
                );
            }
        }
    }
    
    /**
     * Try a complete initialization sequence in order of importance
     * This is called when we've failed too many times with a specific command
     */
    private void tryCompleteInitSequence() {
        // Reset any pending command state
        pendingCommand = null;
        lastCommandTime = 0;
        
        Log.d(TAG, "Trying complete initialization sequence");
        
        try {
            // Send RESET command
            Log.d(TAG, "Init sequence: Sending RESET");
            serialManager.sendData(CMD_RESET);
            Thread.sleep(200);
            
            // Send SETUP command (most important for NO VMC communication)
            Log.d(TAG, "Init sequence: Sending SETUP");
            serialManager.sendData(CMD_SETUP);
            Thread.sleep(200);
            
            // Send COIN_TYPE command
            Log.d(TAG, "Init sequence: Sending COIN_TYPE");
            serialManager.sendData(CMD_COIN_TYPE);
            Thread.sleep(200);
            
            // Send POLL command
            Log.d(TAG, "Init sequence: Sending POLL");
            serialManager.sendData(CMD_POLL);
            
            Log.d(TAG, "Complete initialization sequence sent");
        } catch (Exception e) {
            Log.e(TAG, "Error sending initialization sequence: " + e.getMessage());
        }
    }
    
    /**
     * Send Poll command
     * write: 0B 0B
     * expected response: 00 or 51 0E 5F
     */
    public boolean sendPoll() {
        // Allow sending even if there's a pending command
        // This can help force-reset the communication with the device
        if (pendingCommand != null) {
            Log.d(TAG, "Forcing poll command while " + pendingCommand + " is pending");
            pendingCommand = null;  // Clear pending command state
        }
        
        pendingCommand = CommandType.POLL;
        lastCommandTime = System.currentTimeMillis();
        
        // Try to send the command
        boolean result = serialManager.sendData(CMD_POLL);
        
        // If it failed, try one more time after a short delay
        if (!result) {
            try {
                Thread.sleep(50);
                result = serialManager.sendData(CMD_POLL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        return result;
    }
    
    /**
     * Process Poll response
     */
    private void processPollResponse(byte[] data) {
        if (data.length == 1 && data[0] == RESP_ACK) {
            // Standard ACK response - no coin inserted
            Log.d(TAG, "Poll response: ACK (no coin)");
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.POLL,
                    ResponseStatus.SUCCESS,
                    data
                );
            }
        } else if (data.length == 3 && Arrays.equals(data, RESP_POLL_COIN)) {
            // Coin accepted response
            Log.d(TAG, "Poll response: COIN ACCEPTED");
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.POLL,
                    ResponseStatus.COIN_ACCEPTED,
                    data
                );
            }
        } else {
            // Unknown or error response
            Log.w(TAG, "Unexpected poll response: " + serialManager.bytesToHexString(data));
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.POLL,
                    ResponseStatus.ERROR,
                    data
                );
            }
        }
    }
    
    /**
     * Send Reset command
     * write: 08 08
     * expected response: 00
     */
    public boolean sendReset() {
        // Allow sending even if there's a pending command
        if (pendingCommand != null) {
            Log.d(TAG, "Forcing reset command while " + pendingCommand + " is pending");
            pendingCommand = null;  // Clear pending command state
        }
        
        pendingCommand = CommandType.RESET;
        lastCommandTime = System.currentTimeMillis();
        
        // Try multiple approaches to send the command for better reliability
        boolean result = false;
        
        // Skip the root method as it's consistently failing and causing errors
        // Just use the standard, more reliable method
        result = serialManager.sendData(CMD_RESET);
        
        // If we get here, log the command status
        Log.d(TAG, "Reset command sent with result: " + result);
        
        return result;
    }
    
    /**
     * Process Reset response
     */
    private void processResetResponse(byte[] data) {
        if (data.length == 1 && data[0] == RESP_ACK) {
            // Reset acknowledged
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.RESET,
                    ResponseStatus.SUCCESS,
                    data
                );
            }
        } else {
            // Error or unknown response
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.RESET,
                    ResponseStatus.ERROR,
                    data
                );
            }
        }
    }
    
    /**
     * Send Setup command
     * write: 09 09
     * expected response: 03 11 56 01 01 00 03 05 0A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 7E
     * This is the most important command for resolving 'NO VMC communication' errors
     */
    public boolean sendSetup() {
        if (pendingCommand != null) {
            // Since SETUP is critical for communication, we can force it even if another command
            // is pending, with the exception of another SETUP command
            if (pendingCommand == CommandType.SETUP) {
                return false; // Another SETUP command is already in progress
            }
            
            Log.d(TAG, "Forcing SETUP command while " + pendingCommand + " is pending");
            pendingCommand = null; // Clear pending command state
        }
        
        // Reset the retry counter when we intentionally send a SETUP command
        setupRetryCount = 0;
        
        pendingCommand = CommandType.SETUP;
        lastCommandTime = System.currentTimeMillis();
        
        boolean result = serialManager.sendData(CMD_SETUP);
        return result;
    }
    
    /**
     * Process Setup response
     */
    private void processSetupResponse(byte[] data) {
        // Expected full setup response is 24 bytes starting with 03 11 56
        // But sometimes it might come in multiple parts or have extra bytes
        
        // Check if it's the start of the correct response sequence
        if (data.length >= 3 && data[0] == 0x03 && data[1] == 0x11 && data[2] == 0x56) {
            Log.d(TAG, "Received valid setup response header");
            
            // If we got the full response
            if (data.length >= 24) {
                // Parse the denomination data
                parseDenominationData(data);
                
                if (responseListener != null) {
                    responseListener.onCommandResponse(
                        CommandType.SETUP,
                        ResponseStatus.SUCCESS,
                        data
                    );
                }
            } else {
                // If we got a partial response, but it looks valid
                // Sometimes with serial protocols, the full response might come in chunks
                Log.d(TAG, "Received partial setup response (valid header): " + serialManager.bytesToHexString(data));
                
                // For the hardware LCD, even this partial response might be enough
                // to clear the "NO VMC communication" message
                if (responseListener != null) {
                    responseListener.onCommandResponse(
                        CommandType.SETUP,
                        ResponseStatus.SUCCESS,
                        data
                    );
                }
                
                // Wait a small delay and send a follow-up command to keep communication active
                try {
                    Thread.sleep(100);
                    serialManager.sendData(CMD_POLL);  // Send a poll command to keep communication active
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            // Response doesn't match expected pattern
            Log.w(TAG, "Unexpected setup response format: " + serialManager.bytesToHexString(data));
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.SETUP,
                    ResponseStatus.ERROR,
                    data
                );
            }
        }
    }
    
    /**
     * Parse the denomination data from setup response
     * This follows the logic from the C++ code in OnSelftest()
     */
    private void parseDenominationData(byte[] data) {
        // Clear existing denominations
        coinDenominations.clear();
        
        // Check if we have enough data to parse
        if (data.length < 14) {
            Log.w(TAG, "Setup response too short to parse denominations");
            return;
        }
        
        // Extract the scaling factor (same as m_scale in C++ code)
        int scale = data[3] & 0xFF;
        this.scaleFactor = scale;
        Log.d(TAG, "Scaling factor: " + scale);
        
        // Extract decimal places
        int decimalPlaces = data[4] & 0xFF;
        Log.d(TAG, "Decimal places: " + decimalPlaces);
        
        // Determine if we have a C2 hardware (14 bytes) or Gryphon hardware (24 bytes)
        boolean isGryphonHardware = (data.length >= 24);
        int maxCoins = isGryphonHardware ? 16 : 6;
        
        // Log the hardware type
        Log.d(TAG, "Detected " + (isGryphonHardware ? "Gryphon" : "C2") + " hardware");
        
        // Parse coin denominations - start at byte 7
        for (int i = 0; i < maxCoins; i++) {
            if (i + 7 >= data.length || data[i + 7] == 0) {
                // No more coin types or end of data
                break;
            }
            
            double denomination = 0.0;
            int coinValue = data[i + 7] & 0xFF;
            
            // Apply decimal places and scaling factor, same as in C++ code
            if (decimalPlaces < 6) {
                switch (decimalPlaces) {
                    case 0: denomination = coinValue * scale; break;
                    case 1: denomination = (double)(coinValue * scale) / 10; break;
                    case 2: denomination = (double)(coinValue * scale) / 100; break;
                    case 3: denomination = (double)(coinValue * scale) / 1000; break;
                    case 4: denomination = (double)(coinValue * scale) / 10000; break;
                    case 5: denomination = (double)(coinValue * scale) / 100000; break;
                }
            } else {
                denomination = coinValue * scale;
            }
            
            // Store the denomination (coin type is 1-indexed in the UI)
            coinDenominations.put(i + 1, denomination);
            Log.d(TAG, "Coin Type " + (i + 1) + ": " + denomination);
        }
    }
    
    /**
     * Send Coin Type command
     * write: 0C FF FF FF FF 08
     * expected response: 00
     */
    public boolean sendCoinType() {
        if (pendingCommand != null) {
            return false; // Command in progress
        }
        
        pendingCommand = CommandType.COIN_TYPE;
        lastCommandTime = System.currentTimeMillis();
        
        return serialManager.sendData(CMD_COIN_TYPE);
    }
    
    /**
     * Process Coin Type response
     */
    private void processCoinTypeResponse(byte[] data) {
        if (data.length == 1 && data[0] == RESP_ACK) {
            // Coin type acknowledged
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.COIN_TYPE,
                    ResponseStatus.SUCCESS,
                    data
                );
            }
        } else {
            // Error or unknown response
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.COIN_TYPE,
                    ResponseStatus.ERROR,
                    data
                );
            }
        }
    }
    
    /**
     * Send dispense command to dispense coins
     * Based on OnBnClickedDispense2() in C++ code
     * 
     * @param amount Amount to dispense (in the same unit as coin denominations)
     * @return true if command was sent successfully
     */
    public boolean sendDispense(int amount) {
        if (pendingCommand != null) {
            return false; // Command in progress
        }
        
        if (amount <= 0) {
            Log.e(TAG, "Invalid dispense amount: " + amount);
            return false;
        }
        
        // Check if amount is divisible by scale factor
        if (amount % scaleFactor != 0) {
            Log.e(TAG, "Amount " + amount + " is not divisible by scale factor " + scaleFactor);
            return false;
        }
        
        // Calculate data value
        int data = amount / scaleFactor;
        
        // Create dispense command
        byte[] dispenseCommand = new byte[] {
            0x0F, 0x02, (byte)data, (byte)(0x0F + 0x02 + data) // Command + checksum
        };
        
        Log.d(TAG, "Sending dispense command for amount: " + amount + ", data: " + data);
        
        pendingCommand = CommandType.DISPENSE;
        lastCommandTime = System.currentTimeMillis();
        
        return serialManager.sendData(dispenseCommand);
    }
    
    /**
     * Process Dispense response
     */
    private void processDispenseResponse(byte[] data) {
        if (data.length == 1 && data[0] == RESP_ACK) {
            // Dispense acknowledged
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.DISPENSE,
                    ResponseStatus.DISPENSED,
                    data
                );
            }
        } else {
            // Error or unknown response
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.DISPENSE,
                    ResponseStatus.ERROR,
                    data
                );
            }
        }
    }
    
    /**
     * Send a specific coin type dispense command
     * Based on OnBnClickedButton2() in C++ code
     * 
     * @param coinType The coin type to dispense (1-16)
     * @param count The number of coins to dispense
     * @return true if command was sent successfully
     */
    public boolean sendDispenseType(int coinType, int count) {
        if (pendingCommand != null) {
            return false; // Command in progress
        }
        
        if (coinType <= 0 || coinType > 15) {
            Log.e(TAG, "Invalid coin type: " + coinType);
            return false;
        }
        
        if (count <= 0) {
            Log.e(TAG, "Invalid coin count: " + count);
            return false;
        }
        
        // Calculate byte values using binary encoding logic from C++ code
        int byte01 = (count & 0x0F) << 4 | (coinType & 0x0F);
        
        // Create dispense command
        byte[] dispenseCommand = new byte[] {
            0x0D, (byte)byte01, (byte)(0x0D + byte01) // Command + checksum
        };
        
        Log.d(TAG, "Sending dispense type command for type: " + coinType + ", count: " + count + ", encoded: " + byte01);
        
        pendingCommand = CommandType.DISPENSE;
        lastCommandTime = System.currentTimeMillis();
        
        return serialManager.sendData(dispenseCommand);
    }
    
    /**
     * Send tube status command
     * Based on OnBnClickedTube() in C++ code
     * 
     * @return true if command was sent successfully
     */
    public boolean sendTubeStatus() {
        if (pendingCommand != null) {
            return false; // Command in progress
        }
        
        pendingCommand = CommandType.TUBE_STATUS;
        lastCommandTime = System.currentTimeMillis();
        
        return serialManager.sendData(CMD_TUBE_STATUS);
    }
    
    /**
     * Process Tube Status response
     */
    private void processTubeStatusResponse(byte[] data) {
        if (data.length >= 19) {
            // Process tube status response
            // First two bytes contain the full tube status bitmaps
            byte tubeFullHigh = data[0];
            byte tubeFullLow = data[1];
            
            // Bytes 2-18 contain the count of coins in each tube
            byte[] tubeCounts = new byte[16];
            System.arraycopy(data, 2, tubeCounts, 0, Math.min(16, data.length - 2));
            
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.TUBE_STATUS,
                    ResponseStatus.SUCCESS,
                    data
                );
            }
        } else {
            // Incomplete response
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.TUBE_STATUS,
                    ResponseStatus.ERROR,
                    data
                );
            }
        }
    }
    
    /**
     * Send self test command
     * Based on OnBnClickedAbout() in C++ code
     * 
     * @return true if command was sent successfully
     */
    public boolean sendSelfTest() {
        if (pendingCommand != null) {
            return false; // Command in progress
        }
        
        pendingCommand = CommandType.SELF_TEST;
        lastCommandTime = System.currentTimeMillis();
        
        return serialManager.sendData(CMD_SELF_TEST);
    }
    
    /**
     * Process Self Test response
     */
    private void processSelfTestResponse(byte[] data) {
        if (data.length >= 3) {
            // Process diagnostic status response
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.SELF_TEST,
                    ResponseStatus.DIAGNOSTIC_STATUS,
                    data
                );
            }
        } else {
            // Incomplete response
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.SELF_TEST,
                    ResponseStatus.ERROR,
                    data
                );
            }
        }
    }
    
    /**
     * Get the coin denominations map parsed from setup response
     * @return Map of coin types (1-indexed) to their denominations
     */
    public Map<Integer, Double> getCoinDenominations() {
        return new HashMap<>(coinDenominations);
    }
    
    /**
     * Get the scale factor parsed from setup response
     * @return The scale factor
     */
    public int getScaleFactor() {
        return scaleFactor;
    }
}
