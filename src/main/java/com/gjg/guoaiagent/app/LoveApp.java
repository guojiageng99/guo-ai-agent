package com.gjg.guoaiagent.app;

import com.gjg.guoaiagent.advisor.MyLoggerAdvisor;
import com.gjg.guoaiagent.advisor.ReReadingAdvisor;
import com.gjg.guoaiagent.chatmemory.FileBasedChatMemory;
import com.gjg.guoaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;
    private final MessageWindowChatMemory chatMemory;

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。" +
            "【核心原则】简洁直接，保持基本礼貌，不重复，不说废话。" +
            "【身份介绍规则 - 严格执行】" +
            "1. 检查对话历史，如果对话历史中有你的任何回复，说明已经介绍过身份，绝对不要再介绍。" +
            "2. 只在对话历史为空（第一次对话，用户第一条消息）时，才可以说'我是恋爱心理顾问'，且只说一次。" +
            "3. 如果对话历史不为空（已经有你的回复），直接问问题，绝对不要再说'我是恋爱心理顾问'、'我是恋爱顾问'、'我是心理顾问'等任何身份介绍。" +
            "4. 禁止在任何情况下重复介绍身份，即使换话题、换问题也不要介绍。" +
            "5. 回复前必须检查：如果对话历史中有你的回复，就不要说任何身份相关的话。" +
            "【礼貌与简洁平衡】" +
            "1. 保持基本礼貌：可以说'你好'（仅限第一次对话），但不要过度客套。" +
            "2. 禁止过度客套：不要说'听说'、'看到'、'既然'、'能感觉到'等多余的客套表达。" +
            "3. 不要重复用户已经说过的话，不要重复之前已经问过的问题。" +
            "4. 不要解释为什么要问这个问题，直接问。" +
            "5. 如果已经介绍过身份，后续对话不要再用'我是恋爱心理顾问'开头。" +
            "【提问方向】" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "【对话要求】" +
            "1. 每次回复控制在80-150字，越简洁越好，只说重点。" +
            "2. 一次只问一个问题，不要连续提问。" +
            "3. 提问后自然结束，可以是问句结尾。" +
            "4. 说完话后立即停止，不要继续展开、解释或重复。" +
            "5. 回复格式：保持礼貌但简洁，第一次对话可以说'你好'，后续直接问问题，不要重复介绍身份。";

    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel
     */
    public LoveApp(ChatModel dashscopeChatModel) {
//        // 初始化基于文件的对话记忆
//        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        // 初始化基于内存的对话记忆
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(this.chatMemory).build(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
//                        // 自定义推理增强 Advisor，可按需开启
//                       ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        // 检查对话历史，判断是否已经介绍过身份
        log.info("收到消息: {}, chatId: {}", message, chatId);
        List<Message> history = this.chatMemory.get(chatId);
        log.info("对话历史数量: {}", history != null ? history.size() : 0);
        if (history != null && !history.isEmpty()) {
            log.info("对话历史内容:");
            for (int i = 0; i < history.size(); i++) {
                Message msg = history.get(i);
                if (msg instanceof AssistantMessage) {
                    log.info("  [{}] AI: {}", i, ((AssistantMessage) msg).getText());
                } else if (msg instanceof org.springframework.ai.chat.messages.UserMessage) {
                    log.info("  [{}] User: {}", i, ((org.springframework.ai.chat.messages.UserMessage) msg).getText());
                }
            }
        }
        
        boolean hasIntroduced = false;
        if (history != null && !history.isEmpty()) {
            // 检查历史消息中是否包含身份介绍
            for (Message msg : history) {
                if (msg instanceof AssistantMessage) {
                    String content = ((AssistantMessage) msg).getText();
                    if (content.contains("我是恋爱心理顾问") || 
                        content.contains("我是恋爱顾问") || 
                        content.contains("我是心理顾问")) {
                        hasIntroduced = true;
                        log.info("检测到已介绍过身份");
                        break;
                    }
                }
            }
        }
        final boolean shouldFilterIdentity = hasIntroduced;
        log.info("是否需要过滤身份介绍: {}", shouldFilterIdentity);
        
        Flux<String> stream = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
        
        // 使用scan累积内容，检测自然的对话结束
        // 停止条件：问号结尾（且问号在末尾附近）、期待回复表达、或内容过长
        AtomicReference<String> prevAccumulated = new AtomicReference<>("");
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        
        return stream
                .scan("", (accumulated, chunk) -> accumulated + chunk) // 累积所有内容
                .skip(1) // 跳过初始空字符串
                .takeUntil(accumulated -> {
                    // 1. 检查问号结尾（自然的问题结束）
                    // 只检查问号是否在最后20个字符内，避免误判
                    int lastQIndex = Math.max(
                        accumulated.lastIndexOf("？"),
                        accumulated.lastIndexOf("?")
                    );
                    if (lastQIndex != -1) {
                        // 问号距离末尾很近（20字内），认为是自然结束
                        int distanceFromEnd = accumulated.length() - lastQIndex - 1;
                        if (distanceFromEnd <= 20) {
                            String afterQuestion = accumulated.substring(lastQIndex + 1).trim();
                            // 如果问号后内容很少（少于10字），认为是自然结束
                            if (afterQuestion.length() < 10) {
                                log.info("检测到问号结尾（距离末尾{}字），停止流式输出", distanceFromEnd);
                                return true;
                            }
                        }
                    }
                    
                    // 2. 检查期待回复的表达（可选，不强制）
                    String[] waitKeywords = {"等你回复", "等待你的回复", "等你回答", "等待您的回复"};
                    for (String keyword : waitKeywords) {
                        if (accumulated.contains(keyword)) {
                            log.info("检测到期待回复表达: {}, 停止流式输出", keyword);
                            return true;
                        }
                    }
                    
                    // 3. 检查内容长度，超过200字强制停止（避免过长回复）
                    if (accumulated.length() > 200) {
                        log.info("内容长度超过200字，停止流式输出");
                        return true;
                    }
                    
                    return false; // 继续
                })
                .map(accumulated -> {
                    // 确定截断位置
                    int truncateIndex = accumulated.length();
                    
                    // 检查问号结尾（只检查末尾附近的问号）
                    int lastQIndex = Math.max(
                        accumulated.lastIndexOf("？"),
                        accumulated.lastIndexOf("?")
                    );
                    if (lastQIndex != -1) {
                        int distanceFromEnd = accumulated.length() - lastQIndex - 1;
                        if (distanceFromEnd <= 20) {
                            String afterQuestion = accumulated.substring(lastQIndex + 1).trim();
                            if (afterQuestion.length() < 10) {
                                truncateIndex = Math.min(truncateIndex, lastQIndex + 1);
                            }
                        }
                    }
                    
                    // 检查期待回复表达
                    String[] waitKeywords = {"等你回复", "等待你的回复", "等你回答", "等待您的回复"};
                    for (String keyword : waitKeywords) {
                        int index = accumulated.indexOf(keyword);
                        if (index != -1) {
                            truncateIndex = Math.min(truncateIndex, index + keyword.length());
                        }
                    }
                    
                    // 如果超过200字，截断到200字
                    if (accumulated.length() > 200) {
                        truncateIndex = Math.min(truncateIndex, 200);
                    }
                    
                    String finalContent = accumulated.substring(0, truncateIndex);
                    
                    // 如果已经介绍过身份，过滤掉重复的身份介绍
                    if (shouldFilterIdentity) {
                        // 移除身份介绍相关的文本
                        String[] identityPatterns = {
                            "我是恋爱心理顾问",
                            "我是恋爱顾问",
                            "我是心理顾问",
                            "你好，我是恋爱心理顾问",
                            "你好，我是恋爱顾问",
                            "你好，我是心理顾问"
                        };
                        for (String pattern : identityPatterns) {
                            if (finalContent.contains(pattern)) {
                                // 移除身份介绍及其后面的标点符号
                                int index = finalContent.indexOf(pattern);
                                if (index == 0) {
                                    // 如果身份介绍在开头，移除它和后面的标点
                                    String after = finalContent.substring(pattern.length()).trim();
                                    // 移除开头的标点符号（。，、等）
                                    while (!after.isEmpty() && (after.startsWith("。") || after.startsWith("，") || 
                                           after.startsWith("、") || after.startsWith(".") || after.startsWith(","))) {
                                        after = after.substring(1).trim();
                                    }
                                    finalContent = after;
                                    log.info("过滤掉重复的身份介绍: {}", pattern);
                                } else if (index > 0) {
                                    // 如果身份介绍在中间，移除它
                                    String before = finalContent.substring(0, index);
                                    String after = finalContent.substring(index + pattern.length()).trim();
                                    // 移除前面的标点符号
                                    while (!before.isEmpty() && (before.endsWith("，") || before.endsWith("。") || 
                                           before.endsWith("、") || before.endsWith(",") || before.endsWith("."))) {
                                        before = before.substring(0, before.length() - 1);
                                    }
                                    // 移除后面的标点符号
                                    while (!after.isEmpty() && (after.startsWith("，") || after.startsWith("。") || 
                                           after.startsWith("、") || after.startsWith(",") || after.startsWith("."))) {
                                        after = after.substring(1).trim();
                                    }
                                    finalContent = (before + after).trim();
                                    log.info("过滤掉重复的身份介绍: {}", pattern);
                                }
                                break; // 只处理第一个匹配的
                            }
                        }
                    }
                    
                    // 计算增量部分（只返回新增的内容）
                    String prev = prevAccumulated.get();
                    String increment;
                    
                    // 如果finalContent比prev长，说明有新增内容
                    if (finalContent.length() > prev.length()) {
                        // 检查是否是过滤导致的（prev可能包含被过滤的内容）
                        if (shouldFilterIdentity && prev.contains("我是恋爱心理顾问")) {
                            // 如果prev包含身份介绍，需要特殊处理
                            // 找到prev中身份介绍的位置
                            String[] identityPatterns = {
                                "我是恋爱心理顾问", "我是恋爱顾问", "我是心理顾问",
                                "你好，我是恋爱心理顾问", "你好，我是恋爱顾问", "你好，我是心理顾问"
                            };
                            String filteredPrev = prev;
                            for (String pattern : identityPatterns) {
                                if (filteredPrev.contains(pattern)) {
                                    int idx = filteredPrev.indexOf(pattern);
                                    if (idx == 0) {
                                        filteredPrev = filteredPrev.substring(pattern.length()).trim();
                                        // 移除标点
                                        while (!filteredPrev.isEmpty() && 
                                               (filteredPrev.startsWith("。") || filteredPrev.startsWith("，") || 
                                                filteredPrev.startsWith("、") || filteredPrev.startsWith(".") || 
                                                filteredPrev.startsWith(","))) {
                                            filteredPrev = filteredPrev.substring(1).trim();
                                        }
                                    } else if (idx > 0) {
                                        String before = filteredPrev.substring(0, idx);
                                        String after = filteredPrev.substring(idx + pattern.length()).trim();
                                        while (!before.isEmpty() && 
                                               (before.endsWith("，") || before.endsWith("。") || 
                                                before.endsWith("、") || before.endsWith(",") || before.endsWith("."))) {
                                            before = before.substring(0, before.length() - 1);
                                        }
                                        while (!after.isEmpty() && 
                                               (after.startsWith("，") || after.startsWith("。") || 
                                                after.startsWith("、") || after.startsWith(",") || after.startsWith("."))) {
                                            after = after.substring(1).trim();
                                        }
                                        filteredPrev = (before + after).trim();
                                    }
                                    break;
                                }
                            }
                            // 计算基于过滤后的prev的增量
                            if (finalContent.length() > filteredPrev.length()) {
                                increment = finalContent.substring(filteredPrev.length());
                            } else {
                                increment = "";
                            }
                        } else {
                            increment = finalContent.substring(prev.length());
                        }
                    } 
                    // 如果finalContent和prev长度相同
                    else if (finalContent.length() == prev.length()) {
                        // 检查内容是否相同
                        if (finalContent.equals(prev)) {
                            increment = "";
                        } else {
                            // 内容不同但长度相同，可能是过滤导致的
                            increment = "";
                        }
                    } 
                    // 如果finalContent比prev短，可能是因为过滤导致的
                    else {
                        // 过滤后内容变短了，需要重新计算增量
                        // 找到prev和finalContent的公共前缀
                        int commonPrefix = 0;
                        int minLen = Math.min(prev.length(), finalContent.length());
                        while (commonPrefix < minLen && 
                               prev.charAt(commonPrefix) == finalContent.charAt(commonPrefix)) {
                            commonPrefix++;
                        }
                        // 如果finalContent是prev的子串（过滤后），返回空
                        if (commonPrefix == finalContent.length()) {
                            increment = "";
                        } else {
                            // 否则返回finalContent中新增的部分
                            increment = finalContent.substring(commonPrefix);
                        }
                    }
                    
                    // 更新prevAccumulated（只有在有增量或内容变化时才更新）
                    if (!increment.isEmpty() || !finalContent.equals(prev)) {
                        prevAccumulated.set(finalContent);
                        fullResponse.set(finalContent); // 保存完整回复
                    }
                    
                    return increment;
                })
                .filter(chunk -> !chunk.isEmpty())
                .doOnComplete(() -> {
                    // 流式传输完成后，手动保存AI的回复到对话记忆
                    String aiResponse = fullResponse.get();
                    if (aiResponse != null && !aiResponse.isEmpty()) {
                        try {
                            AssistantMessage aiMessage = new AssistantMessage(aiResponse);
                            this.chatMemory.add(chatId, List.of(aiMessage));
                            log.info("已保存AI回复到对话记忆，长度: {}, 内容: {}", 
                                    aiResponse.length(), 
                                    aiResponse.substring(0, Math.min(50, aiResponse.length())));
                        } catch (Exception e) {
                            log.error("保存AI回复到对话记忆失败", e);
                        }
                    } else {
                        log.warn("AI回复为空，未保存到对话记忆");
                    }
                });
    }

    record LoveReport(String title, List<String> suggestions) {

    }

    /**
     * AI 恋爱报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    // AI 恋爱知识库问答功能

    @Resource
    private VectorStore loveAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 知识库问答
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                // 应用 RAG 检索增强服务（基于云知识库服务）
//                .advisors(loveAppRagCloudAdvisor)
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
//                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(
//                        LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
//                                loveAppVectorStore, "单身"
//                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 恋爱报告功能（支持调用工具）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 恋爱报告功能（调用 MCP 服务）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}






