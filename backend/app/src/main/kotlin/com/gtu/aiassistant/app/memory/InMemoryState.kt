package com.gtu.aiassistant.app.memory

import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifact
import com.gtu.aiassistant.domain.materials.model.MaterialChunk
import com.gtu.aiassistant.domain.materials.model.MaterialCollection
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.user.model.User
import java.util.concurrent.ConcurrentHashMap

class InMemoryState {
    val users = ConcurrentHashMap<String, User>()
    val chats = ConcurrentHashMap<String, Chat>()
    val generatedArtifacts = ConcurrentHashMap<String, GeneratedArtifact>()
    val materialCollections = ConcurrentHashMap<String, MaterialCollection>()
    val materialDocuments = ConcurrentHashMap<String, MaterialDocument>()
    val materialChunks = ConcurrentHashMap<String, MaterialChunk>()
}
