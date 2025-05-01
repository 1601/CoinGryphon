package com.example.coingryphon.serial;

import android.util.Log;

import java.util.Arrays;

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
    
    /**
     * Command types for the Gryphon coin acceptor
     */
    public enum CommandType {
        POLL,
        RESET,
        SETUP,
        COIN_TYPE
    }
    
    /**
     * Response status from Gryphon commands
     */
    public enum ResponseStatus {
        SUCCESS,
        ERROR,
        COIN_ACCEPTED,
        TIMEOUT
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
}
