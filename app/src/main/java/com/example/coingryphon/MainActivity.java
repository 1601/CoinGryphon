package com.example.coingryphon;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.coingryphon.serial.GryphonProtocol;
import com.example.coingryphon.serial.GryphonSerialManager;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private GryphonSerialManager serialManager;
    private GryphonProtocol gryphonProtocol;
    
    private ScheduledExecutorService pollingExecutor;
    private ArrayList<String> logMessages = new ArrayList<>();
    
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
        
        // Start the timeout checker
        handler.post(timeoutChecker);
        
        // Try to connect automatically on startup
        connectToDevice();
    }
    
    private void setupUI() {
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
        
        // Clear Log button
        findViewById(R.id.btnClearLog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logMessages.clear();
                updateLogDisplay();
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
                } else if (status == GryphonProtocol.ResponseStatus.TIMEOUT) {
                    // Handle timeouts with potential reconnection
                    if (!serialManager.isConnected()) {
                        addLogMessage("Connection lost - attempting to reconnect...");
                        reconnectWithDelay(1000); // Try to reconnect after 1 second
                    }
                }
                
                Log.d(TAG, "Command response: " + responseText);
            }
        });
        
        // Set up serial manager listener for connection events
        serialManager.setListener(new GryphonSerialManager.GryphonSerialListener() {
            @Override
            public void onReceiveData(byte[] data) {
                // Data processing is handled by the protocol class
            }
            
            @Override
            public void onConnectionEstablished() {
                updateConnectionStatus("Connected");
                addLogMessage("Connection established with device");
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
     * Connect to the Gryphon device
     */
    private void connectToDevice() {
        if (serialManager.connectToDevice()) {
            addLogMessage("Connecting to device...");
            
            // Schedule initialization sequence after connection is established
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (serialManager.isConnected()) {
                        initializeConnection();
                    } else {
                        // If not connected yet, try again
                        reconnectWithDelay(1000);
                    }
                }
            }, 1000); // Give the connection a second to establish
        } else {
            addLogMessage("No devices found or permission denied - will retry...");
            reconnectWithDelay(3000); // Try again in 3 seconds
        }
    }
    
    /**
     * Disconnect from the Gryphon device
     */
    private void disconnectDevice() {
        serialManager.closeConnection();
        updateConnectionStatus("Disconnected");
        addLogMessage("Disconnected from device");
        stopPolling(); // Stop polling when disconnected
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
     */
    private void startPolling() {
        if (!serialManager.isConnected()) {
            addLogMessage("Cannot start polling: Not connected to device");
            return;
        }
        
        // Stop any existing polling job
        stopPolling();
        
        // Start a new polling executor
        pollingExecutor = Executors.newSingleThreadScheduledExecutor();
        pollingExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                gryphonProtocol.sendPoll();
            }
        }, 0, 500, TimeUnit.MILLISECONDS); // Poll every 500ms
        
        addLogMessage("Started continuous polling");
    }
    
    /**
     * Stop continuous polling
     */
    private void stopPolling() {
        if (pollingExecutor != null && !pollingExecutor.isShutdown()) {
            pollingExecutor.shutdownNow();
            pollingExecutor = null;
            addLogMessage("Stopped polling");
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
     */
    private void initializeConnection() {
        if (serialManager.isConnected()) {
            addLogMessage("Initializing device communication...");
            
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
                                    }
                                }
                            }, 500);
                        }
                    }
                }, 500);
            }
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
