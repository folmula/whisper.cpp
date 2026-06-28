package com.whispercppdemo.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.concurrent.thread

@Composable
fun MainScreen(viewModel: MainScreenViewModel) {
    val context = LocalContext.current

    // 1. 모델 파일(.bin) 선택 및 복사 런처
    val modelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            thread {
                try {
                    context.contentResolver.openInputStream(it)?.use { input ->
                        File(context.cacheDir, "custom_model.bin").outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        viewModel.loadCustomModel()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    // 2. 오디오 파일(.wav) 선택 및 복사 런처
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            thread {
                try {
                    context.contentResolver.openInputStream(it)?.use { input ->
                        File(context.cacheDir, "custom.wav").outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        viewModel.transcribeCustomAudio()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    // 매우 직관적이고 심플한 UI 레이아웃
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { modelLauncher.launch("*/*") }) {
                Text("1. 모델 선택 (.bin)")
            }
            Button(
                onClick = { audioLauncher.launch("audio/*") },
                enabled = viewModel.canTranscribe
            ) {
                Text("2. 음성 선택 (.wav)")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 텍스트 변환 결과 및 로그 출력창
        Text(
            text = viewModel.dataLog,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        )
    }
}
