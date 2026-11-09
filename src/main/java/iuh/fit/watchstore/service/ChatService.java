package iuh.fit.watchstore.service;

import iuh.fit.watchstore.dto.ChatRequest;
import iuh.fit.watchstore.dto.ChatResponse;

public interface ChatService {
    ChatResponse chat(ChatRequest request);

    ChatResponse chat(ChatRequest request, String chatId);
}
