package com.game.sage.advisor;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameSageRiskAdvisor implements CallAdvisor {

    // 违规模式：关键词组合判断，比单字匹配更准
    private static final List<String> RISK_PHRASES = List.of(
            "代练", "代打", "代肝", "帮忙上分", "帮我打号",
            "脚本", "外挂", "自动", "连点器", "按键精灵",
            "挂机", "刷墨迹", "刷阅历", "刷币"
    );

    @Override
    public @NonNull String getName() {
        return "GameGuideRiskAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // 最先执行
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request,
                                         CallAdvisorChain chain) {
        String userText = request.prompt().getUserMessage().getText();

        // 检查是否命中风险短语
        boolean risky = RISK_PHRASES.stream().anyMatch(userText::contains);

        if (risky) {
            // 构造拒答响应，不调用 chain.nextCall()
            ChatResponse blocked = new ChatResponse(List.of(
                    new Generation(new AssistantMessage(
                            "这个话题涉及违规操作或封号风险，我只聊加页手记的正常攻略。"
                    ))
            ));
            return ChatClientResponse.builder()
                    .chatResponse(blocked)
                    .build();
        }

        // 安全，放行到下一个 Advisor 或模型
        return chain.nextCall(request);
    }
}
