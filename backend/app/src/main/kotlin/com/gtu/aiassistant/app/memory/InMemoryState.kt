package com.gtu.aiassistant.app.memory

import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.user.model.User
import java.util.concurrent.ConcurrentHashMap

class InMemoryState {
    val users = ConcurrentHashMap<String, User>()
    val chats = ConcurrentHashMap<String, Chat>()
}
