package com.examguard.proctoring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examguard.proctoring.core.AlertEvent
import com.examguard.proctoring.core.RiskLevel
import com.examguard.proctoring.core.Severity
import com.examguard.proctoring.ui.theme.AccentBlue
import com.examguard.proctoring.ui.theme.BgCard
import com.examguard.proctoring.ui.theme.BgDark
import com.examguard.proctoring.ui.theme.BgPanel
import com.examguard.proctoring.ui.theme.TextPrimary
import com.examguard.proctoring.ui.theme.TextSecondary

/** Setup screen — ask for the candidate's name before starting. */
@Composable
fun SetupScreen(onStart: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🛡️ ExamGuard", color = AccentBlue, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Offline proctoring — no data leaves this device", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(36.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Student name (optional)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("student_name_field"),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onStart(name) },
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = BgDark),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_button"),
        ) {
            Text("Start Session", fontWeight = FontWeight.Bold)
        }
    }
}

/** Permission gate shown when CAMERA has not been granted yet. */
@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Camera access required", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "ExamGuard needs the camera to monitor the exam. Footage is analysed on-device and never uploaded.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = BgDark),
            modifier = Modifier.testTag("grant_permission_button"),
        ) { Text("Grant camera access") }
    }
}

/** Live proctoring dashboard. */
@Composable
fun DashboardScreen(
    state: ProctoringUiState,
    onFrameAvailable: @Composable (Modifier) -> Unit,
    onPauseResume: () -> Unit,
    onReset: () -> Unit,
    onEnd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .testTag("dashboard"),
    ) {
        // Camera preview area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Color.Black),
        ) {
            onFrameAvailable(Modifier.fillMaxSize())
            // HUD: face count + motion
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .background(BgPanel.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Faces: ${state.faceCount}", color = TextPrimary, fontSize = 13.sp)
                Spacer(Modifier.size(12.dp))
                Text("Motion: ${state.motionLevel}", color = TextSecondary, fontSize = 13.sp)
            }
        }

        // Risk meter
        RiskMeter(score = state.score, level = state.riskLevel)

        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onPauseResume, modifier = Modifier.weight(1f).testTag("pause_button")) {
                Text(if (state.phase == SessionPhase.PAUSED) "Resume" else "Pause")
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f).testTag("reset_button")) {
                Text("Reset")
            }
            Button(
                onClick = onEnd,
                colors = ButtonDefaults.buttonColors(containerColor = RiskLevel.CRITICAL.colorArgb.toColor()),
                modifier = Modifier.weight(1f).testTag("end_button"),
            ) { Text("End") }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "  Alerts (${state.totalAlerts})",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
        AlertList(state.recentEvents)
    }
}

@Composable
private fun RiskMeter(score: Int, level: RiskLevel) {
    val color = level.colorArgb.toColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("RISK SCORE", color = TextSecondary, fontSize = 12.sp)
                Text(
                    "$score",
                    color = color,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("score_text"),
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(color)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(level.label, color = BgDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun AlertList(events: List<AlertEvent>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(events) { event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(severityColor(event.type.severity)),
                )
                Spacer(Modifier.size(10.dp))
                Text(event.type.label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("+${event.scoreDelta}", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

/** End-of-session report. */
@Composable
fun ReportScreen(state: ProctoringUiState, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
            .testTag("report_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Session Report", color = AccentBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BgCard, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = BgPanel),
        ) {
            Text(
                state.reportText ?: "No report available.",
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        state.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = RiskLevel.HIGH.colorArgb.toColor(), fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = BgDark),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_session_button"),
        ) { Text("New Session", fontWeight = FontWeight.Bold) }
    }
}

private fun severityColor(s: Severity): Color = when (s) {
    Severity.LOW -> RiskLevel.MEDIUM.colorArgb.toColor()
    Severity.MEDIUM -> RiskLevel.HIGH.colorArgb.toColor()
    Severity.HIGH -> RiskLevel.CRITICAL.colorArgb.toColor()
}

private fun Long.toColor(): Color = Color(this)
