// Config.java
package com.example.nutricompass;

public class Config {
    // 你的电脑IP（用 ipconfig 查看）
    public static final String YOUR_IP = "10.128.141.95";  // 改成你的实际IP

    // RAG 服务地址
    public static final String RAG_SERVICE_URL =
            "http://" + YOUR_IP + ":8001/api/search_recipes";

    // Ollama 服务地址
    public static final String OLLAMA_URL =
            "http://" + YOUR_IP + ":11434/api/chat";
}