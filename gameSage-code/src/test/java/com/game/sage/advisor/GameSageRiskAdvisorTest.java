package com.game.sage.advisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameSageRiskAdvisorTest {

    private static final String BLOCKED_MESSAGE =
            "这个话题涉及违规操作或封号风险，我只聊加页手记的正常攻略。";

    private final GameSageRiskAdvisor advisor = new GameSageRiskAdvisor();

    private static ChatClientRequest request(String text) {
        return ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage(text)))
                .build();
    }

    private static ChatClientResponse response(String text) {
        return ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(
                        new Generation(new AssistantMessage(text)))))
                .build();
    }

    @ParameterizedTest(name = "风险词：{0}")
    @ValueSource(strings = {
            "代练", "代打", "代肝", "帮忙上分", "帮我打号",
            "脚本", "外挂", "自动", "连点器", "按键精灵",
            "挂机", "刷墨迹", "刷阅历", "刷币"
    })
    @DisplayName("命中任一风险词时直接拒答，且不调用后续链路")
    void adviseCall_shouldBlockRiskyPhrase(String phrase) {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        ChatClientResponse result = advisor.adviseCall(request("请问" + phrase + "怎么弄"), chain);

        assertEquals(BLOCKED_MESSAGE, result.chatResponse().getResult().getOutput().getText());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("普通攻略问题透传到下一个 Advisor，并原样返回响应")
    void adviseCall_shouldPassThroughSafeQuestion() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientRequest request = request("贪婪的盗匪绿锤怎么躲");
        ChatClientResponse expected = response("起跳瞬间往身后闪");
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(expected);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertSame(expected, result);
        verify(chain).nextCall(request);
    }

    @Test
    @DisplayName("空字符串不会被判为风险内容")
    void adviseCall_shouldPassThroughBlankText() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse expected = response("正常回复");
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(expected);

        ChatClientResponse result = advisor.adviseCall(request(""), chain);

        assertSame(expected, result);
        verify(chain).nextCall(any(ChatClientRequest.class));
    }

    @Test
    @DisplayName("Advisor 名称与最高优先级正确")
    void advisorMetadata_shouldBeCorrect() {
        assertEquals("GameGuideRiskAdvisor", advisor.getName());
        assertEquals(Ordered.HIGHEST_PRECEDENCE, advisor.getOrder());
    }
}
