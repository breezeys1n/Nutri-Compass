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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

                    // 检查assets中的模型文件夹结构
                    logAssetsContent(context.getAssets(), modelAssetPath);

                    boolean copySuccess = copyAssetsFolder(context.getAssets(), modelAssetPath, modelDir.getAbsolutePath());

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

    /**
     * 记录assets中的内容
     */
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

        // 检查是否有任何文件
        File[] files = modelDir.listFiles();
        if (files == null || files.length == 0) {
            Log.w(TAG, "模型文件夹为空");
            return false;
        }

        // Vosk模型应该包含一些特定的文件或目录
        // 常见的Vosk模型文件/目录
        boolean hasModelFiles = false;

        // 检查是否有常见的Vosk模型文件
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.contains("am") || name.contains("conf") || name.contains("graph") ||
                    name.contains("mfcc") || name.contains("model") || name.endsWith(".mdl")) {
                hasModelFiles = true;
                Log.i(TAG, "找到可能的模型文件: " + file.getName());
                break;
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
        listFilesRecursive(dir, "", true);
        Log.i(TAG, "=== 结束模型结构 ===");
    }

    /**
     * 递归列出文件
     */
    private void listFilesRecursive(File dir, String indent, boolean logFiles) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                Log.i(TAG, indent + "[DIR] " + file.getName());
                if (logFiles) {
                    listFilesRecursive(file, indent + "  ", logFiles);
                }
            } else {
                if (logFiles) {
                    Log.i(TAG, indent + file.getName() + " (" + file.length() + " bytes)");
                }
            }
        }
    }

    /**
     * 从Assets复制整个文件夹
     */
    private boolean copyAssetsFolder(AssetManager assetManager, String sourcePath, String targetPath) {
        InputStream in = null;
        OutputStream out = null;

        try {
            // 首先检查源路径是否存在
            String[] files = assetManager.list(sourcePath);
            if (files == null || files.length == 0) {
                Log.w(TAG, "Assets路径 '" + sourcePath + "' 不存在或为空");
                return false;
            }

            Log.i(TAG, "开始复制assets文件夹: " + sourcePath + " -> " + targetPath);

            // 创建目标目录
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            int totalFiles = 0;
            long totalBytes = 0;

            // 遍历assets中的所有文件/目录
            for (String file : files) {
                String assetFilePath = sourcePath.isEmpty() ? file : sourcePath + "/" + file;
                String targetFilePath = targetPath + "/" + file;

                try {
                    // 尝试打开文件，如果能打开就是文件，否则可能是目录
                    in = assetManager.open(assetFilePath);
                    // 能打开，说明是文件
                    File outFile = new File(targetFilePath);
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

                    totalBytes += fileBytes;
                    totalFiles++;

                    if (totalFiles <= 10) { // 只记录前10个文件
                        Log.d(TAG, "已复制: " + assetFilePath + " (" + fileBytes + " bytes)");
                    }

                    // 关闭流
                    in.close();
                    out.close();
                    in = null;
                    out = null;

                } catch (FileNotFoundException e) {
                    // 可能是目录，尝试递归复制
                    Log.d(TAG, "尝试复制子目录: " + assetFilePath);
                    copyAssetsFolder(assetManager, assetFilePath, targetFilePath);
                } catch (IOException e) {
                    // 其他IO异常
                    Log.e(TAG, "复制文件失败: " + assetFilePath, e);
                    return false;
                }
            }

            Log.i(TAG, "复制完成: " + totalFiles + " 个文件，总共 " + totalBytes + " 字节");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "复制assets文件夹失败: " + sourcePath, e);
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
        if (deleted) {
            Log.d(TAG, "删除: " + dir.getAbsolutePath());
        } else {
            Log.w(TAG, "删除失败: " + dir.getAbsolutePath());
        }
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

            // 再次检查模型结构
            logModelStructure(modelDir);

            // 尝试加载模型
            model = new Model(modelPath);
            recognizer = new Recognizer(model, SAMPLE_RATE);

            mainHandler.post(() -> {
                callback.onStatus("模型加载成功！可以开始说话");
                Log.i(TAG, "Vosk模型初始化成功");
            });

        } catch (Exception e) {
            Log.e(TAG, "加载模型失败", e);

            // 检查模型文件夹内容
            File[] files = modelDir.listFiles();
            if (files != null) {
                Log.w(TAG, "模型文件夹内容:");
                for (File file : files) {
                    if (file.isDirectory()) {
                        Log.w(TAG, "  [DIR] " + file.getName());
                        // 检查子目录
                        File[] subFiles = file.listFiles();
                        if (subFiles != null) {
                            for (File subFile : subFiles) {
                                Log.w(TAG, "    " + subFile.getName());
                            }
                        }
                    } else {
                        Log.w(TAG, "  " + file.getName());
                    }
                }
            }

            String errorMsg = "加载模型失败: " + e.getMessage();
            mainHandler.post(() -> callback.onError(errorMsg));
        }
    }

    /**
     * 开始录音识别
     */
    public void startRecording() {
        if (isRecording) {
            return;
        }

        if (recognizer == null) {
            callback.onError("请先初始化模型");
            return;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize * 2
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                callback.onError("无法初始化录音设备");
                return;
            }

            isRecording = true;
            audioRecord.startRecording();
            mainHandler.post(() -> {
                callback.onRecordingStarted();
                callback.onStatus("正在录音...");
            });

            // 启动识别线程
            recognitionThread = new RecognitionThread();
            recognitionThread.start();

        } catch (Exception e) {
            isRecording = false;
            mainHandler.post(() -> callback.onError("启动录音失败: " + e.getMessage()));
            Log.e(TAG, "启动录音失败", e);
        }
    }

    /**
     * 停止录音识别
     */
    public void stopRecording() {
        isRecording = false;

        if (recognitionThread != null) {
            recognitionThread.interrupt();
            try {
                recognitionThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "等待识别线程结束失败", e);
            }
            recognitionThread = null;
        }

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

        mainHandler.post(() -> {
            callback.onRecordingStopped();
            callback.onStatus("已停止录音");
        });
    }

    /**
     * 释放资源
     */
    public void release() {
        stopRecording();

        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭识别器失败", e);
            }
            recognizer = null;
        }

        if (model != null) {
            try {
                model.close();
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
            byte[] buffer = new byte[bufferSize];

            while (isRecording && !Thread.interrupted() && audioRecord != null) {
                try {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                    if (bytesRead > 0 && recognizer != null) {
                        if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                            // 最终结果
                            String resultJson = recognizer.getResult();
                            String text = parseResult(resultJson);
                            if (text != null && !text.isEmpty()) {
                                final String finalText = text;
                                mainHandler.post(() -> callback.onResult(finalText));
                            }
                        } else {
                            // 部分结果
                            String partialJson = recognizer.getPartialResult();
                            String partialText = parsePartialResult(partialJson);
                            if (partialText != null && !partialText.isEmpty()) {
                                final String finalPartialText = partialText;
                                mainHandler.post(() -> callback.onPartialResult(finalPartialText));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "识别过程中出错", e);
                    mainHandler.post(() -> callback.onError("识别错误: " + e.getMessage()));
                    break;
                }
            }

            // 录音结束时获取最终结果
            if (recognizer != null) {
                try {
                    String finalResult = recognizer.getFinalResult();
                    String text = parseResult(finalResult);
                    if (text != null && !text.isEmpty()) {
                        final String finalText = text;
                        mainHandler.post(() -> callback.onResult(finalText));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "获取最终结果失败", e);
                }
            }
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
            Log.e(TAG, "解析部分结果失败", e);
        }
        return "";
    }

}