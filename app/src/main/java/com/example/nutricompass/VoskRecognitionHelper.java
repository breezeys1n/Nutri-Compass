package com.example.nutricompass;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.vosk.LibVosk;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.LogLevel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class VoskRecognitionHelper {
    private static final String TAG = "VoskRecognition";

    // 音频参数
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize;

    private AudioRecord audioRecord;
    private Recognizer recognizer;
    private Model model;
    private RecognitionThread recognitionThread;
    private volatile boolean isRecording = false;

    private WeakReference<Context> contextRef;
    private RecognitionCallback callback;
    private Handler mainHandler;

    // 保存部分结果的缓冲区
    private List<String> partialResultsBuffer = new ArrayList<>();
    private static final int MAX_PARTIAL_RESULTS = 10;

    public interface RecognitionCallback {
        void onResult(String text);
        void onPartialResult(String text);
        void onError(String error);
        void onStatus(String status);
        void onRecordingStarted();
        void onRecordingStopped();
    }

    public VoskRecognitionHelper(Context context, RecognitionCallback callback) {
        this.contextRef = new WeakReference<>(context);
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        Log.d(TAG, "AudioRecord buffer size: " + bufferSize);
    }

    public void initModel() {
        new Thread(() -> {
            try {
                Context context = contextRef.get();
                if (context == null) return;

                LibVosk.setLogLevel(LogLevel.INFO);
                mainHandler.post(() -> callback.onStatus("正在初始化Vosk引擎..."));

                // 根据日志，模型在assets中的确切路径
                final String modelAssetPath = "vosk-model-small-cn-0.22";
                final File modelDir = new File(context.getFilesDir(), "vosk-model");

                Log.i(TAG, "模型assets路径: " + modelAssetPath);
                Log.i(TAG, "目标存储路径: " + modelDir.getAbsolutePath());

                if (!isModelValid(modelDir)) {
                    mainHandler.post(() -> callback.onStatus("正在复制模型文件，首次使用较慢..."));

                    // 删除旧的模型文件夹
                    if (modelDir.exists()) {
                        deleteDirectory(modelDir);
                    }

                    // 创建模型目录
                    modelDir.mkdirs();

                    // 从assets复制模型文件
                    mainHandler.post(() -> callback.onStatus("正在复制模型文件..."));

                    // 记录assets中模型文件夹的内容
                    logAssetsContent(context.getAssets(), modelAssetPath);

                    boolean copySuccess = copyAssetsFolderRecursive(context.getAssets(), modelAssetPath, modelDir.getAbsolutePath());

                    if (copySuccess) {
                        mainHandler.post(() -> callback.onStatus("模型文件复制完成，正在验证..."));
                        // 记录复制后的结构
                        logModelStructure(modelDir);

                        // 验证模型
                        if (isModelValid(modelDir)) {
                            loadModel(modelDir);
                        } else {
                            mainHandler.post(() -> callback.onError("模型文件验证失败，请检查模型完整性"));
                        }
                    } else {
                        mainHandler.post(() -> callback.onError("模型文件复制失败"));
                    }
                } else {
                    // 模型已存在且有效，直接加载
                    mainHandler.post(() -> callback.onStatus("加载现有模型..."));
                    loadModel(modelDir);
                }
            } catch (Exception e) {
                Log.e(TAG, "初始化异常", e);
                mainHandler.post(() -> callback.onError("初始化异常: " + e.getMessage()));
            }
        }).start();
    }

    private void logAssetsContent(AssetManager assetManager, String path) {
        try {
            String[] list = assetManager.list(path);
            if (list != null && list.length > 0) {
                Log.i(TAG, "Assets目录 '" + path + "' 包含 " + list.length + " 个文件/目录:");
                for (String item : list) {
                    Log.i(TAG, "  - " + item);
                }
            } else {
                Log.w(TAG, "Assets目录 '" + path + "' 为空");
            }
        } catch (IOException e) {
            Log.e(TAG, "无法读取assets目录: " + path, e);
        }
    }
    /**
     * 检查模型文件夹是否有效
     */
    private boolean isModelValid(File modelDir) {
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            Log.w(TAG, "模型文件夹不存在或不是目录");
            return false;
        }

        File[] files = modelDir.listFiles();
        if (files == null || files.length == 0) {
            Log.w(TAG, "模型文件夹为空");
            return false;
        }

        // 检查Vosk模型的关键文件
        boolean hasModelFiles = false;

        // Vosk模型通常包含这些关键目录
        for (File file : files) {
            String name = file.getName().toLowerCase();
            // 检查是否有am、conf、graph等关键目录
            if (file.isDirectory() && (name.contains("am") || name.contains("conf") ||
                    name.contains("graph") || name.contains("ivector"))) {
                hasModelFiles = true;
                Log.i(TAG, "找到模型目录: " + file.getName());
                // 检查目录下是否有文件
                File[] subFiles = file.listFiles();
                if (subFiles != null && subFiles.length > 0) {
                    Log.i(TAG, "  包含 " + subFiles.length + " 个文件");
                }
            } else if (file.isFile()) {
                // 检查是否有模型文件
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".mdl") || fileName.contains("mfcc") ||
                        fileName.contains("model") || fileName.endsWith(".fst")) {
                    hasModelFiles = true;
                    Log.i(TAG, "找到模型文件: " + file.getName());
                }
            }
        }

        if (hasModelFiles) {
            Log.i(TAG, "模型文件夹检查通过，包含 " + files.length + " 个文件/目录");
            return true;
        } else {
            Log.w(TAG, "模型文件夹未找到预期的模型文件");
            return false;
        }
    }

    /**
     * 记录模型结构
     */
    private void logModelStructure(File dir) {
        if (dir == null || !dir.exists()) {
            Log.w(TAG, "无法记录模型结构，目录不存在");
            return;
        }

        Log.i(TAG, "=== 模型文件夹结构 ===");
        Log.i(TAG, "路径: " + dir.getAbsolutePath());
        listFilesRecursive(dir, "");
        Log.i(TAG, "=== 结束模型结构 ===");
    }

    /**
     * 递归列出文件
     */
    private void listFilesRecursive(File dir, String indent) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                Log.i(TAG, indent + "[DIR] " + file.getName());
                listFilesRecursive(file, indent + "  ");
            } else {
                Log.i(TAG, indent + file.getName() + " (" + formatFileSize(file.length()) + ")");
            }
        }
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        else return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    /**
     * 递归复制assets文件夹
     */
    private boolean copyAssetsFolderRecursive(AssetManager assetManager, String sourcePath, String targetPath) {
        try {
            String[] files = assetManager.list(sourcePath);
            if (files == null || files.length == 0) {
                Log.w(TAG, "Assets路径 '" + sourcePath + "' 为空");
                return false;
            }

            Log.i(TAG, "复制: " + sourcePath + " -> " + targetPath);

            for (String file : files) {
                String assetFilePath = sourcePath.isEmpty() ? file : sourcePath + "/" + file;
                String targetFilePath = targetPath + "/" + file;

                try {
                    // 尝试打开文件，如果能打开就是文件，否则可能是目录
                    InputStream inputStream = assetManager.open(assetFilePath);
                    // 是文件
                    copyAssetFile(assetManager, assetFilePath, targetFilePath);
                    inputStream.close();
                } catch (IOException e) {
                    // 可能是目录，尝试递归复制
                    Log.d(TAG, "创建子目录: " + targetFilePath);
                    File dir = new File(targetFilePath);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    copyAssetsFolderRecursive(assetManager, assetFilePath, targetFilePath);
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "复制assets文件夹失败: " + sourcePath, e);
            return false;
        }
    }

    /**
     * 复制单个assets文件
     */
    private boolean copyAssetFile(AssetManager assetManager, String assetPath, String targetPath) {
        InputStream in = null;
        OutputStream out = null;

        try {
            in = assetManager.open(assetPath);
            File outFile = new File(targetPath);

            // 确保父目录存在
            File parentDir = outFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            out = new FileOutputStream(outFile);
            byte[] buffer = new byte[8192];
            int length;
            long fileBytes = 0;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
                fileBytes += length;
            }

            Log.v(TAG, "已复制文件: " + assetPath + " (" + formatFileSize(fileBytes) + ")");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "复制assets文件失败: " + assetPath, e);
            return false;
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭流失败", e);
            }
        }
    }

    /**
     * 删除目录及其内容
     */
    private boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return true;

        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }

        boolean deleted = dir.delete();
        Log.d(TAG, "删除: " + dir.getAbsolutePath() + (deleted ? " 成功" : " 失败"));
        return deleted;
    }

    /**
     * 加载模型
     */
    private void loadModel(File modelDir) {
        try {
            mainHandler.post(() -> callback.onStatus("正在加载语音模型..."));

            String modelPath = modelDir.getAbsolutePath();
            Log.i(TAG, "加载模型路径: " + modelPath);

            // 尝试加载模型
            model = new Model(modelPath);
            recognizer = new Recognizer(model, SAMPLE_RATE);

            // 设置识别器参数
            recognizer.setMaxAlternatives(0);
            recognizer.setWords(true);
            recognizer.setPartialWords(true);

            mainHandler.post(() -> {
                callback.onStatus("模型加载成功！点击开始录音按钮说话");
                Log.i(TAG, "Vosk模型初始化成功");
            });

        } catch (Exception e) {
            Log.e(TAG, "加载模型失败", e);

            String errorMsg = "加载模型失败: " + e.getMessage();
            if (e.getMessage().contains("does not contain model files")) {
                errorMsg += "\n请确保模型文件完整且格式正确";
            }

            mainHandler.post(() -> callback.onError("加载模型失败"));
        }
    }

    /**
     * 开始录音识别
     */
    public void startRecording() {
        // ==================== 添加权限检查 ====================
        Context context = contextRef.get();
        if (context == null) {
            mainHandler.post(() -> callback.onError("Context为空"));
            return;
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            mainHandler.post(() -> callback.onError("没有录音权限"));
            Log.e(TAG, "没有录音权限");
            return;
        }
        // ====================================================

        if (isRecording) {
            Log.w(TAG, "已经在录音中");
            return;
        }

        if (recognizer == null) {
            mainHandler.post(() -> callback.onError("请先初始化模型"));
            Log.e(TAG, "识别器未初始化");
            return;
        }

        try {
            // 计算合适的buffer大小
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                minBufferSize = SAMPLE_RATE * 2;
            }

            // 使用更大的buffer以避免欠载
            bufferSize = Math.max(minBufferSize, 4096);
            Log.d(TAG, "使用buffer大小: " + bufferSize);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize * 2
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                mainHandler.post(() -> callback.onError("无法初始化录音设备"));
                Log.e(TAG, "AudioRecord初始化失败");
                releaseAudioRecord();
                return;
            }

            isRecording = true;

            // 重置部分结果缓冲区
            partialResultsBuffer.clear();

            // 重置识别器
            recognizer.reset();

            try {
                audioRecord.startRecording();
            } catch (IllegalStateException e) {
                mainHandler.post(() -> callback.onError("启动录音失败: " + e.getMessage()));
                Log.e(TAG, "启动录音失败", e);
                releaseAudioRecord();
                isRecording = false;
                return;
            }

            // 检查录音状态
            if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                mainHandler.post(() -> callback.onError("录音设备未就绪"));
                Log.e(TAG, "录音设备未进入录音状态");
                releaseAudioRecord();
                isRecording = false;
                return;
            }

            mainHandler.post(() -> {
                callback.onRecordingStarted();
                callback.onStatus("正在录音...请说话");
                Log.i(TAG, "录音已开始");
            });

            // 启动识别线程
            recognitionThread = new RecognitionThread();
            recognitionThread.start();

        } catch (Exception e) {
            isRecording = false;
            mainHandler.post(() -> callback.onError("启动录音失败: " + e.getMessage()));
            Log.e(TAG, "启动录音失败", e);
            releaseAudioRecord();
        }
    }

    /**
     * 停止录音识别
     */
    public void stopRecording() {
        Log.i(TAG, "停止录音");

        // 先设置标志位，让线程退出循环
        isRecording = false;

        // 等待识别线程结束
        if (recognitionThread != null) {
            try {
                recognitionThread.interrupt();
                recognitionThread.join(500);
            } catch (InterruptedException e) {
                Log.e(TAG, "等待识别线程结束失败", e);
            }
            recognitionThread = null;
        }

        // 释放录音设备
        releaseAudioRecord();

        // 获取最终结果
        if (recognizer != null) {
            try {
                String finalResult = recognizer.getFinalResult();
                String text = parseResult(finalResult);
                if (text != null && !text.isEmpty()) {
                    Log.i(TAG, "最终识别结果: " + text);
                    final String finalText = text;
                    mainHandler.post(() -> callback.onResult(finalText));
                }
            } catch (Exception e) {
                Log.e(TAG, "获取最终结果失败", e);
            }
        }

        mainHandler.post(() -> {
            callback.onRecordingStopped();
            callback.onStatus("已停止录音");
            callback.onPartialResult("");
        });
    }

    /**
     * 释放录音设备资源
     */
    private void releaseAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "释放录音设备失败", e);
            }
            audioRecord = null;
        }
    }

    /**
     * 释放所有资源
     */
    public void release() {
        Log.i(TAG, "释放所有资源");

        // 停止录音
        isRecording = false;

        // 等待识别线程结束
        if (recognitionThread != null) {
            recognitionThread.interrupt();
            try {
                recognitionThread.join(500);
            } catch (InterruptedException e) {
                Log.e(TAG, "等待识别线程结束失败", e);
            }
            recognitionThread = null;
        }

        // 释放录音设备
        releaseAudioRecord();

        // 关闭识别器
        if (recognizer != null) {
            try {
                recognizer.close();
                Log.i(TAG, "识别器已关闭");
            } catch (Exception e) {
                Log.e(TAG, "关闭识别器失败", e);
            }
            recognizer = null;
        }

        // 关闭模型
        if (model != null) {
            try {
                model.close();
                Log.i(TAG, "模型已关闭");
            } catch (Exception e) {
                Log.e(TAG, "关闭模型失败", e);
            }
            model = null;
        }
    }

    /**
     * 识别线程
     */
    private class RecognitionThread extends Thread {
        @Override
        public void run() {
            Log.i(TAG, "识别线程开始运行");
            byte[] buffer = new byte[bufferSize];

            while (isRecording && !Thread.interrupted() && audioRecord != null) {
                try {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                    if (bytesRead > 0 && recognizer != null) {
                        boolean accepted = recognizer.acceptWaveForm(buffer, bytesRead);

                        if (accepted) {
                            // 最终结果
                            String resultJson = recognizer.getResult();
                            String text = parseResult(resultJson);
                            if (text != null && !text.isEmpty()) {
                                Log.i(TAG, "识别结果: " + text);
                                final String finalText = text;
                                mainHandler.post(() -> callback.onResult(finalText));
                            }
                        } else {
                            // 部分结果
                            String partialJson = recognizer.getPartialResult();
                            String partialText = parsePartialResult(partialJson);
                            if (partialText != null && !partialText.isEmpty()) {
                                // 添加到缓冲区
                                partialResultsBuffer.add(partialText);
                                if (partialResultsBuffer.size() > MAX_PARTIAL_RESULTS) {
                                    partialResultsBuffer.remove(0);
                                }

                                Log.d(TAG, "部分结果: " + partialText);
                                final String finalPartialText = partialText;
                                mainHandler.post(() -> callback.onPartialResult(finalPartialText));
                            }
                        }
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "AudioRecord读取错误: ERROR_INVALID_OPERATION");
                        break;
                    } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "AudioRecord读取错误: ERROR_BAD_VALUE");
                        break;
                    }

                    // 短暂休眠，避免过于频繁的循环
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        break;
                    }

                } catch (Exception e) {
                    Log.e(TAG, "识别过程中出错", e);
                    mainHandler.post(() -> callback.onError("识别错误: " + e.getMessage()));
                    break;
                }
            }

            Log.i(TAG, "识别线程结束");
        }
    }

    /**
     * 解析识别结果JSON
     */
    private String parseResult(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return "";
            }

            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            if (jsonObject.has("text")) {
                return jsonObject.get("text").getAsString();
            }
        } catch (Exception e) {
            Log.e(TAG, "解析结果失败: " + json, e);
        }
        return "";
    }

    /**
     * 解析部分结果JSON
     */
    private String parsePartialResult(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return "";
            }

            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            if (jsonObject.has("partial")) {
                return jsonObject.get("partial").getAsString();
            }
        } catch (Exception e) {
            Log.e(TAG, "解析部分结果失败: " + json, e);
        }
        return "";
    }

    /**
     * 检查是否正在录音
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * 检查模型是否已加载
     */
    public boolean isModelLoaded() {
        return model != null && recognizer != null;
    }
}