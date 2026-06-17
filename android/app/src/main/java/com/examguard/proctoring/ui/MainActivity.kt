package com.examguard.proctoring.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examguard.proctoring.ui.theme.BgDark
import com.examguard.proctoring.ui.theme.ExamGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExamGuardTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgDark) {
                    ProctoringApp()
                }
            }
        }
    }
}

@Composable
fun ProctoringApp(viewModel: ProctoringViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    when (state.phase) {
        SessionPhase.SETUP -> SetupScreen(onStart = viewModel::startSession)

        SessionPhase.RUNNING, SessionPhase.PAUSED -> {
            if (!hasCameraPermission) {
                PermissionScreen(onRequest = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                })
            } else {
                DashboardScreen(
                    state = state,
                    onFrameAvailable = { modifier ->
                        CameraPreview(
                            onFrame = viewModel::onFrame,
                            onError = { /* surfaced via logcat; engine keeps running */ },
                            modifier = modifier,
                        )
                    },
                    onPauseResume = {
                        if (state.phase == SessionPhase.PAUSED) viewModel.resume() else viewModel.pause()
                    },
                    onReset = viewModel::resetScore,
                    onEnd = viewModel::endSession,
                )
            }
        }

        SessionPhase.REPORT -> ReportScreen(state = state, onDone = viewModel::backToSetup)
    }
}
