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
    
    // Command timeout in milliseconds
    private static final long COMMAND_TIMEOUT = 1000;
    
    private final GryphonSerialManager serialManager;
    private GryphonResponseListener responseListener;
    private CommandType pendingCommand = null;
    private long lastCommandTime = 0;
    
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
        if (data == null || data.length == 0 || pendingCommand == null) {
            return;
        }
        
        // Reset the pending command timer
        pendingCommand = null;
        lastCommandTime = 0;
        
        Log.d(TAG, "Processing response for command: " + pendingCommand);
        
        switch (pendingCommand) {
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
        }
    }
    
    /**
     * Check for command timeouts
     */
    public void checkTimeouts() {
        if (pendingCommand != null && 
            (System.currentTimeMillis() - lastCommandTime) > COMMAND_TIMEOUT) {
            
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    pendingCommand,
                    ResponseStatus.TIMEOUT,
                    null
                );
            }
            
            pendingCommand = null;
            lastCommandTime = 0;
        }
    }
    
    /**
     * Send Poll command
     * write: 0B 0B
     * expected response: 00 or 51 0E 5F
     */
    public boolean sendPoll() {
        if (pendingCommand != null) {
            return false; // Command in progress
        }
        
        pendingCommand = CommandType.POLL;
        lastCommandTime = System.currentTimeMillis();
        
        return serialManager.sendData(CMD_POLL);
    }
    
    /**
     * Process Poll response
     */
    private void processPollResponse(byte[] data) {
        if (data.length == 1 && data[0] == RESP_ACK) {
            // Acknowledged - no coin
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.POLL,
                    ResponseStatus.SUCCESS,
                    data
                );
            }
        } else if (data.length == 3 && 
                  Arrays.equals(data, RESP_POLL_COIN)) {
            // Coin accepted
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.POLL,
                    ResponseStatus.COIN_ACCEPTED,
                    data
                );
            }
        } else {
            // Error or unknown response
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
        if (pendingCommand != null) {
            return false; // Command in progress
        }
        
        pendingCommand = CommandType.RESET;
        lastCommandTime = System.currentTimeMillis();
        
        return serialManager.sendData(CMD_RESET);
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
     */
    public boolean sendSetup() {
        if (pendingCommand != null) {
            return false; // Command in progress
        }
        
        pendingCommand = CommandType.SETUP;
        lastCommandTime = System.currentTimeMillis();
        
        return serialManager.sendData(CMD_SETUP);
    }
    
    /**
     * Process Setup response
     */
    private void processSetupResponse(byte[] data) {
        // Expected full setup response is 24 bytes
        if (data.length == 24) {
            if (responseListener != null) {
                responseListener.onCommandResponse(
                    CommandType.SETUP,
                    ResponseStatus.SUCCESS,
                    data
                );
            }
        } else {
            // Error or unknown response
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
