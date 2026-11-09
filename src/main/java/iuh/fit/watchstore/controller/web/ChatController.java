package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.dto.ChatRequest;
import iuh.fit.watchstore.dto.ChatResponse;
import iuh.fit.watchstore.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final String AI_CHAT_SESSION_KEY = "AI_CHAT_CONVERSATION_ID";
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, HttpServletRequest httpServletRequest) {
        HttpSession session = httpServletRequest.getSession(true);
        String chatId = (String) session.getAttribute(AI_CHAT_SESSION_KEY);
        if (chatId == null || chatId.isBlank()) {
            chatId = session.getId() != null ? session.getId() : UUID.randomUUID().toString();
            session.setAttribute(AI_CHAT_SESSION_KEY, chatId);
        }
        ChatResponse response = chatService.chat(request, chatId);
        return ResponseEntity.ok(response);
    }
}
