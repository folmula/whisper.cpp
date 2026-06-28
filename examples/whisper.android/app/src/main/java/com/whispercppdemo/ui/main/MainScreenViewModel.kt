package com.whispercppdemo.ui.main

import android.app.Application
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.recorder.Recorder
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "MainScreenViewModel"

class MainScreenViewModel(private val application: Application) : ViewModel() {

    var canTranscribe by mutableStateOf(false)
        private set
    var dataLog by mutableStateOf("")
        private set
    var isRecording by mutableStateOf(false)
        private set

    private val modelsPath = File(application.filesDir, "models")
    private val samplesPath = File(application.filesDir, "samples")
    private var recorder: Recorder = Recorder()
    private var whisperContext: WhisperContext? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordedFile: File? = null

    init {
        viewModelScope.launch {
            printMessage("Loading data...\n")
            try {
                copyAssets()
                loadBaseModel()
                canTranscribe = true
            } catch (e: Exception) {
                Log.w(LOG_TAG, e)
                printMessage("${e.localizedMessage}\n")
            }
        }
    }

    private suspend fun printMessage(msg: String) = withContext(Dispatchers.Main) {
        dataLog += msg
    }

    private suspend fun copyAssets() = withContext(Dispatchers.IO) {
        modelsPath.mkdirs()
        samplesPath.mkdirs()
        application.copyData("samples", samplesPath, ::printMessage)
        printMessage("All data copied to working directory.\n")
    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        printMessage("앱이 켜졌습니다. 상단의 '1. 모델 선택' 버튼을 눌러 터보 모델(.bin)을 등록해 주세요.\n")
    }

    fun benchmark() = viewModelScope.launch {
        val modelFile = java.io.File(application.cacheDir, "custom_model.bin")
        if (modelFile.exists()) {
            whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
            printMessage("✅ 모델 로딩 완료! 이제 '2. 파일 선택(음성)' 버튼을 눌러주세요.\n")
            canTranscribe = true
        } else {
            printMessage("❌ 모델 파일 복사 실패\n")
        }
    }

    fun transcribeSample() = viewModelScope.launch {
        transcribeAudio(java.io.File(application.cacheDir, "custom.wav"))
    }

    private suspend fun runBenchmark(nthreads: Int) {
        if (!canTranscribe) return
        canTranscribe = false
        printMessage("Running benchmark. This will take minutes...\n")
        whisperContext?.benchMemory(nthreads)?.let { printMessage(it) }
        printMessage("\n")
        whisperContext?.benchGgmlMulMat(nthreads)?.let { printMessage(it) }
        canTranscribe = true
    }

    private suspend fun getFirstSample(): File = withContext(Dispatchers.IO) {
        samplesPath.listFiles()!!.first()
    }

    private suspend fun readAudioSamples(file: File): FloatArray = withContext(Dispatchers.IO) {
        stopPlayback()
        startPlayback(file)
        return@withContext decodeWaveFile(file)
    }

    private suspend fun stopPlayback() = withContext(Dispatchers.Main) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private suspend fun startPlayback(file: File) = withContext(Dispatchers.Main) {
        mediaPlayer = MediaPlayer.create(application, file.absolutePath.toUri())
        mediaPlayer?.start()
    }

    private suspend fun transcribeAudio(file: File) {
        if (!canTranscribe) return
        canTranscribe = false
        try {
            printMessage("Reading audio samples...\n")
            val data = readAudioSamples(file)
            printMessage("Transcribing data...\n")
            val start = System.currentTimeMillis()
            val text = whisperContext?.transcribeData(data)
            val elapsed = System.currentTimeMillis() - start
            printMessage("Done ($elapsed ms): $text\n")
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
        canTranscribe = true
    }

    fun toggleRecord() = viewModelScope.launch {
        try {
            if (isRecording) {
                recorder.stopRecording()
                isRecording = false
                recordedFile?.let { transcribeAudio(it) }
            } else {
                stopPlayback()
                val file = File(application.filesDir, "record.wav")
                recorder.startRecording(file) { e ->
                    viewModelScope.launch {
                        withContext(Dispatchers.Main) {
                            printMessage("${e.localizedMessage}\n")
                            isRecording = false
                        }
                    }
                }
                isRecording = true
                recordedFile = file
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isRecording = false
        }
    }

    override fun onCleared() {
        runCatching {
            whisperContext?.release()
            whisperContext = null
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainScreenViewModel(application)
            }
        }
    }
}

suspend fun Application.copyData(
    prefix: String,
    dir: File,
    printLog: suspend (String) -> Unit
) = withContext(Dispatchers.IO) {
    assets.list(prefix)?.forEach { asset ->
        val file = File(dir, asset)
        if (file.exists()) {
            printLog("${file.absolutePath} already exists...\n")
        } else {
            printLog("Copying $asset...\n")
            assets.open("$prefix/$asset").use { inputStream ->
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            printLog("Copied $asset!\n")
        }
    }
}
