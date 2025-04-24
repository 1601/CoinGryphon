package com.example.coingryphon

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coingryphon.serial.GryphonProtocol
import com.example.coingryphon.serial.GryphonSerialManager
import com.example.coingryphon.ui.theme.CoinGryphonTheme
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    private lateinit var serialManager: GryphonSerialManager
    private lateinit var gryphonProtocol: GryphonProtocol
    
    // UI state variables
    private var connectionStatus by mutableStateOf("Disconnected")
    private var lastResponse by mutableStateOf("No response yet")
    private var logMessages = mutableStateListOf<String>()
    
    // Coroutine for polling
    private var pollingJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize the serial manager
        serialManager = GryphonSerialManager.getInstance(this)
        gryphonProtocol = GryphonProtocol(serialManager)
        
        // Set up the protocol response listener
        setupResponseListener()
        
        setContent {
            CoinGryphonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GryphonUI()
                }
            }
        }
    }
    
    @Composable
    fun GryphonUI() {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Coin Gryphon Controller",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Connection status
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Connection Status: $connectionStatus",
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            Button(
                                onClick = { connectToDevice() },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Connect")
                            }
                            
                            Button(
                                onClick = { disconnectDevice() }
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
                
                // Control buttons
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Commands",
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            Button(
                                onClick = { sendPollCommand() },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Poll")
                            }
                            
                            Button(
                                onClick = { sendResetCommand() },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Reset")
                            }
                            
                            Button(
                                onClick = { sendSetupCommand() },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Setup")
                            }
                            
                            Button(
                                onClick = { sendCoinTypeCommand() }
                            ) {
                                Text("Coin Type")
                            }
                        }
                        
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            Button(
                                onClick = { startPolling() },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Start Polling")
                            }
                            
                            Button(
                                onClick = { stopPolling() }
                            ) {
                                Text("Stop Polling")
                            }
                        }
                    }
                }
                
                // Response display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Last Response: $lastResponse",
                            fontWeight = FontWeight.Medium
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            text = "Log:",
                            fontWeight = FontWeight.Medium
                        )
                        
                        // Log messages in a scrollable list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 8.dp)
                        ) {
                            items(logMessages.size) { index ->
                                Text(logMessages[logMessages.size - index - 1])
                            }
                        }
                        
                        Button(
                            onClick = { logMessages.clear() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Clear Log")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Set up the response listener for Gryphon protocol commands
     */
    private fun setupResponseListener() {
        gryphonProtocol.setResponseListener(object : GryphonProtocol.GryphonResponseListener {
            override fun onCommandResponse(
                command: GryphonProtocol.CommandType,
                status: GryphonProtocol.ResponseStatus,
                responseData: ByteArray?
            ) {
                // Update UI with response
                val responseHex = responseData?.let { GryphonSerialManager.bytesToHexString(it) } ?: "No data"
                val responseText = "$command - $status: $responseHex"
                
                lastResponse = responseHex
                logMessages.add(responseText)
                
                // Handle coin acceptance
                if (status == GryphonProtocol.ResponseStatus.COIN_ACCEPTED) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Coin Accepted!", Toast.LENGTH_SHORT).show()
                    }
                }
                
                Log.d(TAG, "Command response: $responseText")
            }
        })
        
        // Set up serial manager listener for connection events
        serialManager.setListener(object : GryphonSerialManager.GryphonSerialListener {
            override fun onReceiveData(data: ByteArray) {
                // Data processing is handled by the protocol class
            }
            
            override fun onConnectionEstablished() {
                connectionStatus = "Connected"
                logMessages.add("Connection established with device")
            }
            
            override fun onConnectionLost() {
                connectionStatus = "Disconnected"
                logMessages.add("Connection lost with device")
                stopPolling() // Stop polling if connection is lost
            }
            
            override fun onError(e: Exception) {
                connectionStatus = "Error: ${e.message}"
                logMessages.add("Error: ${e.message}")
            }
        })
    }
    
    /**
     * Connect to the Gryphon device
     */
    private fun connectToDevice() {
        if (serialManager.connectToDevice()) {
            logMessages.add("Connecting to device...")
        } else {
            logMessages.add("No USB devices found or permission denied")
        }
    }
    
    /**
     * Disconnect from the Gryphon device
     */
    private fun disconnectDevice() {
        serialManager.closeConnection()
        connectionStatus = "Disconnected"
        logMessages.add("Disconnected from device")
        stopPolling() // Stop polling when disconnected
    }
    
    /**
     * Send a poll command to check for coin acceptance
     */
    private fun sendPollCommand() {
        if (serialManager.isConnected()) {
            if (gryphonProtocol.sendPoll()) {
                logMessages.add("Sent Poll command")
            } else {
                logMessages.add("Failed to send Poll command")
            }
        } else {
            logMessages.add("Not connected to device")
        }
    }
    
    /**
     * Send a reset command to the Gryphon
     */
    private fun sendResetCommand() {
        if (serialManager.isConnected()) {
            if (gryphonProtocol.sendReset()) {
                logMessages.add("Sent Reset command")
            } else {
                logMessages.add("Failed to send Reset command")
            }
        } else {
            logMessages.add("Not connected to device")
        }
    }
    
    /**
     * Send a setup command to the Gryphon
     */
    private fun sendSetupCommand() {
        if (serialManager.isConnected()) {
            if (gryphonProtocol.sendSetup()) {
                logMessages.add("Sent Setup command")
            } else {
                logMessages.add("Failed to send Setup command")
            }
        } else {
            logMessages.add("Not connected to device")
        }
    }
    
    /**
     * Send a coin type command to the Gryphon
     */
    private fun sendCoinTypeCommand() {
        if (serialManager.isConnected()) {
            if (gryphonProtocol.sendCoinType()) {
                logMessages.add("Sent Coin Type command")
            } else {
                logMessages.add("Failed to send Coin Type command")
            }
        } else {
            logMessages.add("Not connected to device")
        }
    }
    
    /**
     * Start continuous polling for coins
     */
    private fun startPolling() {
        if (!serialManager.isConnected()) {
            logMessages.add("Cannot start polling: Not connected to device")
            return
        }
        
        // Stop any existing polling job
        stopPolling()
        
        // Start a new polling coroutine
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            logMessages.add("Started continuous polling")
            while (isActive) {
                gryphonProtocol.sendPoll()
                delay(500) // Poll every 500ms
            }
        }
    }
    
    /**
     * Stop continuous polling
     */
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        logMessages.add("Stopped polling")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        serialManager.destroy() // Clean up resources
    }
}