package com.game.sage.controller;

import com.game.sage.tools.IdentityVCrypticNotesTool;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/guide")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class GameSageController {

    private final ChatClient gameGuideClient;
    private final IdentityVCrypticNotesTool identityVcrypticNotesTool;

    @GetMapping(value = "/ask", produces = "text/html;charset=utf-8")
    public Flux<String> ask(@RequestParam String question,
                            @RequestParam String sessionId) {

        if (question != null) {
            if (question.length() > 50) {
                return Flux.just("【系统提示】您输入的内容过长，请精简您的问题后再试。");
            }

            return gameGuideClient.prompt()
                    .user(question)
                    //.options(ChatOptions.builder().maxTokens(100)) // 限制最多生成 100 个 token
                    .tools(identityVcrypticNotesTool)
                    .advisors(SessionAdvisors.session(sessionId))
                    .stream()
                    .content();
        } else {
            return Flux.just("【系统提示】请输入你的问题。");
        }
    }
}
