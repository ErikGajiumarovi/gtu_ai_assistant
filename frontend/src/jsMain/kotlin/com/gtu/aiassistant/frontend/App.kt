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
import org.w3c.dom.*

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

    var notice by remember { mutableStateOf(Notice("info", "Workspace ready", "Sign in to open the chat workspace and talk to the assistant.")) }
    var authMode by remember { mutableStateOf("login") }
    var session by remember { mutableStateOf(loadSession()) }
    var registeredUser by remember { mutableStateOf<UserResponse?>(null) }
    var registrationDraft by remember { mutableStateOf(RegistrationDraft("", "", "", "")) }
    var loginDraft by remember { mutableStateOf(LoginDraft("", "")) }
    var chats by remember { mutableStateOf<List<ChatResponse>>(emptyList()) }
    var selectedChatId by remember { mutableStateOf("") }
    var chatSearch by remember { mutableStateOf("") }
    var composerText by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var isRefreshingChats by remember { mutableStateOf(false) }
    var isCreatingChat by remember { mutableStateOf(false) }
    var isContinuingChat by remember { mutableStateOf(false) }

    val selectedChat = chats.find { it.id == selectedChatId }
    val isSending = isCreatingChat || isContinuingChat
    val deferredSearch = chatSearch.trim().lowercase()
    val filteredChats = if (deferredSearch.isEmpty()) chats
        else chats.filter { chat ->
            getChatTitle(chat).lowercase().contains(deferredSearch) ||
            getChatPreview(chat).lowercase().contains(deferredSearch)
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
            refreshChats(apiClient, { chats = it }, { notice = it }, { isRefreshingChats = it })
        } else {
            chats = emptyList(); selectedChatId = ""; composerText = ""
        }
    }

    val showError: (String, Exception) -> Unit = { title, error ->
        notice = if (error is ApiClientError) Notice("error", title, "${error.code}: ${error.message}")
        else Notice("error", title, error.message ?: "Unknown error")
    }

    Div(attrs = { style { minHeight("100vh"); property("background", "radial-gradient(circle at top left, rgba(248,226,192,0.9), transparent 28%), radial-gradient(circle at top right, rgba(214,228,255,0.9), transparent 26%), linear-gradient(180deg, #f7f3ec 0%, #f3efe8 42%, #ece8df 100%)"); color("#0f172a") } }) {
        val isGrid = session != null
        Div(attrs = { style { margin("0 auto"); minHeight("100vh"); maxWidth("1680px"); property("display", if (isGrid) "grid" else "flex"); property("grid-template-columns", if (isGrid) "320px 1fr" else "none"); flexDirection(FlexDirection.Column) } }) {
            if (session != null) {
                Sidebar(
                    chats = filteredChats, selectedChatId = selectedChatId, session = session!!,
                    isRefreshingChats = isRefreshingChats,
                    onSelectChat = { selectedChatId = it }, onNewChat = {
                        selectedChatId = ""; composerText = ""
                        notice = Notice("info", "New chat", "Write the first message below to start a fresh conversation.")
                    }, onLogout = {
                        session = null; chats = emptyList(); selectedChatId = ""; composerText = ""
                        notice = Notice("info", "Signed out", "The local session token has been cleared.")
                    }, onSearchChange = { chatSearch = it },
                    onRefresh = { scope.launch { refreshChats(apiClient, { chats = it }, { notice = it }, { isRefreshingChats = it }) } }
                )
            }

            Main(attrs = { style { display(DisplayMode.Flex); flexDirection(FlexDirection.Column); flexGrow(1.0); property("min-height", "0"); property("overflow", if (session != null) "hidden" else "visible") } }) {
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
                                notice = Notice("success", "Account created", "${u.name} ${u.lastName} can now sign in with ${u.email}.")
                            } catch (e: Exception) { showError("Registration failed", e) } finally { isRegistering = false }
                        } }
                    )
                } else {
                    ChatScreen(
                        notice = notice, selectedChat = selectedChat, composerText = composerText,
                        isSending = isSending, isRefreshingChats = isRefreshingChats,
                        onComposerChange = { composerText = it },
                        onSubmit = { scope.launch {
                            val text = composerText.trim(); if (text.isEmpty()) return@launch
                            if (selectedChat == null) {
                                isCreatingChat = true
                                try {
                                    val chat = apiClient.createChatWithAgent(CreateChatWithAgentRequest(text))
                                    chats = sortChats(listOf(chat) + chats.filter { it.id != chat.id })
                                    selectedChatId = chat.id; composerText = ""
                                } catch (e: Exception) {
                                    if (isUnauthorized(e)) { session = null; notice = Notice("error", "Session expired", "Sign in again.") }
                                    else showError("Unable to start chat", e)
                                } finally { isCreatingChat = false }
                            } else {
                                isContinuingChat = true
                                try {
                                    val chat = apiClient.continueChatWithAgent(selectedChat.id, ContinueChatWithAgentRequest(text))
                                    chats = sortChats(listOf(chat) + chats.filter { it.id != chat.id })
                                    selectedChatId = chat.id; composerText = ""
                                } catch (e: Exception) {
                                    if (isUnauthorized(e)) { session = null; notice = Notice("error", "Session expired", "Sign in again.") }
                                    else showError("Unable to send message", e)
                                } finally { isContinuingChat = false }
                            }
                        } }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthPage(
    authMode: String, notice: Notice, loginDraft: LoginDraft, registrationDraft: RegistrationDraft,
    registeredUser: UserResponse?, isRegistering: Boolean, isLoggingIn: Boolean,
    onSetAuthMode: (String) -> Unit,
    onLoginDraftChange: (LoginDraft) -> Unit, onRegistrationDraftChange: (RegistrationDraft) -> Unit,
    onLogin: () -> Unit, onRegister: () -> Unit
) {
    Div(attrs = { style { display(DisplayMode.Flex); flex("1"); alignItems(AlignItems.Center); padding(16.px, 24.px); property("padding-top", "2rem"); property("padding-bottom", "2rem") } }) {
        Div(attrs = { style { margin("0 auto"); width("100%"); maxWidth("460px") } }) {
            Section(attrs = { style { property("border-radius", "2.25rem"); border(1.px, LineStyle.Solid, Color("rgba(255,255,255,0.65)")); backgroundColor(Color("rgba(255,255,255,0.82)")); padding(24.px, 32.px); property("box-shadow", "0 24px 70px rgba(148,163,184,0.14)"); property("backdrop-filter", "blur(24px)") } }) {
                Div(attrs = { style { display(DisplayMode.InlineFlex); property("border-radius", "9999px"); border(1.px, LineStyle.Solid, Color("#e2e8f0")); backgroundColor(Color("#f1f5f9")); padding(4.px) } }) {
                    Button(attrs = {
                        onClick { onSetAuthMode("login") }
                        style { property("border-radius", "9999px"); padding(8.px, 16.px); fontSize(14.px); fontWeight("600"); property("background", if (authMode == "login") "white" else "transparent"); color(if (authMode == "login") Color("#0f172a") else Color("#64748b")); property("box-shadow", if (authMode == "login") "0 1px 3px rgba(0,0,0,0.1)" else "none") }
                    }) { Text("Log in") }
                    Button(attrs = {
                        onClick { onSetAuthMode("register") }
                        style { property("border-radius", "9999px"); padding(8.px, 16.px); fontSize(14.px); fontWeight("600"); property("background", if (authMode == "register") "white" else "transparent"); color(if (authMode == "register") Color("#0f172a") else Color("#64748b")); property("box-shadow", if (authMode == "register") "0 1px 3px rgba(0,0,0,0.1)" else "none") }
                    }) { Text("Register") }
                }

                if (notice.tone != "info") { Div(attrs = { style { property("margin-top", "24px") } }) { NoticeBanner(notice = notice) } }

                if (authMode == "login") {
                    Form(attrs = { onSubmit { it.preventDefault(); onLogin() }; style { property("margin-top", "24px") }; classes("space-y-4") }) {
                        Div(attrs = { style { property("margin-bottom", "16px") } }) { P(attrs = { style { fontFamily("Sora, sans-serif"); fontSize(24.px); color(Color("#0f172a")); margin(0.px) } }) { Text("Welcome back") } }
                        Field("Email", "email", loginDraft.email) { onLoginDraftChange(loginDraft.copy(email = it)) }
                        Field("Password", "password", loginDraft.password) { onLoginDraftChange(loginDraft.copy(password = it)) }
                        PrimaryButton(busy = isLoggingIn, attrs = { attr("type", "submit") }) { Text("Log in") }
                        P(attrs = { style { fontSize(14.px); color(Color("#64748b")) } }) {
                            Text(registeredUser?.let { "Latest registered account: ${it.email}" } ?: "After registration, the login form is prefilled automatically.")
                        }
                    }
                } else {
                    Form(attrs = { onSubmit { it.preventDefault(); onRegister() }; style { property("margin-top", "24px") }; classes("space-y-4") }) {
                        Div(attrs = { style { property("margin-bottom", "16px") } }) {
                            P(attrs = { style { fontFamily("Sora, sans-serif"); fontSize(24.px); color(Color("#0f172a")); margin(0.px) } }) { Text("Create account") }
                            P(attrs = { style { property("margin-top", "8px"); fontSize(14.px); color(Color("#64748b")) } }) { Text("Open a user account first, then continue into the chat workspace.") }
                        }
                        Div(attrs = { style { display(DisplayMode.Grid); property("grid-template-columns", "1fr 1fr"); property("gap", "16px") } }) {
                            Field("First name", "text", registrationDraft.name) { onRegistrationDraftChange(registrationDraft.copy(name = it)) }
                            Field("Last name", "text", registrationDraft.lastName) { onRegistrationDraftChange(registrationDraft.copy(lastName = it)) }
                        }
                        Field("Email", "email", registrationDraft.email) { onRegistrationDraftChange(registrationDraft.copy(email = it)) }
                        Field("Password", "password", registrationDraft.password) { onRegistrationDraftChange(registrationDraft.copy(password = it)) }
                        PrimaryButton(busy = isRegistering, attrs = { attr("type", "submit") }) { Text("Register") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    notice: Notice, selectedChat: ChatResponse?, composerText: String,
    isSending: Boolean, isRefreshingChats: Boolean,
    onComposerChange: (String) -> Unit, onSubmit: () -> Unit
) {
    val messageViewportRef = remember { mutableStateOf<HTMLElement?>(null) }

    LaunchedEffect(selectedChat?.id, selectedChat?.messages?.size) {
        messageViewportRef.value?.let { it.scrollTop = it.scrollHeight.toDouble() }
    }

    Div(attrs = { style { display(DisplayMode.Flex); flexDirection(FlexDirection.Column); flexGrow(1.0); property("min-height", "0"); property("gap", "20px"); padding(20.px, 24.px) } }) {
        if (notice.tone != "info") { NoticeBanner(notice = notice) }

        Section(attrs = { style { display(DisplayMode.Flex); flexDirection(FlexDirection.Column); flexGrow(1.0); property("min-height", "0"); property("overflow", "hidden"); property("border-radius", "2rem"); border(1.px, LineStyle.Solid, Color("rgba(255,255,255,0.6)")); backgroundColor(Color("rgba(255,255,255,0.7)")); property("box-shadow", "0 24px 70px rgba(148,163,184,0.12)"); property("backdrop-filter", "blur(24px)") } }) {
            Div(attrs = {
                ref { messageViewportRef.value = it }
                style { flexGrow(1.0); property("min-height", "0"); property("overflow-y", "auto"); padding(20.px, 24.px) }
            }) {
                if (selectedChat != null) {
                    for (message in selectedChat.messages) {
                        val isUser = message.senderType == "USER"
                        val citations = message.citations

                        Div(attrs = { style { display(DisplayMode.Flex); justifyContent(if (isUser) JustifyContent.End else JustifyContent.Start); property("margin-bottom", "16px") } }) {
                            Div(attrs = { style { maxWidth("48rem"); property("border-radius", "1.75rem"); padding(12.px, 16.px); property("background", if (isUser) "#0f172a" else "white"); color(if (isUser) Color("white") else Color("#1e293b")); border(1.px, LineStyle.Solid, if (isUser) Color("transparent") else Color("#e2e8f0")); property("box-shadow", if (isUser) "0 20px 40px rgba(15,23,42,0.16)" else "0 16px 34px rgba(148,163,184,0.14)") } }) {
                                Div(attrs = { style { display(DisplayMode.Flex); alignItems(AlignItems.Center); property("gap", "8px"); fontSize(11.px); fontWeight("600"); property("text-transform", "uppercase"); property("letter-spacing", "0.18em"); property("margin-bottom", "8px") } }) {
                                    Span(attrs = { style { color(if (isUser) Color("rgba(255,255,255,0.72)") else Color("#64748b")) } }) { Text(if (isUser) "You" else "Assistant") }
                                    Span(attrs = { style { color(if (isUser) Color("rgba(255,255,255,0.5)") else Color("#94a3b8")) } }) { Text(formatMessageTime(message.createdAt)) }
                                }
                                P(attrs = { style { fontSize(15.px); margin(0.px); whiteSpace("pre-wrap"); lineHeight(1.75) } }) { Text(message.originalText) }
                                if (!isUser && citations.isNotEmpty()) {
                                    Div(attrs = { style { display(DisplayMode.Flex); property("flex-wrap", "wrap"); property("gap", "8px"); property("margin-top", "16px"); borderTop(1.px, LineStyle.Solid, Color("#f1f5f9")); paddingTop(12.px) } }) {
                                        for (citation in citations) {
                                            A(href = citation.url, attrs = {
                                                target("_blank"); rel("noreferrer")
                                                title(citation.snippet.ifEmpty { citation.title })
                                                style { property("border-radius", "9999px"); border(1.px, LineStyle.Solid, Color("#e2e8f0")); backgroundColor(Color("#f8fafc")); padding(4.px, 12.px); fontSize(11.px); fontWeight("600"); property("text-transform", "uppercase"); property("letter-spacing", "0.12em"); color(Color("#64748b")); cursor("pointer") }
                                            }) {
                                                Text("${if (citation.sourceType == "WEB") "Web" else "GTU"} \u00B7 ${citation.title.ifEmpty { getHostname(citation.url) }}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Form(attrs = { onSubmit { it.preventDefault(); onSubmit() }; style { borderTop(1.px, LineStyle.Solid, Color("rgba(15,23,42,0.08)")); padding(16.px, 24.px) } }) {
                Div(attrs = { style { property("border-radius", "1.75rem"); border(1.px, LineStyle.Solid, Color("#e2e8f0")); backgroundColor(Color("white")); padding(12.px); property("box-shadow", "0 18px 42px rgba(148,163,184,0.14)") } }) {
                    val textareaRef = remember { mutableStateOf<HTMLTextAreaElement?>(null) }

                    LaunchedEffect(composerText) {
                        textareaRef.value?.let { ta ->
                            val lineHeight = 1.75 * 15
                            val maxHeight = lineHeight * 10 + 16
                            ta.style.height = "0px"
                            ta.style.height = "${kotlin.math.min(ta.scrollHeight.toDouble(), maxHeight)}px"
                            ta.style.overflowY = if (ta.scrollHeight.toDouble() > maxHeight) "auto" else "hidden"
                        }
                    }

                    TextArea(attrs = {
                        ref { textareaRef.value = it }
                        value(composerText)
                        rows(1)
                        placeholder(if (selectedChat != null) "Reply to the assistant..." else "Message the assistant to begin a new conversation...")
                        onInput { event -> onComposerChange((event.target as HTMLTextAreaElement).value) }
                        style { width("100%"); property("resize", "none"); property("overflow", "hidden"); border(0.px); fontSize(15.px); color(Color("#0f172a")); property("outline", "none"); property("max-height", "280px"); backgroundColor(Color("transparent")) }
                    })

                    Div(attrs = { style { display(DisplayMode.Flex); justifyContent(JustifyContent.SpaceBetween); alignItems(AlignItems.Center); property("margin-top", "12px"); borderTop(1.px, LineStyle.Solid, Color("#f1f5f9")); paddingTop(12.px) } }) {
                        Span(attrs = { style { fontSize(12.px); color(Color("#64748b")) } }) { Text("\u25CF") }

                        Button(attrs = {
                            disabled(isSending || composerText.trim().isEmpty())
                            attr("type", "submit")
                            style {
                                display(DisplayMode.InlineFlex); alignItems(AlignItems.Center); justifyContent(JustifyContent.Center); property("gap", "8px")
                                property("border-radius", "9999px"); backgroundColor(Color("#0f172a")); padding(10.px, 16.px)
                                fontSize(14.px); fontWeight("600"); color(Color("white"))
                                property("box-shadow", "0 16px 34px rgba(15,23,42,0.18)")
                                property("opacity", if (isSending || composerText.trim().isEmpty()) "0.55" else "1")
                                property("cursor", if (isSending || composerText.trim().isEmpty()) "not-allowed" else "pointer")
                            }
                        }) {
                            Text(if (isSending) "\u21BB" else "\u2192")
                            Text(if (selectedChat != null) "Send message" else "Start chat")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    chats: List<ChatResponse>, selectedChatId: String, session: SessionState,
    isRefreshingChats: Boolean,
    onSelectChat: (String) -> Unit, onNewChat: () -> Unit, onLogout: () -> Unit,
    onSearchChange: (String) -> Unit, onRefresh: () -> Unit
) {
    Aside(attrs = { style { borderBottom(1.px, LineStyle.Solid, Color("rgba(15,23,42,0.08)")); property("background", "rgba(255,255,255,0.65)"); property("backdrop-filter", "blur(24px)") } }) {
        Div(attrs = { style { display(DisplayMode.Flex); flexDirection(FlexDirection.Column); property("gap", "24px"); padding(16.px, 24.px); height("100%") } }) {
            Button(attrs = {
                onClick { onNewChat() }
                style { display(DisplayMode.InlineFlex); alignItems(AlignItems.Center); justifyContent(JustifyContent.Center); property("gap", "8px"); property("border-radius", "1.35rem"); backgroundColor(Color("#0f172a")); padding(12.px, 16.px); fontSize(14.px); fontWeight("600"); color(Color("white")); property("box-shadow", "0 16px 34px rgba(15,23,42,0.15)"); width("100%") }
            }) { Text("\u2728 New chat") }

            Section(attrs = { style { property("border-radius", "1.75rem"); border(1.px, LineStyle.Solid, Color("rgba(15,23,42,0.08)")); property("background", "rgba(255,255,255,0.85)"); padding(16.px); property("box-shadow", "0 16px 32px rgba(148,163,184,0.12)") } }) {
                Input(type = InputType.Text, attrs = {
                    placeholder("Search chats")
                    onInput { event -> onSearchChange((event.target as HTMLInputElement).value) }
                    style { width("100%"); property("border-radius", "1.1rem"); border(1.px, LineStyle.Solid, Color("#e2e8f0")); backgroundColor(Color("#f8fafc")); padding(10.px, 12.px); fontSize(14.px); color(Color("#0f172a")); property("outline", "none") }
                })

                Div(attrs = { style { display(DisplayMode.Flex); justifyContent(JustifyContent.SpaceBetween); alignItems(AlignItems.Center); property("margin-top", "16px"); fontSize(12.px); fontWeight("500"); color(Color("#64748b")) } }) {
                    Span { Text("Recent conversations") }
                    Span { Text("${chats.size}") }
                }

                Div(attrs = { style { display(DisplayMode.Flex); flexDirection(FlexDirection.Column); property("gap", "8px"); property("margin-top", "12px"); property("max-height", if (chats.isNotEmpty()) "40vh" else "none"); property("overflow-y", "auto") } }) {
                    if (chats.isEmpty()) {
                        SidebarEmptyState("No conversations", "Start a new chat to populate the workspace.")
                    } else {
                        for (chat in chats) {
                            val selected = chat.id == selectedChatId
                            Button(attrs = {
                                onClick { onSelectChat(chat.id) }
                                style { display(DisplayMode.Block); width("100%"); property("border-radius", "1.3rem"); padding(12.px, 16.px); textAlign(TextAlign.Left); property("background", if (selected) "#0f172a" else "#f8fafc"); color(if (selected) Color("white") else Color("#0f172a")); border(1.px, LineStyle.Solid, if (selected) Color("#0f172a") else Color("#e2e8f0")); property("box-shadow", if (selected) "0 14px 28px rgba(15,23,42,0.16)" else "none") }
                            }) {
                                Div(attrs = { style { display(DisplayMode.Flex); justifyContent(JustifyContent.SpaceBetween); alignItems(AlignItems.FlexStart); property("gap", "12px") } }) {
                                    Div(attrs = { style { property("min-width", "0"); property("flex", "1") } }) {
                                        P(attrs = { style { fontSize(14.px); fontWeight("600"); margin(0.px); property("white-space", "nowrap"); property("overflow", "hidden"); property("text-overflow", "ellipsis") } }) { Text(getChatTitle(chat)) }
                                        P(attrs = { style { fontSize(12.px); property("margin-top", "4px"); color(if (selected) Color("rgba(255,255,255,0.72)") else Color("#64748b")) } }) { Text(getChatPreview(chat)) }
                                    }
                                    Span(attrs = { style { property("border-radius", "9999px"); padding(4.px, 8.px); fontSize(10.px); fontWeight("600"); property("text-transform", "uppercase"); property("letter-spacing", "0.18em"); property("background", if (selected) "rgba(255,255,255,0.1)" else "#e2e8f0"); color(if (selected) "rgba(255,255,255,0.8)" else "#64748b") } }) { Text(formatSidebarDate(chat.updatedAt)) }
                                }
                            }
                        }
                    }
                }
            }

            Section(attrs = { style { property("margin-top", "auto"); property("border-radius", "1.75rem"); border(1.px, LineStyle.Solid, Color("rgba(15,23,42,0.08)")); property("background", "rgba(255,255,255,0.85)"); padding(16.px); property("box-shadow", "0 16px 32px rgba(148,163,184,0.12)") } }) {
                Div(attrs = { style { display(DisplayMode.Flex); alignItems(AlignItems.FlexStart); property("gap", "12px") } }) {
                    Div(attrs = { style { display(DisplayMode.Flex); width(44.px); height(44.px); alignItems(AlignItems.Center); justifyContent(JustifyContent.Center); property("border-radius", "1rem"); backgroundColor(Color("#0f172a")); fontSize(14.px); fontWeight("600"); color(Color("white")); property("flex-shrink", "0") } }) { Text(session.email.slice(0..1).uppercase()) }
                    Div(attrs = { style { property("min-width", "0") } }) {
                        P(attrs = { style { fontSize(12.px); fontWeight("600"); property("letter-spacing", "0.18em"); color(Color("#64748b")); property("text-transform", "uppercase"); margin(0.px) } }) { Text("Active session") }
                        P(attrs = { style { fontSize(14.px); fontWeight("600"); color(Color("#0f172a")); property("margin-top", "4px"); property("white-space", "nowrap"); property("overflow", "hidden"); property("text-overflow", "ellipsis") } }) { Text(session.email) }
                    }
                }
                Button(attrs = {
                    onClick { onLogout() }
                    style { display(DisplayMode.InlineFlex); width("100%"); alignItems(AlignItems.Center); justifyContent(JustifyContent.Center); property("gap", "8px"); property("margin-top", "16px"); property("border-radius", "1.1rem"); border(1.px, LineStyle.Solid, Color("#e2e8f0")); backgroundColor(Color("#f8fafc")); padding(10.px, 16.px); fontSize(14.px); fontWeight("500"); color(Color("#64748b")) }
                }) { Text("\u2190 Log out") }
            }
        }
    }
}

@Composable
private fun Field(label: String, type: String, value: String, onChange: (String) -> Unit) {
    Div(attrs = { style { display(DisplayMode.Block) } }) {
        Span(attrs = { style { display(DisplayMode.Block); property("margin-bottom", "8px"); fontSize(12.px); fontWeight("600"); property("letter-spacing", "0.18em"); color(Color("#64748b")); property("text-transform", "uppercase") } }) { Text(label) }
        Input(type = if (type == "password") InputType.Password else if (type == "email") InputType.Text else InputType.Text, attrs = {
            if (type == "email") attr("type", "email")
            value(value)
            attr("required", "")
            onInput { event -> onChange((event.target as HTMLInputElement).value) }
            style { width("100%"); property("border-radius", "1.1rem"); border(1.px, LineStyle.Solid, Color("#e2e8f0")); backgroundColor(Color("#f8fafc")); padding(12.px, 16.px); fontSize(14.px); color(Color("#0f172a")); property("outline", "none") }
        })
    }
}

@Composable
private fun PrimaryButton(busy: Boolean, attrs: (AttrsBuilder<HTMLButtonElement>.() -> Unit)? = null, content: @Composable () -> Unit) {
    Button(attrs = {
        disabled(busy)
        style {
            display(DisplayMode.InlineFlex); width("100%"); alignItems(AlignItems.Center); justifyContent(JustifyContent.Center); property("gap", "8px")
            property("border-radius", "1.1rem"); backgroundColor(Color("#0f172a")); padding(12.px, 16.px)
            fontSize(14.px); fontWeight("600"); color(Color("white"))
            property("box-shadow", "0 16px 34px rgba(15,23,42,0.16)")
            property("cursor", if (busy) "not-allowed" else "pointer")
            property("opacity", if (busy) "0.6" else "1")
        }
        if (attrs != null) attrs()
    }) {
        if (busy) { Span { Text("\u21BB") }; Text("Working...") }
        else content()
    }
}

@Composable
private fun NoticeBanner(notice: Notice) {
    val borderColor = when (notice.tone) {
        "success" -> "#a7f3d0"; "error" -> "#fecaca"; else -> "#e2e8f0"
    }
    val bgColor = when (notice.tone) {
        "success" -> "#ecfdf5"; "error" -> "#fff1f2"; else -> "white"
    }
    val textColor = when (notice.tone) {
        "success" -> "#064e3b"; "error" -> "#991b1b"; else -> "#334155"
    }
    Div(attrs = { style { property("border-radius", "1.4rem"); border(1.px, LineStyle.Solid, Color(borderColor)); backgroundColor(Color(bgColor)); color(Color(textColor)); padding(12.px, 16.px) } }) {
        P(attrs = { style { fontWeight("600"); margin(0.px) } }) { Text(notice.title) }
        P(attrs = { style { property("margin-top", "4px"); fontSize(14.px); margin(0.px) } }) { Text(notice.detail) }
    }
}

@Composable
private fun SidebarEmptyState(title: String, detail: String) {
    Div(attrs = { style { property("border-radius", "1.3rem"); border(1.px, LineStyle.Dashed, Color("#e2e8f0")); backgroundColor(Color("#f8fafc")); padding(16.px, 24.px); textAlign(TextAlign.Center) } }) {
        P(attrs = { style { fontWeight("600"); color(Color("#334155")); margin(0.px) } }) { Text(title) }
        P(attrs = { style { property("margin-top", "8px"); fontSize(14.px); color(Color("#64748b")); margin(0.px) } }) { Text(detail) }
    }
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

private fun formatSidebarDate(value: String): String {
    try {
        val parts = value.split("T").firstOrNull()?.split("-") ?: return value
        if (parts.size != 3) return value
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val month = parts[1].toIntOrNull()?.let { monthNames.getOrNull(it - 1) } ?: parts[1]
        val day = parts[2].toIntOrNull()?.toString() ?: parts[2]
        return "$month $day"
    } catch (_: Exception) { return value }
}

private fun formatMessageTime(value: String): String {
    try {
        val parts = value.split("T")
        if (parts.size < 2) return value
        val timeParts = parts[1].split(":").take(2)
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: return value
        val minute = timeParts.getOrNull(1) ?: "00"
        val ampm = if (hour >= 12) "PM" else "AM"
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        return "$h:$minute $ampm"
    } catch (_: Exception) { return value }
}

private fun isUnauthorized(error: Exception): Boolean =
    error is ApiClientError && error.status == 401

private fun getHostname(url: String): String {
    return try {
        url.removePrefix("https://").removePrefix("http://").split("/").firstOrNull() ?: url
    } catch (_: Exception) { url }
}

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
            onNotice(Notice("error", "Session expired", "The saved token is no longer valid. Sign in again to continue."))
        } else {
            val msg = if (error is ApiClientError) "${error.code}: ${error.message}" else error.message ?: "Unknown error"
            onNotice(Notice("error", "Unable to load chats", msg))
        }
    } finally {
        onLoading(false)
    }
}
