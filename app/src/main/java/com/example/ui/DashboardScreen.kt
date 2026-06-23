package com.example.ui

import android.hardware.usb.UsbDevice
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.animation.core.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.example.data.ServicingLog
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ServicingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State bindings
    val devices by viewModel.detectedDevices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val consoleLog by viewModel.activeConsoleLog.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val targetBrandMatch by viewModel.targetBrandMatch.collectAsState()
    val databaseLogs by viewModel.servicingLogs.collectAsState()

    val showProgressDialog by viewModel.showProgressDialog.collectAsState()
    val showActionsDialog by viewModel.showActionsDialog.collectAsState()
    val showFlashingProgressDialog by viewModel.showFlashingProgressDialog.collectAsState()
    val handshakeProgressMessage by viewModel.handshakeProgressMessage.collectAsState()
    val supportedModelInfo by viewModel.supportedModelInfo.collectAsState()
    val flashProgress by viewModel.flashProgress.collectAsState()
    val flashStatus by viewModel.flashStatus.collectAsState()
    val flashingVisualInstructions by viewModel.flashingVisualInstructions.collectAsState()
    val userBalance by viewModel.userBalance.collectAsState()
    val isFlashing by viewModel.isFlashing.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Operations, 1: History Logging, 2: Help Guides
    var selectedProtocolMode by remember { mutableStateOf("ADB") } // ADB, Fastboot, EDL, BootROM, FDL, DFU

    // For file parameters inside operations panel
    var customLoaderInput by remember { mutableStateOf("firehose_sdm845.bin") }
    var customPartitionFlash by remember { mutableStateOf("boot") }
    var customFileToFlash by remember { mutableStateOf("unlocked_system.img") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CosmicBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = Purple80,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "MOBILE SERVICING WORKBENCH",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = TerminalBg
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(CosmicBg)
        ) {
            // Live Hardware Connection Banner Highlight
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (selectedDevice != null) Purple80 else TextGray.copy(alpha = 0.3f)),
                colors = CardDefaults.cardColors(containerColor = CardSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedDevice != null) Purple80 else Color.Red)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedDevice != null) "OTG HARDWARE CONNECTED" else "NO USB DEVICE DETECTED",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedDevice != null) Purple80 else TextGray
                            )
                        }

                        IconButton(
                            onClick = { viewModel.scanForConnectedDevices() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("scan_usb_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan USB",
                                tint = CyanGlow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = targetBrandMatch,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    if (selectedDevice != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Detected Interface Class: USB Host Driver Enrolled.",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextGray
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connect phone in ADB, BootROM, EDL or Fastboot mode using OTG cable.",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextGray
                        )
                    }
                }
            }

            val isAutomatedRunning by viewModel.isAutomatedRunning.collectAsState()

            // Dynamic Automated Unbrick Flow Controller Card
            GlassmorphicContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                borderColor = if (isAutomatedRunning) CyanGlow else Purple80.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AUTOMATED UNBRICK ENGINE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAutomatedRunning) CyanGlow else Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isAutomatedRunning) {
                                "Running... Handshaking with secure Firebase Cloud Function."
                            } else if (selectedDevice != null) {
                                "OTG target detected. Automated flow listens to USB connection intents."
                            } else {
                                "Ready. Run automated flow using connected device or virtual mock."
                            },
                            fontSize = 11.sp,
                            color = TextGray
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isAutomatedRunning) {
                        RaindropSpinner(color = CyanGlow, modifier = Modifier.size(28.dp))
                    } else {
                        Button(
                            onClick = { viewModel.triggerAutomatedFlow(selectedDevice) },
                            colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("trigger_auto_flow_btn")
                        ) {
                            Text(
                                text = "RUN FLOW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Central Navigation Tabs (Operations, History Logging, Help Guides)
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = TerminalBg,
                contentColor = Purple80,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = Purple80
                    )
                }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("OPERATIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("HISTORY LOGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("SUPPORT MANUALS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
                )
            }

            // Render Active Panels dynamically
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> OperationsWorkspace(
                        selectedProtocolMode = selectedProtocolMode,
                        onProtocolChange = { selectedProtocolMode = it },
                        isProcessing = isProcessing,
                        viewModel = viewModel,
                        customLoaderInput = customLoaderInput,
                        onLoaderChange = { customLoaderInput = it },
                        customPartitionFlash = customPartitionFlash,
                        onPartitionChange = { customPartitionFlash = it },
                        customFileToFlash = customFileToFlash,
                        onFileChange = { customFileToFlash = it }
                    )
                    1 -> DatabaseLogsWorkspace(
                        databaseLogs = databaseLogs,
                        onClearLogs = { viewModel.clearHistory() }
                    )
                    2 -> TechnicalHelpManuals()
                }
            }

            // Real-time scrolling technician console terminal (Anchor Bottom)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(TerminalBg)
                    .border(1.dp, CardSlate)
            ) {
                // Console Toolbar header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSlate)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyanGlow,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = "LIVE HW TRANSACTION CONSOLE",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextGray
                        )
                    }

                    Row {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(end = 12.dp),
                                color = Purple80,
                                strokeWidth = 2.dp
                            )
                        }

                        Text(
                            text = "CLEAR",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Pink80,
                            modifier = Modifier
                                .clickable { viewModel.clearConsole() }
                                .padding(horizontal = 6.dp)
                        )
                    }
                }

                // Scrolling hex diagnostic stream lines
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = consoleLog.ifEmpty { "Awaiting USB interface initialization. Select protocol mode and connect target device to execute handshake cycles...\n" },
                        fontFamily = FontFamily.Monospace,
                        color = Purple80,
                        lineHeight = 16.sp,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }

    if (showProgressDialog) {
        ProgressOverlay(
            message = handshakeProgressMessage,
            onDismiss = { viewModel.closeProgressDialog() }
        )
    }

    if (showActionsDialog && supportedModelInfo != null) {
        ActionsOverlay(
            info = supportedModelInfo!!,
            onDismiss = { viewModel.closeActionsDialog() },
            onActionTrigger = { action ->
                viewModel.closeActionsDialog()
                if (action == "LOGS") {
                    activeTab = 1
                } else {
                    viewModel.triggerFlashingAction(action, supportedModelInfo!!)
                }
            }
        )
    }

    if (showFlashingProgressDialog) {
        FlashingProgressOverlay(
            statusMessage = flashStatus,
            progress = flashProgress,
            instructions = flashingVisualInstructions,
            isFlashing = isFlashing,
            onDismiss = { viewModel.closeFlashingProgressDialog() }
        )
    }
}

@Composable
fun OperationsWorkspace(
    selectedProtocolMode: String,
    onProtocolChange: (String) -> Unit,
    isProcessing: Boolean,
    viewModel: ServicingViewModel,
    customLoaderInput: String,
    onLoaderChange: (String) -> Unit,
    customPartitionFlash: String,
    onPartitionChange: (String) -> Unit,
    customFileToFlash: String,
    onFileChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal Chip list of standard servicing protocols
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ADB", "FASTBOOT", "AT COMMAND", "EDL (QCOM)", "BOOTROM (MTK)", "FDL (UNISOC)", "DFU (STM)").forEach { mode ->
                val isSelected = selectedProtocolMode == mode
                OutlinedButton(
                    onClick = { onProtocolChange(mode) },
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Purple80 else CardSlate
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) CardSlate else Color.Transparent
                    ),
                    modifier = Modifier.testTag("tab_protocol_${mode.replace(" ", "_")}")
                ) {
                    Text(
                        text = mode,
                        color = if (isSelected) Purple80 else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Active control card block depending on selected type
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            when (selectedProtocolMode) {
                "ADB" -> AdbControlCard(isProcessing, viewModel)
                "FASTBOOT" -> FastbootControlCard(
                    isProcessing,
                    viewModel,
                    customPartitionFlash,
                    onPartitionChange,
                    customFileToFlash,
                    onFileChange
                )
                "AT COMMAND" -> AtControlCard(isProcessing, viewModel)
                "EDL (QCOM)" -> EdlControlCard(
                    isProcessing,
                    viewModel,
                    customLoaderInput,
                    onLoaderChange
                )
                "BOOTROM (MTK)" -> MtkControlCard(isProcessing, viewModel)
                "FDL (UNISOC)" -> FdlControlCard(isProcessing, viewModel, customFileToFlash, onFileChange)
                "DFU (STM)" -> DfuControlCard(isProcessing, viewModel)
            }
        }
    }
}

@Composable
fun AdbControlCard(isProcessing: Boolean, viewModel: ServicingViewModel) {
    GlassmorphicContainer(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Purple80
    ) {
        Column {
            Text(
                text = "ADB INTERFACE CONTROLLER",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyanGlow
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Connects via standard Android Debug endpoint. Allows direct service queries, boot redirection pipelines, and secure state provisions.",
                fontSize = 11.5.sp,
                color = TextGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RaindropSpinner(color = Purple80)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "TRANSMITTING ADB PACKETS...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Purple80,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.performAdbOperation("INFO") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adb_btn_info")
                    ) {
                        Text("READ DEVICE INFO", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.performAdbOperation("REBOOT_BOOTLOADER") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adb_btn_bootloader")
                    ) {
                        Text("REBOOT BOOTLOADER", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.performAdbOperation("REBOOT_EDL") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adb_btn_edl")
                    ) {
                        Text("REBOOT TO EDL", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.performAdbOperation("BYPASS_FRP") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pink40),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adb_btn_frp")
                    ) {
                        Text("SECURE WORKFLOW BYPASS", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

val CardBg = Color(0xFF202A3C)

@Composable
fun FastbootControlCard(
    isProcessing: Boolean,
    viewModel: ServicingViewModel,
    partition: String,
    onPartitionChange: (String) -> Unit,
    fileName: String,
    onFileChange: (String) -> Unit
) {
    GlassmorphicContainer(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Purple80
    ) {
        Column {
            Text(
                text = "FASTBOOT SERVICE MANAGER",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyanGlow
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Reads fastboot hardware parameters, performs partition erasures, writes images, and executes OEM state locks.",
                fontSize = 11.5.sp,
                color = TextGray
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RaindropSpinner(color = Purple80)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "FLASHING BLOCKS & SYNCHRONIZING...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Purple80,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = partition,
                    onValueChange = onPartitionChange,
                    label = { Text("Target Partition", color = TextGray, fontSize = 11.sp) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple80,
                        unfocusedBorderColor = CardBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = fileName,
                    onValueChange = onFileChange,
                    label = { Text("Flashing Image File", color = TextGray, fontSize = 11.sp) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple80,
                        unfocusedBorderColor = CardBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.performFastbootOperation("READ_INFO") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("READ STATE", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = { viewModel.performFastbootOperation("FLASH", partition) },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("FLASH IMAGE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.performFastbootOperation("UNLOCK") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pink40),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OEM UNLOCK", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    }

                    Button(
                        onClick = { viewModel.performFastbootOperation("ERASE", partition) },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ERASE PARTITION", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun EdlControlCard(
    isProcessing: Boolean,
    viewModel: ServicingViewModel,
    loader: String,
    onLoaderChange: (String) -> Unit
) {
    GlassmorphicContainer(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Purple80
    ) {
        Column {
            Text(
                text = "EDL WORKSPACE (QUALCOMM SAHARA & FIREHOSE)",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyanGlow
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Runs low-level physical syncs over Sahara endpoints. Allows flashing custom Firehose programmers to read partition mapping and erase device settings.",
                fontSize = 11.5.sp,
                color = TextGray
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RaindropSpinner(color = Purple80)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SAHARA TRANSFER MODE IN ACTION...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Purple80,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = loader,
                    onValueChange = onLoaderChange,
                    label = { Text("Firehose Bin Loader Input", color = TextGray, fontSize = 11.sp) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple80,
                        unfocusedBorderColor = CardBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.performEdlOperation("LOAD_FIREHOSE", loader) },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("LOAD FIREHOSE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    }

                    Button(
                        onClick = { viewModel.performEdlOperation("READ_GPT") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("READ DEVICE GPT", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.performEdlOperation("WIPE_FRP") },
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink40),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ERASE COMPRESSED FRP BLOCKS", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun MtkControlCard(isProcessing: Boolean, viewModel: ServicingViewModel) {
    GlassmorphicContainer(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Purple80
    ) {
        Column {
            Text(
                text = "MTK BOOTROM SERVICING PANEL",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyanGlow
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manages serial synchronizations with MediaTek Preloader and BootROM drivers. Runs watchdog disable cycles and secure SLA authentication bypass bridges.",
                fontSize = 11.5.sp,
                color = TextGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RaindropSpinner(color = Purple80)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "BYPASSING SLA BOOT PROTECTION...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Purple80,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.performBootromOperation("READ_SOC_ID") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("READ SOC CHIP ID", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = { viewModel.performBootromOperation("BYPASS_SLA") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pink40),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("BYPASS SLA / DAA", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun FdlControlCard(isProcessing: Boolean, viewModel: ServicingViewModel, flashFile: String, onFileChange: (String) -> Unit) {
    GlassmorphicContainer(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Purple80
    ) {
        Column {
            Text(
                text = "UNISOC FDL WORKSTATION",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyanGlow
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Performs Unisoc calibration handshakes via HDLC packet frames. Loads first-level (FDL1) and second-level (FDL2) loader binaries to edit blocks.",
                fontSize = 11.5.sp,
                color = TextGray
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RaindropSpinner(color = Purple80)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "STUFFING HDLC PACKET BUFFER...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Purple80,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = flashFile,
                    onValueChange = onFileChange,
                    label = { Text("FDL Binary File Name", color = TextGray, fontSize = 11.sp) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple80,
                        unfocusedBorderColor = CardBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.performFdlOperation("FLASH_PAC", flashFile) },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("FLASH LOADER pac", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    }

                    Button(
                        onClick = { viewModel.performFdlOperation("RESET_FRP") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("RESET UNISOC FRP", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun DfuControlCard(isProcessing: Boolean, viewModel: ServicingViewModel) {
    GlassmorphicContainer(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Purple80
    ) {
        Column {
            Text(
                text = "USB DFU CONTROLS",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyanGlow
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Addresses standard Device Firmware Upgrade interfaces via USB Endpoint 0. Supports querying status registers, forcing download resets, and software syncs.",
                fontSize = 11.5.sp,
                color = TextGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RaindropSpinner(color = Purple80)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "STREAMPATH ENDPOINT INTERACTION...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Purple80,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.performDfuOperation("GET_STATUS") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("GET STATE STATUS", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = { viewModel.performDfuOperation("CLR_STATUS") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CLEAR STATE STUCK", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.performDfuOperation("ABORT") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ABORT TRANSFER", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = { viewModel.performDfuOperation("DETACH") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pink40),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("FORCE DETACH DFU", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun DatabaseLogsWorkspace(
    databaseLogs: List<ServicingLog>,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SERVICING SESSION HISTORY (" + databaseLogs.size + " logged)",
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Button(
                onClick = onClearLogs,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pink40)
            ) {
                Text(
                    text = "WIPE SQL ARCHIVES",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (databaseLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No prior servicing events archived.",
                        fontSize = 13.sp,
                        color = TextGray
                    )
                    Text(
                        text = "Completed handshakes will appear here.",
                        fontSize = 11.sp,
                        color = TextGray.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(databaseLogs) { log ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        border = BorderStroke(1.dp, CardBg),
                        colors = CardDefaults.cardColors(containerColor = CardSlate)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (log.status == "SUCCESS") Purple40 else Pink40)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = log.protocol,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = log.operation,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Text(
                                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextGray
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Device: ${log.deviceName}",
                                fontSize = 12.sp,
                                color = TextGray
                            )

                            if (log.details.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Details: ${log.details}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Purple80
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TechnicalHelpManuals() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "TECHNICAL PROTOCOLS REFERENCE MANUAL",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Purple80
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Guide for using OTG mobile hardware programming loops safely.",
                fontSize = 11.sp,
                color = TextGray
            )
        }

        item {
            ManualCard(
                title = "1. QUALCOMM Sahara & Firehose",
                content = "Sahara interface runs on boot. In this mode, the Qualcomm chip listens for Hello packets (0x01). The host responds with Hello Response (0x02) which sets up memory mapping. Upon acknowledgement, Sahara opens channels allowing the transfer of Firehose files. Firehose executes over bulk interfaces using strict high-level XML communication protocols."
            )
        }

        item {
            ManualCard(
                title = "2. MEDIATEK Preloader & BootROM",
                content = "MediaTek systems initialize handshakes by listening for specific sync pulses (0xA0) over high-speed UART interfaces. Upon matching, device sends back registers, SoC descriptors, and chip identification. Technicians utilize watchdog clock overrides to bypass secure handshakes (SLA/DAA) preventing unauthorized flashing."
            )
        }

        item {
            ManualCard(
                title = "3. UNISOC Spreadtrum FDL",
                content = "FDL protocols operate using high-level DL/HDLC packages bounded by 0x7E boundaries. Escaping commands like 0x7D must translate properly to avoid transmission syntax errors. FDL loads loader blocks into CPU cache enabling flashing PAC firmware packages over USB Host serial."
            )
        }

        item {
            ManualCard(
                title = "4. USB DFU (Device Firmware Upgrade)",
                content = "DFU utilizes standardized USB command structures mapped to USB Control Endpoint 0. Host implements DFU_DNLOAD configurations to stream file packets sequentially and monitors status frames to guarantee checksum calculations on-device."
            )
        }

        item {
            ManualCard(
                title = "5. Firestore Rules & Document Schema",
                content = "FIRESTORE PATHS:\n" +
                        " - users/{userId} (credits: int, role: string)\n" +
                        " - audit_logs/{logId} (uid: string, imei: string, type: string, timestamp: timestamp)\n" +
                        " - provisioning_payloads/{payloadId} (vid: string, pid: string, cid: string, payloadUrl: string)\n\n" +
                        "RULES:\n" +
                        "service cloud.firestore {\n" +
                        "  match /databases/{database}/documents {\n" +
                        "    match /users/{userId} {\n" +
                        "      allow read: if request.auth != null && request.auth.uid == userId;\n" +
                        "      allow write: if false; // Only Cloud Functions can increment/decrement balances\n" +
                        "    }\n" +
                        "    match /audit_logs/{id} {\n" +
                        "      allow read: if request.auth != null && request.auth.uid == resource.data.uid;\n" +
                        "      allow write: if false;\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"
            )
        }

        item {
            ManualCard(
                title = "6. Firebase Cloud Function v2 (Node.js)",
                content = "EXPORTS FUNCTION:\n" +
                        "const { onCall, HttpsError } = require('firebase-functions/v2/https');\n" +
                        "const admin = require('firebase-admin');\n" +
                        "admin.initializeApp();\n\n" +
                        "exports.verifyBalanceAndRetrievePayload = onCall(async (request) => {\n" +
                        "  if (!request.auth) throw new HttpsError('unauthenticated', 'User must be signed in.');\n" +
                        "  const uid = request.auth.uid;\n" +
                        "  const { vid, pid, cid } = request.data;\n" +
                        "  const userRef = admin.firestore().collection('users').doc(uid);\n\n" +
                        "  return await admin.firestore().runTransaction(async (transaction) => {\n" +
                        "    const userDoc = await transaction.get(userRef);\n" +
                        "    if (!userDoc.exists || userDoc.data().credits < 1) {\n" +
                        "      throw new HttpsError('failed-precondition', 'Insufficient unbrick credits.');\n" +
                        "    }\n" +
                        "    // Decrement 1 wallet token safely\n" +
                        "    transaction.update(userRef, { credits: userDoc.data().credits - 1 });\n" +
                        "    // Record transaction audit trace\n" +
                        "    const auditRef = admin.firestore().collection('audit_logs').doc();\n" +
                        "    transaction.set(auditRef, {\n" +
                        "      uid, imei: `\${vid}:\${pid}:\${cid}`, type: 'AUTO_UNBRICK', timestamp: admin.firestore.FieldValue.serverTimestamp()\n" +
                        "    });\n" +
                        "    // Query payload URL dynamically from repository collection...\n" +
                        "    return { success: true, payloadUrl: `https://storage.googleapis.com/unbrick-payloads/\${vid}_\${pid}_recovery.zip` };\n" +
                        "  });\n" +
                        "});"
            )
        }
    }
}

@Composable
fun ManualCard(title: String, content: String) {
    GlassmorphicContainer(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Purple80.copy(alpha = 0.4f)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = content,
                fontSize = 11.5.sp,
                color = TextGray,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun GlassmorphicContainer(
    modifier: Modifier = Modifier,
    borderColor: Color = Purple80,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x33161C2A), // Elegant semi-transparent Card Slate glaze
                        Color(0xEE05070B)  // Premium liquid terminal backing
                    )
                )
            )
            .border(
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            borderColor.copy(alpha = 0.8f),
                            Color(0x228F9CAE),
                            borderColor.copy(alpha = 0.05f)
                        )
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
fun RaindropSpinner(
    modifier: Modifier = Modifier,
    color: Color = PurpleGrey80,
    dropletCount: Int = 10
) {
    val transition = rememberInfiniteTransition(label = "RaindropSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(
        modifier = modifier
            .size(48.dp)
            .padding(4.dp)
            .testTag("raindrop_spinner")
    ) {
        val radius = size.minDimension / 2.2f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)

        rotate(rotation, center) {
            for (i in 0 until dropletCount) {
                val angleRad = (i * 2 * Math.PI / dropletCount).toFloat()
                val alpha = (i + 1).toFloat() / dropletCount
                
                val dropRadius = 2.dp.toPx() + (3.dp.toPx() * alpha)
                val x = center.x + radius * kotlin.math.cos(angleRad)
                val y = center.y + radius * kotlin.math.sin(angleRad)

                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = dropRadius,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
                
                drawCircle(
                    color = CyanGlow.copy(alpha = alpha * 0.25f),
                    radius = dropRadius * 1.8f,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
            }
        }
    }
}

@Composable
fun AtControlCard(isProcessing: Boolean, viewModel: ServicingViewModel) {
    var customCommandText by remember { mutableStateOf("AT") }
    
    val presetCommands = listOf(
        "AT" to "PING",
        "ATI" to "INFO",
        "AT+CGMI" to "VENDOR",
        "AT+CGMM" to "MODEL",
        "AT+CGSN" to "IMEI",
        "AT+COPS?" to "CARRIER",
        "AT+CSQ" to "SIGNAL",
        "AT+CBC" to "BATTERY"
    )

    GlassmorphicContainer(
        modifier = Modifier.fillMaxWidth(),
        borderColor = CyanGlow
    ) {
        Text(
            text = "HAYES AT MODEM WORKSPACE",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = CyanGlow
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Queries mobile transceiver diagnostics & band carrier parameters over USB Serial interface. Supports preset baseband requests or custom manual payloads.",
            fontSize = 11.5.sp,
            color = TextGray
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RaindropSpinner(color = CyanGlow)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "TRANSMITTING AT CARRIER COMMANDS...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = CyanGlow,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Text(
                text = "Preset Commands:",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextGray,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presetCommands.forEach { (cmd, label) ->
                    OutlinedButton(
                        onClick = { 
                            customCommandText = cmd
                            viewModel.performAtOperation(cmd) 
                        },
                        border = BorderStroke(1.dp, CardBg),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = cmd, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Purple80)
                            Text(text = label, fontSize = 8.5.sp, color = TextGray)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = customCommandText,
                onValueChange = { customCommandText = it },
                label = { Text("Manual AT Command Entry", color = TextGray, fontSize = 11.sp) },
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanGlow,
                    unfocusedBorderColor = CardBg
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = CyanGlow,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("custom_at_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.performAtOperation(customCommandText) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                modifier = Modifier.fillMaxWidth().testTag("execute_at_button")
            ) {
                Text(
                    text = "EXECUTE AT TRANSCEIVER TASK",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun ProgressOverlay(
    message: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardSlate)
                .border(2.dp, CyanGlow, RoundedCornerShape(16.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "SECURE TRANSCEIVER CHECK",
                    color = CyanGlow,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Spinner
                RaindropSpinner(color = CyanGlow, modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Please keep your OTG connected...",
                    color = TextGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ActionsOverlay(
    info: SupportedModelInfo,
    onDismiss: () -> Unit,
    onActionTrigger: (String) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardSlate)
                .border(2.dp, Purple80, RoundedCornerShape(16.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modelLogo = if (info.logo == "xiaomi_logo") {
                        Icons.Default.Build
                    } else {
                        Icons.Default.CheckCircle
                    }
                    Icon(
                        imageVector = modelLogo,
                        contentDescription = null,
                        tint = Purple80,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SECURE DEPLOYMENT LOCKED",
                        color = Purple80,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Model Detail Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TerminalBg)
                        .border(1.dp, TextGray.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("TARGET BRAND:", color = TextGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text(info.brand, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("MODEL ID:", color = TextGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text(info.modelName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("CHIPSET:", color = TextGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text(info.chipSet, color = PurpleGrey80, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("HARDWARE SIGNATURE:", color = TextGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("VID:${info.vid} PID:${info.pid}", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Wallet credit display matching described serverless transaction
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACCOUNT WALLET STATUS:",
                        color = TextGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                    Text(
                        text = "${info.userBalance} CR AVAILABLE",
                        color = CyanGlow,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "AUTHORIZED OPERATION BLOCKS:",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Beautiful action panel containing buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    info.actions.forEach { action ->
                        val icon = when (action) {
                            "FACTORY RESET" -> Icons.Default.Delete
                            "BL UNLOCK" -> Icons.Default.Lock
                            "UNBRICK (EDL)" -> Icons.Default.Refresh
                            "FRP BYPASS" -> Icons.Default.Send
                            "FLASH FIRMWARE" -> Icons.Default.PlayArrow
                            "LOGS" -> Icons.Default.List
                            else -> Icons.Default.Star
                        }
                        
                        Button(
                            onClick = { onActionTrigger(action) },
                            colors = ButtonDefaults.buttonColors(containerColor = CardSlate),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CyanGlow.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .testTag("secure_action_${action.replace(" ", "_").lowercase()}"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = CyanGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = action,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Purple80,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_actions_btn")
                ) {
                    Text(
                        text = "CLOSE INTERFACES",
                        color = Pink80,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FlashingProgressOverlay(
    statusMessage: String,
    progress: Float,
    instructions: String,
    isFlashing: Boolean,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!isFlashing) onDismiss() }) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardSlate)
                .border(2.dp, CyanGlow, RoundedCornerShape(16.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Spinning indicator + Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isFlashing) {
                        CircularProgressIndicator(
                            color = CyanGlow,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = if (isFlashing) "SECURE FLASH PIPELINE ACTIVE" else "FLASH SEQUENCE COMPLETED",
                        color = if (isFlashing) CyanGlow else Purple80,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Progress Info & Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "TRANSMISSION BLOCKS:",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextGray
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Glowy M3 Linear Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = CyanGlow,
                    trackColor = TerminalBg
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Current Action / Status text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalBg)
                        .border(1.dp, TextGray.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = statusMessage,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Instructions Banner (if provided)
                if (instructions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Purple80.copy(alpha = 0.08f))
                            .border(1.dp, Purple80.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "REQUIRED HANDSHAKE SEQUENCE:",
                            color = Purple80,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = instructions,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Status Alert
                Text(
                    text = "Do NOT disconnect OTG pipeline cable...",
                    color = Pink80.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic
                )

                if (!isFlashing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dismiss_flashing_progress_btn")
                    ) {
                        Text(
                            text = "FINALIZE & CLOSE",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
