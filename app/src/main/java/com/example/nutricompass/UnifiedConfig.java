// UnifiedConfig.java
package com.example.nutricompass;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 统一的IP配置管理类
 * 所有网络请求的IP地址都通过此类获取
 */
public class UnifiedConfig {
    private static final String TAG = "UnifiedConfig";
    private static final String PREF_NAME = "network_config";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String DEFAULT_IP = "10.138.79.96";

    private static UnifiedConfig instance;
    private Context context;
    private String currentIp;

    private UnifiedConfig(Context context) {
        this.context = context.getApplicationContext();
        loadSavedIp();
    }

    public static synchronized UnifiedConfig getInstance(Context context) {
        if (instance == null) {
            instance = new UnifiedConfig(context);
        }
        return instance;
    }

    /**
     * 加载保存的IP地址
     */
    private void loadSavedIp() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        currentIp = prefs.getString(KEY_SERVER_IP, DEFAULT_IP);
        Log.d(TAG, "加载IP地址: " + currentIp);
    }

    /**
     * 获取当前IP地址
     */
    public String getCurrentIp() {
        return currentIp;
    }

    /**
     * 获取RAG服务URL
     */
    public String getRagServiceUrl() {
        return "http://" + currentIp + ":8001/api/search_recipes";
    }

    /**
     * 获取Ollama基础URL
     */
    public String getOllamaBaseUrl() {
        return "http://" + currentIp + ":11434";
    }

    /**
     * 获取Ollama聊天API URL
     */
    public String getOllamaChatUrl() {
        return "http://" + currentIp + ":11434/api/chat";
    }

    /**
     * 获取Ollama生成API URL
     */
    public String getOllamaGenerateUrl() {
        return "http://" + currentIp + ":11434/api/generate";
    }

    /**
     * 获取风味模型服务URL
     */
    public String getFlavorModelUrl() {
        return "http://" + currentIp + ":8081/completion";
    }
}