package com.example.coingryphon;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.coingryphon.serial.GryphonProtocol;
import com.example.coingryphon.serial.GryphonSerialManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private GryphonSerialManager serialManager;
    private GryphonProtocol gryphonProtocol;
    
    private ScheduledExecutorService pollingExecutor;
    private ArrayList<String> logMessages = new ArrayList<>();
    
    // UI Elements for coin dispensing
    private Spinner spinnerCoinType;
    private ArrayAdapter<String> coinTypeAdapter;
    private Map<Integer, Integer> spinnerPositionToCoinType = new HashMap<>();
    
    // Handler for delayed operations
    private final android.os.Handler handler = new android.os.Handler();
    
    // Runnable to check for command timeouts
    private final Runnable timeoutChecker = new Runnable() {
        @Override
        public void run() {
            if (gryphonProtocol != null) {
                gryphonProtocol.checkTimeouts();
            }
            // Schedule the next check
            handler.postDelayed(this, 500); // Check every 500ms
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize the serial manager
        serialManager = GryphonSerialManager.getInstance(this);
        gryphonProtocol = new GryphonProtocol(serialManager);
        
        // Set up the UI components
        setupUI();
        
        // Set up the protocol response listener
        setupResponseListener();
        
        // Set up the serial manager listener
        setupSerialListener();
        
        // Start the timeout checker
        handler.post(timeoutChecker);
        
        // Don't connect automatically - wait for user action instead
        // connectToDevice();
    }
    
    private void setupUI() {
        setupAllControls();
    }
    
    private void setupAllControls() {
        // Connection status
        updateConnectionStatus("Disconnected");
        
        // Connect button
        findViewById(R.id.btnConnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToDevice();
            }
        });
        
        // Disconnect button
        findViewById(R.id.btnDisconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectDevice();
            }
        });
        
        // Dispense button (moved to connection section)
        findViewById(R.id.btnDispense).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispenseByAmount();
            }
        });
        
        // Poll button
        findViewById(R.id.btnPoll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendPollCommand();
            }
        });
        
        // Reset button
        findViewById(R.id.btnReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendResetCommand();
            }
        });
        
        // Setup button
        findViewById(R.id.btnSetup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSetupCommand();
            }
        });
        
        // Coin Type button
        findViewById(R.id.btnCoinType).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCoinTypeCommand();
            }
        });
        
        // Start Polling button
        findViewById(R.id.btnStartPolling).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPolling();
            }
        });
        
        // Stop Polling button
        findViewById(R.id.btnStopPolling).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPolling();
            }
        });
        
        // Self Test button
        findViewById(R.id.btnSelfTest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSelfTestCommand();
            }
        });
        
        // Tube Status button
        findViewById(R.id.btnTubeStatus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTubeStatusCommand();
            }
        });
        
        // Set up the coin type spinner
        spinnerCoinType = findViewById(R.id.spinnerCoinType);
        coinTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        spinnerCoinType.setAdapter(coinTypeAdapter);
        
        // Dispense Type button
        findViewById(R.id.btnDispenseType).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispenseByType();
            }
        });
        
        // Clear Log button
        findViewById(R.id.btnClearLog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearLog();
            }
        });
    }
    
    /**
     * Update the UI with the connection status
     */
    private void updateConnectionStatus(String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.tvConnectionStatus).post(new Runnable() {
                    @Override
                    public void run() {
                        ((android.widget.TextView) findViewById(R.id.tvConnectionStatus)).setText("Connection Status: " + status);
                    }
                });
            }
        });
    }
    
    /**
     * Update the UI with the last response
     */
    private void updateLastResponse(String response) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.tvLastResponse).post(new Runnable() {
                    @Override
                    public void run() {
                        ((android.widget.TextView) findViewById(R.id.tvLastResponse)).setText("Last Response: " + response);
                    }
                });
            }
        });
    }
    
    /**
     * Add log message and update the log display
     */
    private void addLogMessage(String message) {
        logMessages.add(0, message); // Add to the beginning for newest first
        updateLogDisplay();
    }
    
    /**
     * Update the log display with current messages
     */
    private void updateLogDisplay() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                StringBuilder logText = new StringBuilder();
                for (String message : logMessages) {
                    logText.append(message).append("\n");
                }
                
                findViewById(R.id.tvLog).post(new Runnable() {
                    @Override
                    public void run() {
                        ((android.widget.TextView) findViewById(R.id.tvLog)).setText(logText.toString());
                    }
                });
            }
        });
    }
    
    /**
     * Set up the response listener for Gryphon protocol commands
     */
    private void setupResponseListener() {
        gryphonProtocol.setResponseListener(new GryphonProtocol.GryphonResponseListener() {
            @Override
            public void onCommandResponse(GryphonProtocol.CommandType command, GryphonProtocol.ResponseStatus status, byte[] responseData) {
                // Update UI with response
                String responseHex = responseData != null ? GryphonSerialManager.bytesToHexString(responseData) : "No data";
                String responseText = command + " - " + status + ": " + responseHex;
                
                updateLastResponse(responseHex);
                addLogMessage(responseText);
                
                // Handle coin acceptance
                if (status == GryphonProtocol.ResponseStatus.COIN_ACCEPTED) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Coin Accepted!", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else if (status == GryphonProtocol.ResponseStatus.SUCCESS) {
                    switch (command) {
                        case COIN_TYPE:
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    addLogMessage("Coin Type command success");
                                }
                            });
                            break;
                            
                        case DISPENSE:
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    addLogMessage("Coins dispensed successfully");
                                }
                            });
                            break;
                            
                        case TUBE_STATUS:
                            // Process tube status data
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    displayTubeStatus(responseData);
                                }
                            });
                            break;
                            
                        case SELF_TEST:
                            // Process self-test data
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    displaySelfTestResults(responseData);
                                }
                            });
                            break;
                    }
                } else if (status == GryphonProtocol.ResponseStatus.TIMEOUT) {
                    // Handle timeouts with potential reconnection
                    if (!serialManager.isConnected()) {
                        addLogMessage("Connection lost - attempting to reconnect...");
                        reconnectWithDelay(1000); // Try to reconnect after 1 second
                    } else {
                        switch (command) {
                            case SETUP:
                                addLogMessage("Setup command timed out");
                                break;
                                
                            case COIN_TYPE:
                                addLogMessage("Coin Type command timed out");
                                break;
                                
                            case DISPENSE:
                                addLogMessage("Dispense command timed out");
                                break;
                                
                            case TUBE_STATUS:
                                addLogMessage("Tube Status command timed out");
                                break;
                                
                            case SELF_TEST:
                                addLogMessage("Self Test command timed out");
                                break;
                        }
                    }
                }
                
                Log.d(TAG, "Command response: " + responseText);
            }
        });
    }
    
    /**
     * Set up the serial manager listener for device connection events
     */
    private void setupSerialListener() {
        serialManager.setListener(new GryphonSerialManager.GryphonSerialListener() {
            @Override
            public void onReceiveData(byte[] data) {
                // Data processing is handled by the protocol class
            }
            
            @Override
            public void onConnectionEstablished() {
                updateConnectionStatus("Connected");
                initializeConnection();
                
                // Update the coin type spinner after connection
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateCoinTypeSpinner();
                        // Explicitly notify the user that they need to click Start Polling to accept coins
                        addLogMessage("Connection ready. Click 'Start Polling' to begin accepting coins.");
                    }
                }, 2000); // Wait for setup to complete
            }
            
            @Override
            public void onConnectionLost() {
                updateConnectionStatus("Disconnected");
                addLogMessage("Connection lost with device");
                stopPolling(); // Stop polling if connection is lost
            }
            
            @Override
            public void onError(Exception e) {
                updateConnectionStatus("Error: " + e.getMessage());
                addLogMessage("Error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Clear all log messages
     */
    private void clearLog() {
        logMessages.clear();
        updateLogDisplay();
    }
    
    /**
     * Connect to the Gryphon device
     */
    /**
     * Connect to the Gryphon device - matches C++ OnOpenport() function
     */
    private void connectToDevice() {
        if (serialManager.connectToDevice()) {
            addLogMessage("Connected to device on " + serialManager.getPortPath());
            
            // Following C++ reference code in OnOpenport:  OnReset() then OnSelftest()
            // First send a reset command
            if (gryphonProtocol.sendReset()) {
                addLogMessage("Reset --> OK");
                addLogMessage("--------------------------------------------");
                
                // Then perform self-test (which also gets coin configuration)
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (gryphonProtocol.sendSetup()) {
                            addLogMessage("The configuration of the current module:");
                            // Setup response will come through the response listener
                        }
                    }
                }, 200); // 200ms delay like in C++ code
                
                // Enable all UI controls after successful connection
                enableControlsAfterConnection(true);
            }
        } else {
            addLogMessage("Failed to open the serial port");
            addLogMessage("Please confirm if the serial port number is correct, or the connection status of communication cable and power cable");
        }
    }
    
    /**
     * Enable or disable controls based on connection state
     * Matches behavior in C++ OnOpenport and OnCloseport functions
     */
    private void enableControlsAfterConnection(boolean connected) {
        // Connection controls
        findViewById(R.id.btnConnect).setEnabled(!connected);
        findViewById(R.id.btnDisconnect).setEnabled(connected);
        
        // Command controls
        findViewById(R.id.btnPoll).setEnabled(connected);
        findViewById(R.id.btnReset).setEnabled(connected);
        findViewById(R.id.btnSetup).setEnabled(connected);
        findViewById(R.id.btnSelfTest).setEnabled(connected);
        findViewById(R.id.btnTubeStatus).setEnabled(connected);
        findViewById(R.id.btnCoinType).setEnabled(connected);
        findViewById(R.id.btnStartPolling).setEnabled(connected);
        findViewById(R.id.btnStopPolling).setEnabled(connected);
        
        // Dispense controls
        findViewById(R.id.btnDispense).setEnabled(connected);
        findViewById(R.id.btnDispenseType).setEnabled(connected);
        findViewById(R.id.spinnerCoinType).setEnabled(connected);
        findViewById(R.id.etDispenseAmount).setEnabled(connected);
        findViewById(R.id.etDispenseCount).setEnabled(connected);
    }
    
    /**
     * Disconnect from the Gryphon device
     */
    /**
     * Disconnect from the Gryphon device - matches C++ OnCloseport() function
     */
    private void disconnectDevice() {
        // Stop polling first
        stopPolling();
        
        // Close the connection
        serialManager.closeConnection();
        
        // Disable all controls except Connect
        enableControlsAfterConnection(false);
        
        // Clear the log display
        clearLog();
        
        // Update status
        updateConnectionStatus("Disconnected");
        addLogMessage("Disconnected from device");
    }
    
    /**
     * Send a poll command to check for coin acceptance
     */
    private void sendPollCommand() {
        if (serialManager.isConnected()) {
            if (gryphonProtocol.sendPoll()) {
                addLogMessage("Sent Poll command");
            } else {
                addLogMessage("Failed to send Poll command");
            }
        } else {
            addLogMessage("Not connected to device");
        }
    }
    
    /**
     * Send a reset command to the Gryphon
     */
    private void sendResetCommand() {
        if (serialManager.isConnected()) {
            if (gryphonProtocol.sendReset()) {
                addLogMessage("Sent Reset command");
            } else {
                addLogMessage("Failed to send Reset command");
            }
        } else {
            addLogMessage("Not connected to device");
        }
    }
    
    /**
     * Send a setup command to the Gryphon
     */
    private void sendSetupCommand() {
        if (serialManager.isConnected()) {
            if (gryphonProtocol.sendSetup()) {
                addLogMessage("Sent Setup command");
                
                // After setup, update the coin type spinner once we get the response
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateCoinTypeSpinner();
                    }
                }, 1000); // Wait for the response to be processed
            } else {
                addLogMessage("Failed to send Setup command");
            }
        } else {
            addLogMessage("Not connected to device");
        }
    }
    
    /**
     * Send a coin type command to the Gryphon
     */
    private void sendCoinTypeCommand() {
        if (serialManager.isConnected()) {
            if (gryphonProtocol.sendCoinType()) {
                addLogMessage("Sent Coin Type command");
            } else {
                addLogMessage("Failed to send Coin Type command");
            }
        } else {
            addLogMessage("Not connected to device");
        }
    }
    
    /**
     * Start continuous polling for coins
     * This shifts the device from 'not accepting coin' to 'accepting coin' mode
     */
    private void startPolling() {
        if (!serialManager.isConnected()) {
            addLogMessage("Cannot start polling: Not connected to device");
            return;
        }
        
        // Stop any existing polling job
        stopPolling();
        
        addLogMessage("Start - transitioning to accepting coin mode");
        
        // Start a new polling executor
        pollingExecutor = Executors.newSingleThreadScheduledExecutor();
        pollingExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                gryphonProtocol.sendPoll();
            }
        }, 0, 500, TimeUnit.MILLISECONDS); // Poll every 500ms
        
        addLogMessage("Started continuous polling - now accepting coins");
    }
    
    /**
     * Stop continuous polling
     * This shifts the device from 'accepting coin' back to 'not accepting coin' mode
     */
    private void stopPolling() {
        if (pollingExecutor != null && !pollingExecutor.isShutdown()) {
            pollingExecutor.shutdownNow();
            pollingExecutor = null;
            addLogMessage("Stopped polling - transitioning to not accepting coin mode");
            addLogMessage("Device is now in standby mode - not accepting coins");
        }
    }
    
    /**
     * Reconnect to the device after a specified delay
     */
    private void reconnectWithDelay(long delayMillis) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!serialManager.isConnected()) {
                    addLogMessage("Attempting reconnection...");
                    connectToDevice();
                }
            }
        }, delayMillis);
    }
    
    /**
     * Initialize the connection by sending the setup command and then 
     * configuring coin types
     * 
     * Follows the C++ sequence:
     * 1. Detect port - ttyS4 (handled by serialManager.connectToDevice)
     * 2. Open port - ttyS4 (handled by serialManager.connectToDevice)
     * 3. Mode - not accepting coin (reset + setup + coin type with no polling)
     * 4. Start - accepting coin (this happens when startPolling is called)
     */
    private void initializeConnection() {
        if (serialManager.isConnected()) {
            addLogMessage("Initializing device communication...");
            addLogMessage("Port detected and opened: " + serialManager.getPortPath());
            addLogMessage("Setting mode: not accepting coin");
            
            // First send a reset command
            if (gryphonProtocol.sendReset()) {
                addLogMessage("Sent Reset command for initialization");
                
                // Wait a bit then send setup 
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (gryphonProtocol.sendSetup()) {
                            addLogMessage("Sent Setup command for initialization");
                            
                            // Wait a bit then configure coin types
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (gryphonProtocol.sendCoinType()) {
                                        addLogMessage("Sent Coin Type command for initialization");
                                        addLogMessage("Device initialization complete");
                                        addLogMessage("Device is ready but not accepting coins");
                                        addLogMessage("Use 'Start Polling' to begin accepting coins");
                                    }
                                }
                            }, 500);
                        }
                    }
                }, 500);
            }
        }
    }
    
    /**
     * Send a self-test command to check diagnostic status
     */
    private void sendSelfTestCommand() {
        if (serialManager.isConnected()) {
            if (gryphonProtocol.sendSelfTest()) {
                addLogMessage("Sent Self Test command");
            } else {
                addLogMessage("Failed to send Self Test command");
            }
        } else {
            addLogMessage("Not connected to device");
        }
    }
    
    /**
     * Send a tube status command to check tube contents
     */
    private void sendTubeStatusCommand() {
        if (serialManager.isConnected()) {
            if (gryphonProtocol.sendTubeStatus()) {
                addLogMessage("Sent Tube Status command");
            } else {
                addLogMessage("Failed to send Tube Status command");
            }
        } else {
            addLogMessage("Not connected to device");
        }
    }
    
    /**
     * Display the tube status information - matches C++ OnBnClickedTube implementation
     */
    private void displayTubeStatus(byte[] data) {
        if (data == null || data.length < 18) {
            addLogMessage("Invalid tube status data");
            return;
        }
        
        addLogMessage("TUBE STATUS --> OK");
        addLogMessage("--------------------------------------------");
        
        // First two bytes represent full tube status
        int fullTubeStatus1 = data[0] & 0xFF;
        int fullTubeStatus2 = data[1] & 0xFF;
        
        // Get coin denominations for better display
        Map<Integer, Double> denominations = gryphonProtocol.getCoinDenominations();
        int scaleFactor = gryphonProtocol.getScaleFactor();
        
        // Check full tube status
        for (int i = 0; i < 8; i++) {
            if ((fullTubeStatus1 & (1 << i)) != 0) {
                String denomination = formatCoinValue(denominations.getOrDefault(i+1, 0.0));
                addLogMessage("The tube of type" + (i+1) + ":" + denomination + ",is already full");
            }
        }
        
        for (int i = 0; i < 8; i++) {
            if ((fullTubeStatus2 & (1 << i)) != 0) {
                String denomination = formatCoinValue(denominations.getOrDefault(i+9, 0.0));
                addLogMessage("The tube of type" + (i+9) + ":" + denomination + ",is already full");
            }
        }
        
        // Tube counts (remaining bytes) - like C++ code lines 1013-1027
        boolean anyCoins = false;
        
        // Process tube status data for coins in tubes
        for (int i = 0; i < 16 && i + 2 < data.length; i++) {
            int count = data[i + 2] & 0xFF;
            if (count > 0) {
                // In C++ it alternates between denom01 and denom02
                String coinValue = formatCoinValue(
                    denominations.getOrDefault((i % 2) + 1, 0.0));
                
                addLogMessage("Type" + (i+1) + ":" + coinValue + ",has been accepting " + count + "coins");
                anyCoins = true;
            }
        }
        
        if (!anyCoins) {
            addLogMessage("No any coins in changer");
        }
        
        addLogMessage("--------------------------------------------");
    }
    
    /**
     * Display the self-test results - matches C++ OnBnClickedAbout implementation
     */
    private void displaySelfTestResults(byte[] data) {
        if (data == null || data.length < 1) {
            addLogMessage("Invalid self-test data received");
            return;
        }
        
        int statusCode = data[0] & 0xFF;
        String statusMessage = "Unknown status";
        
        // Following C++ OnBnClickedAbout() implementation for error/status codes
        addLogMessage("error/status: ");
        
        switch (statusCode) {
            case 1:  statusMessage = "01 00 -> powering up"; break;
            case 2:  statusMessage = "02 00 -> powering down"; break;
            case 3:  statusMessage = "03 00 -> normal"; break;
            case 4:  statusMessage = "04 00 -> Keypad shifted"; break;
            case 5:  statusMessage = "05 10/20 -> Manual fill/New inventory available"; break;
            case 6:  statusMessage = "06 00 -> Inhibited by VMC"; break;
            case 16: statusMessage = "10 E2 -> Changer error"; break;
            case 17: statusMessage = "11 E2 -> Discriminator changer error"; break;
            case 18: statusMessage = "12 E2 -> Accept gate module error"; break;
            case 19: statusMessage = "13 E2 -> Separator module error"; break;
            case 20: statusMessage = "14 E2 -> Dispenser module error"; break;
            case 21: statusMessage = "15 E2 -> Coin tube module error"; break;
        }
        
        addLogMessage(statusMessage);
        addLogMessage("--------------------------------------------");
    }
    
    /**
     * Format a coin value for display, similar to the C++ implementation
     * @param value The coin value to format
     * @return Formatted string representation of the coin value
     */
    private String formatCoinValue(double value) {
        return String.format("%.2f", value);
    }
    
    /**
     * Update the coin type spinner with current denominations
     */
    private void updateCoinTypeSpinner() {
        coinTypeAdapter.clear();
        spinnerPositionToCoinType.clear();
        
        Map<Integer, Double> denominations = gryphonProtocol.getCoinDenominations();
        if (denominations.isEmpty()) {
            // If we don't have denominations yet, add generic options
            for (int i = 1; i <= 16; i++) {
                coinTypeAdapter.add("Type " + i);
                spinnerPositionToCoinType.put(i - 1, i);
            }
        } else {
            // Use the actual denominations
            int position = 0;
            for (Map.Entry<Integer, Double> entry : denominations.entrySet()) {
                int coinType = entry.getKey();
                double value = entry.getValue();
                coinTypeAdapter.add(String.format("Type %d - %.2f", coinType, value));
                spinnerPositionToCoinType.put(position, coinType);
                position++;
            }
        }
        
        coinTypeAdapter.notifyDataSetChanged();
    }
    
    /**
     * Dispense coins by amount
     */
    private void dispenseByAmount() {
        if (!serialManager.isConnected()) {
            addLogMessage("Cannot dispense: Not connected to device");
            return;
        }
        
        EditText etAmount = findViewById(R.id.etDispenseAmount);
        String amountStr = etAmount.getText().toString();
        
        if (TextUtils.isEmpty(amountStr)) {
            addLogMessage("Please enter an amount to dispense");
            return;
        }
        
        try {
            int amount = Integer.parseInt(amountStr);
            if (amount <= 0) {
                addLogMessage("Amount must be greater than zero");
                return;
            }
            
            if (gryphonProtocol.sendDispense(amount)) {
                addLogMessage("Sent dispense command for amount: " + amount);
            } else {
                addLogMessage("Failed to send dispense command");
            }
        } catch (NumberFormatException e) {
            addLogMessage("Invalid amount: " + amountStr);
        }
    }
    
    /**
     * Dispense coins by type and count
     */
    private void dispenseByType() {
        if (!serialManager.isConnected()) {
            addLogMessage("Cannot dispense: Not connected to device");
            return;
        }
        
        // Get the selected coin type
        int position = spinnerCoinType.getSelectedItemPosition();
        if (position < 0 || !spinnerPositionToCoinType.containsKey(position)) {
            addLogMessage("Please select a coin type");
            return;
        }
        
        int coinType = spinnerPositionToCoinType.get(position);
        
        // Get the count
        EditText etCount = findViewById(R.id.etDispenseCount);
        String countStr = etCount.getText().toString();
        
        if (TextUtils.isEmpty(countStr)) {
            addLogMessage("Please enter a count to dispense");
            return;
        }
        
        try {
            int count = Integer.parseInt(countStr);
            if (count <= 0) {
                addLogMessage("Count must be greater than zero");
                return;
            }
            
            if (gryphonProtocol.sendDispenseType(coinType, count)) {
                addLogMessage("Sent dispense command for coin type " + coinType + " count " + count);
            } else {
                addLogMessage("Failed to send dispense type command");
            }
        } catch (NumberFormatException e) {
            addLogMessage("Invalid count: " + countStr);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        handler.removeCallbacks(timeoutChecker); // Stop the timeout checker
        serialManager.destroy(); // Clean up resources
    }
}
