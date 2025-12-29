package com.gjg.guoaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.gjg.guoaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;
    
    // 标记是否已经添加过 nextStepPrompt，避免重复添加导致死循环
    private boolean nextStepPromptAdded = false;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、校验提示词，只在第一次调用时添加 nextStepPrompt（避免死循环）
        // 只在第一次调用时添加 nextStepPrompt，避免每次循环都添加导致消息列表无限增长
        if (StrUtil.isNotBlank(getNextStepPrompt()) && !nextStepPromptAdded) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
            nextStepPromptAdded = true;
        }
        // 2、检查并限制消息列表长度，避免超过 API 输入长度限制（1000000 字符）
        List<Message> messageList = getMessageList();
        messageList = truncateMessageListIfNeeded(messageList);
        setMessageList(messageList);
        
        // 3、调用 AI 大模型，获取工具调用结果
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            log.info(getName() + "的思考：" + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            // 如果不需要调用工具，返回 false（结束循环）
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                // 当 AI 返回文本回复（不调用工具）时，自动结束任务
                setState(AgentState.FINISHED);
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            setState(AgentState.FINISHED);
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        List<Message> updatedMessageList = toolExecutionResult.conversationHistory();
        // 获取工具响应消息（在截断前获取，因为需要检查是否调用了终止工具）
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(updatedMessageList);
        // 检查并截断消息列表，避免超过 API 限制
        updatedMessageList = truncateMessageListIfNeeded(updatedMessageList);
        setMessageList(updatedMessageList);
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        return results;
    }
    
    /**
     * 截断消息列表，避免超过 API 输入长度限制
     * DashScope API 限制：输入长度范围 [1, 1000000] 字符
     * 我们设置安全阈值为 800000 字符，保留一些余量
     *
     * @param messageList 原始消息列表
     * @return 截断后的消息列表
     */
    private List<Message> truncateMessageListIfNeeded(List<Message> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            return messageList;
        }
        
        // 计算当前消息列表的总字符数
        int totalChars = messageList.stream()
                .mapToInt(msg -> {
                    if (msg instanceof UserMessage) {
                        return ((UserMessage) msg).getText().length();
                    } else if (msg instanceof AssistantMessage) {
                        return ((AssistantMessage) msg).getText().length();
                    } else if (msg instanceof ToolResponseMessage) {
                        return ((ToolResponseMessage) msg).getResponses().stream()
                                .mapToInt(r -> r.responseData() != null ? r.responseData().length() : 0)
                                .sum();
                    }
                    return 0;
                })
                .sum();
        
        // 安全阈值：800000 字符（留 200000 字符余量）
        final int MAX_CHARS = 800000;
        
        // 如果超过阈值，只保留最近的消息
        if (totalChars > MAX_CHARS) {
            log.warn("消息列表总长度 {} 字符超过安全阈值 {} 字符，开始截断", totalChars, MAX_CHARS);
            
            // 保留第一条消息（用户初始消息）和最近的消息
            List<Message> truncatedList = new ArrayList<>();
            
            // 始终保留第一条消息（用户初始输入）
            if (!messageList.isEmpty()) {
                truncatedList.add(messageList.get(0));
            }
            
            // 从后往前添加消息，直到接近阈值
            int currentChars = truncatedList.get(0) instanceof UserMessage 
                    ? ((UserMessage) truncatedList.get(0)).getText().length() 
                    : 0;
            
            for (int i = messageList.size() - 1; i > 0; i--) {
                Message msg = messageList.get(i);
                int msgChars = 0;
                if (msg instanceof UserMessage) {
                    msgChars = ((UserMessage) msg).getText().length();
                } else if (msg instanceof AssistantMessage) {
                    msgChars = ((AssistantMessage) msg).getText().length();
                } else if (msg instanceof ToolResponseMessage) {
                    msgChars = ((ToolResponseMessage) msg).getResponses().stream()
                            .mapToInt(r -> r.responseData() != null ? r.responseData().length() : 0)
                            .sum();
                }
                
                if (currentChars + msgChars > MAX_CHARS) {
                    break;
                }
                
                truncatedList.add(1, msg); // 插入到第一条消息之后
                currentChars += msgChars;
            }
            
            log.info("消息列表已从 {} 条截断到 {} 条，字符数从 {} 减少到 {}", 
                    messageList.size(), truncatedList.size(), totalChars, currentChars);
            
            return truncatedList;
        }
        
        return messageList;
    }
}
