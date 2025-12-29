package com.gjg.guoimagesearchmcpserver.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageSearchTool {

    // 替换为你自己的 Pixabay API Key（从 https://pixabay.com/api/docs/ 获取）
    private static final String API_KEY = "53787311-eed45b574c8c96ca890b29fc2";

    // Pixabay 官方 API 地址（注意：不是网页！）
    private static final String API_URL = "https://pixabay.com/api/";

    @Tool(description = "Search images from Pixabay (free stock photos)")
    public String searchImage(@ToolParam(description = "Search query keyword, e.g., 'cat', 'mountain'") String query) {
        try {
            List<String> urls = searchMediumImages(query);
            if (urls.isEmpty()) {
                return "No images found for query: " + query;
            }
            return String.join(",", urls);
        } catch (Exception e) {
            return "Error searching image: " + e.getMessage();
        }
    }

    /**
     * 搜索中等尺寸的图片（使用 webformatURL，约 640px 宽）
     */
    public List<String> searchMediumImages(String query) {
        // Pixabay 使用 API Key 作为 URL 参数，不是 Authorization Header！
        Map<String, Object> params = new HashMap<>();
        params.put("key", API_KEY);          // 必填
        params.put("q", query);              // 搜索关键词
        params.put("image_type", "photo");   // 只搜照片（可选：photo, illustration, vector）
        params.put("per_page", 5);           // 返回前5张（最大200）
        params.put("safesearch", true);      // 开启安全搜索

        // 发送 GET 请求（无需 headers）
        String response = HttpUtil.get(API_URL, params);

        // 解析 JSON 响应
        // Pixabay 返回结构：{ "total": ..., "totalHits": ..., "hits": [ { "webformatURL": "...", ... }, ... ] }
        return JSONUtil.parseObj(response)
                .getJSONArray("hits")
                .stream()
                .map(obj -> (JSONObject) obj)
                .map(hit -> hit.getStr("webformatURL")) // 中等尺寸图片 URL
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}