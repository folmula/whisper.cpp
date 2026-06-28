package com.whispercppdemo.ui.main

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whispercppdemo.media.decodeWaveFile
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "MainScreenViewModel"

class MainScreenViewModel(private val application: Application) : ViewModel() {
    
    var canTranscribe by mutableStateOf(false)
        private set
    var dataLog by mutableStateOf("")
        private set

    private var whisperContext: WhisperContext? = null

    init {
        viewModelScope.launch {
            printMessage("앱이 준비되었습니다. 상단의 '1. 모델 선택' 버튼을 눌러 .bin 모델을 로드하세요.\n")
        }
    }

    private suspend fun printMessage(msg: String) = withContext(Dispatchers.Main) {
        dataLog += msg
    }

    // 모델 파일을 내부로 복사한 뒤 엔진에 장착하는 함수
    fun loadCustomModel() = viewModelScope.launch {
        val modelFile = File(application.cacheDir, "custom_model.bin")
        if (modelFile.exists()) {
            printMessage("모델 로딩 중... 잠시만 기다려주세요.\n")
            try {
                // 백그라운드 스레드에서 무거운 모델 로딩
                withContext(Dispatchers.IO) {
                    whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
                }
                printMessage("✅ 모델 로딩 완료! '2. 음성 선택' 버튼이 활성화되었습니다.\n\n")
                canTranscribe = true
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Model load error", e)
                printMessage("❌ 모델 로딩 실패: ${e.localizedMessage}\n")
            }
        } else {
            printMessage("❌ 복사된 모델 파일을 찾을 수 없습니다.\n")
        }
    }

    // 오디오 파일을 내부로 복사한 뒤 변환을 시작하는 함수
    fun transcribeCustomAudio() = viewModelScope.launch {
        if (!canTranscribe) return@launch
        
        val audioFile = File(application.cacheDir, "custom.wav")
        if (!audioFile.exists()) {
            printMessage("❌ 복사된 오디오 파일을 찾을 수 없습니다.\n")
            return@launch
        }

        canTranscribe = false
        try {
            printMessage("오디오 데이터 해독 중...\n")
            // C++ 엔진이 읽을 수 있게 오디오 데이터를 숫자로 변환
            val data = withContext(Dispatchers.IO) { decodeWaveFile(audioFile) }
            
            printMessage("🚀 하드웨어 가속 텍스트 변환 시작...\n")
            val start = System.currentTimeMillis()
            
            // STT 핵심 연산
            val text = withContext(Dispatchers.Default) {
                whisperContext?.transcribeData(data)
            }
            
            val elapsed = System.currentTimeMillis() - start
            printMessage("✅ 변환 완료 (소요시간: ${elapsed}ms):\n\n$text\n\n")
            
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Transcription error", e)
            printMessage("❌ 변환 실패: ${e.localizedMessage}\n")
        }
        canTranscribe = true
    }

    // 앱 종료 시 코루틴 규칙에 맞게 메모리를 안전하게 비우는 함수 (이전 에러 해결 부분)
    override fun onCleared() {
        runBlocking {
            try {
                whisperContext?.release()
                whisperContext = null
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Release error", e)
            }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainScreenViewModel(application)
            }
        }
    }
}
