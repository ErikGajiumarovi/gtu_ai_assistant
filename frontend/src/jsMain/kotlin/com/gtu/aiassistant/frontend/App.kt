package com.gtu.aiassistant.frontend

import androidx.compose.runtime.*
import com.gtu.aiassistant.shared.*
import kotlinx.browser.localStorage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement

private const val SESSION_KEY = "gtu-ai-assistant.session"
private val sessionJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class SessionState(val email: String, val jwt: String)
private data class Notice(val tone: String, val title: String, val detail: String)
private data class RegistrationDraft(val name: String, val lastName: String, val email: String, val password: String)
private data class LoginDraft(val email: String, val password: String)

fun main() {
    renderComposable("root") { App() }
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient() }

    var notice by remember { mutableStateOf<Notice?>(null) }
    var authMode by remember { mutableStateOf("login") }
    var session by remember { mutableStateOf(loadSession()) }
    var registeredUser by remember { mutableStateOf<UserResponse?>(null) }
    var registrationDraft by remember { mutableStateOf(RegistrationDraft("", "", "", "")) }
    var loginDraft by remember { mutableStateOf(LoginDraft("", "")) }
    var chats by remember { mutableStateOf<List<ChatResponse>>(emptyList()) }
    var selectedChatId by remember { mutableStateOf("") }
    var chatSearch by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var isRefreshingChats by remember { mutableStateOf(false) }

    var isStreaming by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var pendingUserText by remember { mutableStateOf("") }

    val selectedChat = chats.find { it.id == selectedChatId }
    val filteredChats = if (chatSearch.isBlank()) chats
        else chats.filter { c ->
            getChatTitle(c).lowercase().contains(chatSearch.lowercase()) ||
            getChatPreview(c).lowercase().contains(chatSearch.lowercase())
        }

    SideEffect {
        if (session != null) {
            saveSession(session!!)
            apiClient.setAuthToken(session!!.jwt)
        } else {
            localStorage.removeItem(SESSION_KEY)
            apiClient.setAuthToken(null)
        }
    }

    LaunchedEffect(Unit) {
        session?.let { apiClient.setAuthToken(it.jwt) }
    }

    LaunchedEffect(session) {
        if (session != null) {
            refreshChats(apiClient, { chats = sortChats(it) }, { notice = it }, { isRefreshingChats = it })
        } else {
            chats = emptyList(); selectedChatId = ""; pendingUserText = ""; streamingText = ""
        }
    }

    val showError: (String, Exception) -> Unit = { title, error ->
        notice = if (error is ApiClientError) Notice("error", title, "${error.code}: ${error.message}")
        else Notice("error", title, error.message ?: "Unknown error")
    }

    Div(attrs = { style { property("min-height", "100vh"); backgroundColor(Color("#ffffff")); color(Color("#2d2d2d")) } }) {
        if (session == null) {
            AuthPage(
                authMode = authMode, notice = notice, loginDraft = loginDraft, registrationDraft = registrationDraft,
                registeredUser = registeredUser, isRegistering = isRegistering, isLoggingIn = isLoggingIn,
                onSetAuthMode = { authMode = it },
                onLoginDraftChange = { loginDraft = it }, onRegistrationDraftChange = { registrationDraft = it },
                onLogin = { scope.launch {
                    isLoggingIn = true
                    try {
                        val r = apiClient.login(LoginInRequest(loginDraft.email, loginDraft.password))
                        session = SessionState(email = loginDraft.email.trim().lowercase(), jwt = r.jwt)
                    } catch (e: Exception) { showError("Login failed", e) } finally { isLoggingIn = false }
                } },
                onRegister = { scope.launch {
                    isRegistering = true
                    try {
                        val u = apiClient.registerUser(RegisterUserRequest(registrationDraft.name, registrationDraft.lastName, registrationDraft.email, registrationDraft.password))
                        registeredUser = u; authMode = "login"
                        loginDraft = LoginDraft(u.email, registrationDraft.password)
                        registrationDraft = RegistrationDraft("", "", "", "")
                        notice = Notice("success", "Account created", "${u.name} ${u.lastName} can now sign in.")
                    } catch (e: Exception) { showError("Registration failed", e) } finally { isRegistering = false }
                } }
            )
        } else {
            Div(attrs = { style { display(DisplayMode.Grid); property("grid-template-columns", "260px 1fr"); property("height", "100vh") } }) {
                Sidebar(
                    chats = filteredChats, selectedChatId = selectedChatId, session = session!!,
                    isRefreshingChats = isRefreshingChats, isStreaming = isStreaming,
                    onSelectChat = { selectedChatId = it; pendingUserText = ""; streamingText = "" },
                    onNewChat = { selectedChatId = ""; pendingUserText = ""; streamingText = ""; chatSearch = ""; notice = null },
                    onLogout = { session = null },
                    onSearchChange = { chatSearch = it },
                    onRefresh = { scope.launch { refreshChats(apiClient, { chats = sortChats(it) }, { notice = it }, { isRefreshingChats = it }) } }
                )
                ChatScreen(
                    notice = notice, selectedChat = selectedChat, isStreaming = isStreaming,
                    streamingText = streamingText, pendingUserText = pendingUserText,
                    onDismissNotice = { notice = null },
                    onSubmit = { text ->
                        scope.launch {
                            val trimmed = text.trim(); if (trimmed.isEmpty()) return@launch
                            pendingUserText = trimmed; streamingText = ""; isStreaming = true

                            if (selectedChat == null) {
                                apiClient.createChatWithAgentStream(
                                    CreateChatWithAgentRequest(trimmed),
                                    onToken = { token -> streamingText += token },
                                    onDone = { chat ->
                                        chats = sortChats(listOf(chat) + chats.filter { it.id != chat.id })
                                        selectedChatId = chat.id; isStreaming = false
                                        streamingText = ""; pendingUserText = ""
                                    },
                                    onError = { error ->
                                        isStreaming = false; streamingText = ""; pendingUserText = ""
                                        showError("Chat failed", error)
                                    }
                                )
                            } else {
                                apiClient.continueChatWithAgentStream(
                                    selectedChat.id, ContinueChatWithAgentRequest(trimmed),
                                    onToken = { token -> streamingText += token },
                                    onDone = { chat ->
                                        chats = sortChats(listOf(chat) + chats.filter { it.id != chat.id })
                                        selectedChatId = chat.id; isStreaming = false
                                        streamingText = ""; pendingUserText = ""
                                    },
                                    onError = { error ->
                                        isStreaming = false; streamingText = ""; pendingUserText = ""
                                        showError("Response failed", error)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthPage(
    authMode: String, notice: Notice?, loginDraft: LoginDraft, registrationDraft: RegistrationDraft,
    registeredUser: UserResponse?, isRegistering: Boolean, isLoggingIn: Boolean,
    onSetAuthMode: (String) -> Unit,
    onLoginDraftChange: (LoginDraft) -> Unit, onRegistrationDraftChange: (RegistrationDraft) -> Unit,
    onLogin: () -> Unit, onRegister: () -> Unit
) {
    Div(attrs = { style { display(DisplayMode.Flex); alignItems(AlignItems.Center); justifyContent(JustifyContent.Center); property("min-height", "100vh"); backgroundColor(Color("#f7f7f8")); padding(16.px) } }) {
        Div(attrs = { style { property("width", "100%"); property("max-width", "400px") } }) {
            Div(attrs = { style { textAlign(TextAlign.Center); property("margin-bottom", "32px") } }) {
                Span(attrs = { style { fontSize(32.px); fontWeight("700"); color(Color("#1a1a2e")) } }) { Text("GTU Assistant") }
                P(attrs = { style { fontSize(14.px); color(Color("#666")); marginTop(8.px) } }) { Text("Sign in to your account") }
            }
            Div(attrs = { style { backgroundColor(Color("white")); border(1.px, LineStyle.Solid, Color("#e5e5e5")); property("border-radius", "12px"); padding(24.px) } }) {
                Div(attrs = { style { display(DisplayMode.Flex); marginBottom(24.px) } }) {
                    listOf("login" to "Sign In", "register" to "Sign Up").forEach { (mode, label) ->
                        Button(attrs = {
                            onClick { onSetAuthMode(mode) }
                            style {
                                flex(1.0); padding(10.px); fontSize(14.px); fontWeight("600")
                                property("border-radius", "8px"); border(0.px)
                                backgroundColor(if (authMode == mode) Color("#1a1a2e") else Color("transparent"))
                                color(if (authMode == mode) Color("white") else Color("#666"))
                                cursor("pointer")
                            }
                        }) { Text(label) }
                    }
                }
                if (notice != null) { Div(attrs = { style { marginBottom(16.px) } }) { NoticeBanner(notice = notice) } }
                if (authMode == "login") {
                    Form(attrs = { onSubmit { it.preventDefault(); onLogin() } }) {
                        Field("Email", "email", loginDraft.email) { onLoginDraftChange(loginDraft.copy(email = it)) }
                        Spacer(16)
                        Field("Password", "password", loginDraft.password) { onLoginDraftChange(loginDraft.copy(password = it)) }
                        Spacer(24)
                        Button(attrs = {
                            disabled(isLoggingIn); attr("type", "submit")
                            style {
                                property("width", "100%"); padding(12.px); fontSize(14.px); fontWeight("600")
                                property("border-radius", "8px"); border(0.px)
                                backgroundColor(Color("#1a1a2e")); color(Color("white"))
                                cursor(if (isLoggingIn) "not-allowed" else "pointer")
                                property("opacity", if (isLoggingIn) "0.6" else "1")
                            }
                        }) { Text(if (isLoggingIn) "Signing in..." else "Sign in") }
                    }
                } else {
                    Form(attrs = { onSubmit { it.preventDefault(); onRegister() } }) {
                        Field("First name", "text", registrationDraft.name) { onRegistrationDraftChange(registrationDraft.copy(name = it)) }
                        Spacer(16)
                        Field("Last name", "text", registrationDraft.lastName) { onRegistrationDraftChange(registrationDraft.copy(lastName = it)) }
                        Spacer(16)
                        Field("Email", "email", registrationDraft.email) { onRegistrationDraftChange(registrationDraft.copy(email = it)) }
                        Spacer(16)
                        Field("Password", "password", registrationDraft.password) { onRegistrationDraftChange(registrationDraft.copy(password = it)) }
                        Spacer(24)
                        Button(attrs = {
                            disabled(isRegistering); attr("type", "submit")
                            style {
                                property("width", "100%"); padding(12.px); fontSize(14.px); fontWeight("600")
                                property("border-radius", "8px"); border(0.px)
                                backgroundColor(Color("#1a1a2e")); color(Color("white"))
                                cursor(if (isRegistering) "not-allowed" else "pointer")
                                property("opacity", if (isRegistering) "0.6" else "1")
                            }
                        }) { Text(if (isRegistering) "Creating account..." else "Create account") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    notice: Notice?, selectedChat: ChatResponse?,
    isStreaming: Boolean, streamingText: String, pendingUserText: String,
    onDismissNotice: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val scrollRef = remember { mutableStateOf<HTMLElement?>(null) }
    var composerText by remember { mutableStateOf("") }

    LaunchedEffect(selectedChat?.id, streamingText, pendingUserText) {
        scrollRef.value?.scrollIntoView(false)
    }

    Div(attrs = { style { display(DisplayMode.Flex); flexDirection(FlexDirection.Column); property("height", "100vh") } }) {
        Div(attrs = { style { flexGrow(1.0); property("overflow-y", "auto"); padding(0.px) } }) {
            Div(attrs = { style { property("max-width", "768px"); margin("0 auto"); padding(32.px, 24.px) } }) {
                if (notice != null && notice.tone != "info") {
                    NoticeBanner(notice = notice)
                    Spacer(16)
                }

                val existingMessages = selectedChat?.messages ?: emptyList()
                val hasAnyContent = existingMessages.isNotEmpty() || pendingUserText.isNotEmpty() || isStreaming

                if (!hasAnyContent) {
                    Div(attrs = { style { textAlign(TextAlign.Center); paddingTop(120.px) } }) {
                        Span(attrs = { style { fontSize(48.px); color(Color("#e0e0e0")) } }) { Text("\uD83C\uDF93") }
                        P(attrs = { style { fontSize(18.px); fontWeight("600"); color(Color("#333")); marginTop(16.px) } }) { Text("How can I help you?") }
                        P(attrs = { style { fontSize(14.px); color(Color("#888")); marginTop(8.px) } }) { Text("Ask me anything about Georgian Technical University") }
                    }
                }

                for (message in existingMessages) {
                    val isUser = message.senderType == "USER"
                    MessageBubble(
                        text = message.originalText,
                        isUser = isUser,
                        time = formatMessageTime(message.createdAt),
                        citations = if (!isUser) message.citations else emptyList()
                    )
                    Spacer(12)
                }

                if (pendingUserText.isNotEmpty()) {
                    MessageBubble(text = pendingUserText, isUser = true, time = "")
                    Spacer(12)
                }

                if (isStreaming) {
                    MessageBubble(text = streamingText, isUser = false, time = "", isStreaming = true)
                    Spacer(12)
                }

                Div(attrs = { ref { scrollRef.value = it }; style { height(1.px) } })
            }
        }

        Div(attrs = { style { borderTop(1.px, LineStyle.Solid, Color("#e5e5e5")); padding(16.px, 24.px); backgroundColor(Color("white")) } }) {
            Form(attrs = { onSubmit { it.preventDefault(); onSubmit(composerText); composerText = "" }; style { property("max-width", "768px"); margin("0 auto") } }) {
                Div(attrs = { style { display(DisplayMode.Flex); property("gap", "8px"); alignItems(AlignItems.FlexEnd) } }) {
                    val textareaRef = remember { mutableStateOf<HTMLTextAreaElement?>(null) }
                    LaunchedEffect(composerText) {
                        textareaRef.value?.let { ta ->
                            ta.style.height = "0px"
                            ta.style.height = "${kotlin.math.min(ta.scrollHeight.toDouble(), 200.0)}px"
                            ta.style.overflowY = if (ta.scrollHeight.toDouble() > 200.0) "auto" else "hidden"
                        }
                    }
                    TextArea(attrs = {
                        ref { textareaRef.value = it }
                        value(composerText)
                        rows(1)
                        disabled(isStreaming)
                        placeholder("Message GTU Assistant...")
                        onInput { event -> composerText = (event.target as HTMLTextAreaElement).value }
                        style {
                            flex(1.0); property("resize", "none"); padding(12.px, 16.px)
                            property("border-radius", "12px"); border(1.px, LineStyle.Solid, Color("#e5e5e5"))
                            fontSize(14.px); property("outline", "none"); backgroundColor(Color("#fafafa"))
                            property("max-height", "200px"); property("line-height", "1.5")
                            property("opacity", if (isStreaming) "0.5" else "1")
                        }
                    })
                    Button(attrs = {
                        disabled(isStreaming || composerText.trim().isEmpty())
                        attr("type", "submit")
                        style {
                            width(40.px); height(40.px); padding(0.px)
                            property("border-radius", "10px"); border(0.px)
                            backgroundColor(if (composerText.trim().isNotEmpty() && !isStreaming) Color("#1a1a2e") else Color("#e5e5e5"))
                            color(Color("white")); cursor(if (isStreaming || composerText.trim().isEmpty()) "not-allowed" else "pointer")
                            display(DisplayMode.Flex); alignItems(AlignItems.Center); justifyContent(JustifyContent.Center)
                            property("flex-shrink", "0")
                        }
                    }) { Span(attrs = { style { fontSize(18.px) } }) { Text("\u2191") } }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    text: String, isUser: Boolean, time: String,
    citations: List<CitationResponse> = emptyList(),
    isStreaming: Boolean = false
) {
    Div(attrs = { style { display(DisplayMode.Flex); justifyContent(if (isUser) JustifyContent.End else JustifyContent.Start) } }) {
        Div(attrs = { style { property("max-width", "85%") } }) {
            Div(attrs = {
                style {
                    property("border-radius", if (isUser) "18px 18px 4px 18px" else "18px 18px 18px 4px")
                    padding(12.px, 18.px)
                    backgroundColor(if (isUser) Color("#1a1a2e") else Color("#f7f7f8"))
                    color(if (isUser) Color("white") else Color("#2d2d2d"))
                    fontSize(15.px); property("line-height", "1.6"); whiteSpace("pre-wrap")
                    property("word-break", "break-word")
                    property("box-shadow", "0 1px 2px rgba(0,0,0,0.05)")
                }
            }) {
                if (isStreaming && text.isEmpty()) {
                    Span(attrs = { style { property("animation", "blink 1s infinite") } }) { Text("\u258C") }
                } else {
                    Text(text)
                    if (isStreaming) {
                        Span(attrs = { style { property("animation", "blink 1s infinite"); color(Color("#888")) } }) { Text("\u258C") }
                    }
                }
            }
            if (!isUser && citations.isNotEmpty()) {
                Div(attrs = { style { display(DisplayMode.Flex); property("flex-wrap", "wrap"); property("gap", "6px"); marginTop(8.px) } }) {
                    for (citation in citations) {
                        A(href = citation.url, attrs = {
                            target("_blank"); rel("noreferrer noopener")
                            title(citation.snippet.ifEmpty { citation.title })
                            style {
                                fontSize(12.px); color(Color("#666")); textDecoration("none")
                                padding(4.px, 10.px); property("border-radius", "6px")
                                border(1.px, LineStyle.Solid, Color("#e5e5e5"))
                                backgroundColor(Color("#fafafa"))
                            }
                        }) {
                            Text("${if (citation.sourceType == "WEB") "\uD83C\uDF10" else "\uD83C\uDFDB"} ${citation.title.ifEmpty { getHostname(citation.url) }}")
                        }
                    }
                }
            }
            if (time.isNotEmpty()) {
                Span(attrs = { style { fontSize(11.px); color(Color("#999")); marginTop(4.px); display(DisplayMode.Block) } }) { Text(time) }
            }
        }
    }
}

@Composable
private fun Sidebar(
    chats: List<ChatResponse>, selectedChatId: String, session: SessionState,
    isRefreshingChats: Boolean, isStreaming: Boolean,
    onSelectChat: (String) -> Unit, onNewChat: () -> Unit, onLogout: () -> Unit,
    onSearchChange: (String) -> Unit, onRefresh: () -> Unit
) {
    Aside(attrs = { style { backgroundColor(Color("#171717")); color(Color("white")); display(DisplayMode.Flex); flexDirection(FlexDirection.Column); property("height", "100vh") } }) {
        Div(attrs = { style { padding(16.px); flexGrow(1.0); display(DisplayMode.Flex); flexDirection(FlexDirection.Column) } }) {
            Button(attrs = {
                onClick { onNewChat() }
                style {
                    property("width", "100%"); padding(12.px); fontSize(14.px); fontWeight("500")
                    property("border-radius", "8px"); border(1.px, LineStyle.Solid, Color("rgba(255,255,255,0.2)"))
                    backgroundColor(Color("transparent")); color(Color("white"))
                    cursor("pointer"); marginBottom(16.px)
                }
            }) { Text("+ New chat") }

            Div(attrs = { style { display(DisplayMode.Flex); flexDirection(FlexDirection.Column); flexGrow(1.0); property("overflow-y", "auto") } }) {
                if (chats.isEmpty()) {
                    P(attrs = { style { fontSize(13.px); color(Color("rgba(255,255,255,0.4)")); padding(8.px) } }) { Text("No conversations yet") }
                } else {
                    for (chat in chats) {
                        val selected = chat.id == selectedChatId
                        Button(attrs = {
                            onClick { onSelectChat(chat.id) }
                            disabled(isStreaming)
                            style {
                                display(DisplayMode.Block); property("width", "100%"); textAlign(TextAlign.Left); cursor(if (isStreaming) "not-allowed" else "pointer")
                                padding(10.px, 12.px); fontSize(14.px); property("border-radius", "8px"); border(0.px)
                                backgroundColor(if (selected) Color("rgba(255,255,255,0.1)") else Color("transparent"))
                                color(if (selected) Color("white") else Color("rgba(255,255,255,0.7)"))
                                property("margin-bottom", "2px")
                            }
                        }) {
                            Div(attrs = { style { fontSize(14.px); fontWeight("500"); whiteSpace("nowrap"); property("overflow", "hidden"); property("text-overflow", "ellipsis") } }) { Text(getChatTitle(chat)) }
                            Div(attrs = { style { fontSize(12.px); color(Color("rgba(255,255,255,0.4)")); whiteSpace("nowrap"); property("overflow", "hidden"); property("text-overflow", "ellipsis"); marginTop(2.px) } }) { Text(getChatPreview(chat)) }
                        }
                    }
                }
            }

            Div(attrs = { style { borderTop(1.px, LineStyle.Solid, Color("rgba(255,255,255,0.1)")); paddingTop(12.px) } }) {
                Div(attrs = { style { display(DisplayMode.Flex); alignItems(AlignItems.Center); gap(10.px); marginBottom(12.px) } }) {
                    Div(attrs = { style { width(32.px); height(32.px); property("border-radius", "6px"); backgroundColor(Color("#333")); display(DisplayMode.Flex); alignItems(AlignItems.Center); justifyContent(JustifyContent.Center); fontSize(14.px); fontWeight("600") } }) { Text(session.email.take(2).uppercase()) }
                    Span(attrs = { style { fontSize(13.px); color(Color("rgba(255,255,255,0.7)")) } }) { Text(session.email) }
                }
                Button(attrs = {
                    onClick { onLogout() }
                    style { property("width", "100%"); padding(8.px); fontSize(13.px); property("border-radius", "6px"); border(0.px); backgroundColor(Color("rgba(255,255,255,0.1)")); color(Color("rgba(255,255,255,0.6)")); cursor("pointer") }
                }) { Text("Log out") }
            }
        }
    }
}

@Composable
private fun Field(label: String, type: String, value: String, onChange: (String) -> Unit) {
    Div(attrs = { style { marginBottom(0.px) } }) {
        Span(attrs = { style { display(DisplayMode.Block); fontSize(12.px); fontWeight("600"); color(Color("#555")); marginBottom(6.px) } }) { Text(label) }
        Input(type = if (type == "password") InputType.Password else InputType.Text, attrs = {
            if (type == "email") attr("type", "email")
            value(value)
            attr("required", "")
            onInput { event -> onChange((event.target as HTMLInputElement).value) }
            style { property("width", "100%"); padding(10.px, 12.px); fontSize(14.px); property("border-radius", "8px"); border(1.px, LineStyle.Solid, Color("#e5e5e5")); backgroundColor(Color("#fafafa")); property("outline", "none") }
        })
    }
}

@Composable
private fun NoticeBanner(notice: Notice) {
    val (borderColor, bgColor, textColor) = when (notice.tone) {
        "success" -> Triple("#a7f3d0", "#ecfdf5", "#065f46")
        "error" -> Triple("#fecaca", "#fef2f2", "#991b1b")
        else -> Triple("#e5e5e5", "#fafafa", "#555")
    }
    Div(attrs = { style { property("border-radius", "8px"); border(1.px, LineStyle.Solid, Color(borderColor)); backgroundColor(Color(bgColor)); color(Color(textColor)); padding(12.px, 16.px); fontSize(14.px) } }) {
        Div(attrs = { style { fontWeight("600") } }) { Text(notice.title) }
        if (notice.detail.isNotBlank()) {
            Div(attrs = { style { marginTop(4.px); fontSize(13.px) } }) { Text(notice.detail) }
        }
    }
}

@Composable
private fun Spacer(height: Int) {
    Div(attrs = { style { height(height.px) } })
}

private fun sortChats(chats: List<ChatResponse>): List<ChatResponse> =
    chats.sortedByDescending { it.updatedAt }

private fun getChatTitle(chat: ChatResponse): String {
    val firstUserMsg = chat.messages.find { it.senderType == "USER" }
    return firstUserMsg?.let { truncate(it.originalText.replace(Regex("\\s+"), " ").trim(), 52) } ?: "New conversation"
}

private fun getChatPreview(chat: ChatResponse): String {
    val lastMsg = chat.messages.lastOrNull()
    return lastMsg?.let { truncate(it.originalText.replace(Regex("\\s+"), " ").trim(), 88) } ?: "No messages yet"
}

private fun truncate(value: String, length: Int): String =
    if (value.length <= length) value else "${value.slice(0 until length - 1)}..."

private fun formatMessageTime(value: String): String {
    try {
        val parts = value.split("T")
        if (parts.size < 2) return ""
        val timeParts = parts[1].split(":").take(2)
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: return ""
        val minute = timeParts.getOrNull(1) ?: "00"
        val ampm = if (hour >= 12) "PM" else "AM"
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        return "$h:$minute $ampm"
    } catch (_: Exception) { return "" }
}

private fun getHostname(url: String): String {
    return try {
        url.removePrefix("https://").removePrefix("http://").split("/").firstOrNull() ?: url
    } catch (_: Exception) { url }
}

private fun isUnauthorized(error: Exception): Boolean =
    error is ApiClientError && error.status == 401

private fun saveSession(session: SessionState) {
    localStorage.setItem(SESSION_KEY, sessionJson.encodeToString(session))
}

private fun loadSession(): SessionState? {
    val saved = localStorage.getItem(SESSION_KEY) ?: return null
    return try {
        sessionJson.decodeFromString<SessionState>(saved)
    } catch (_: Exception) { null }
}

private suspend fun refreshChats(
    apiClient: ApiClient,
    onChats: (List<ChatResponse>) -> Unit,
    onNotice: (Notice) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    onLoading(true)
    try {
        onChats(sortChats(apiClient.listChats()))
    } catch (error: Exception) {
        if (error is ApiClientError && error.status == 401) {
            onNotice(Notice("error", "Session expired", "Sign in again to continue."))
        } else {
            val msg = if (error is ApiClientError) "${error.code}: ${error.message}" else error.message ?: "Unknown error"
            onNotice(Notice("error", "Unable to load chats", msg))
        }
    } finally {
        onLoading(false)
    }
}
