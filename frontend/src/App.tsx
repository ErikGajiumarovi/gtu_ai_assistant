import {
  Activity,
  Bot,
  LogIn,
  LogOut,
  MessageSquareText,
  RefreshCw,
  Search,
  Send,
  ShieldCheck,
  Sparkles,
  Trash2,
  UserRoundPlus,
} from "lucide-react";
import { useDeferredValue, useEffect, useRef, useState } from "react";
import {
  ApiClientError,
  clearAuthToken,
  type ChatResponse,
  checkHealth,
  continueChatWithAgent,
  createChatWithAgent,
  deleteChat,
  listChats,
  loginIn,
  registerUser,
  setAuthToken,
  type UserResponse,
} from "./lib/api";

type Notice = {
  tone: "success" | "error" | "info";
  title: string;
  detail: string;
};

type HealthState = "idle" | "online" | "offline" | "checking";
type AuthMode = "login" | "register";

type RegistrationDraft = {
  name: string;
  lastName: string;
  email: string;
  password: string;
};

type LoginDraft = {
  email: string;
  password: string;
};

type SessionState = {
  email: string;
  jwt: string;
};

const SESSION_STORAGE_KEY = "gtu-ai-assistant.session";

function App() {
  const [authMode, setAuthMode] = useState<AuthMode>("login");
  const [health, setHealth] = useState<HealthState>("idle");
  const [notice, setNotice] = useState<Notice>({
    tone: "info",
    title: "Workspace ready",
    detail: "Sign in to open the chat workspace and talk to the assistant.",
  });
  const [session, setSession] = useState<SessionState | null>(() => {
    const saved = localStorage.getItem(SESSION_STORAGE_KEY);

    if (!saved) {
      return null;
    }

    try {
      return JSON.parse(saved) as SessionState;
    } catch {
      return null;
    }
  });
  const [registeredUser, setRegisteredUser] = useState<UserResponse | null>(null);
  const [registrationDraft, setRegistrationDraft] = useState<RegistrationDraft>({
    name: "",
    lastName: "",
    email: "",
    password: "",
  });
  const [loginDraft, setLoginDraft] = useState<LoginDraft>({
    email: "",
    password: "",
  });
  const [chats, setChats] = useState<ChatResponse[]>([]);
  const [selectedChatId, setSelectedChatId] = useState("");
  const [chatSearch, setChatSearch] = useState("");
  const deferredChatSearch = useDeferredValue(chatSearch);
  const [composerText, setComposerText] = useState("");
  const [isRegistering, setIsRegistering] = useState(false);
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [isRefreshingChats, setIsRefreshingChats] = useState(false);
  const [isCreatingChat, setIsCreatingChat] = useState(false);
  const [isContinuingChat, setIsContinuingChat] = useState(false);
  const [isDeletingChat, setIsDeletingChat] = useState(false);
  const messageViewportRef = useRef<HTMLDivElement | null>(null);

  const selectedChat = chats.find((chat) => chat.id === selectedChatId) ?? null;
  const filteredChats = (() => {
    const query = deferredChatSearch.trim().toLowerCase();

    if (!query) {
      return chats;
    }

    return chats.filter((chat) => {
      const title = getChatTitle(chat).toLowerCase();
      const preview = getChatPreview(chat).toLowerCase();

      return title.includes(query) || preview.includes(query);
    });
  })();
  const isSending = isCreatingChat || isContinuingChat;

  useEffect(() => {
    if (session) {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
      setAuthToken(session.jwt);
      return;
    }

    localStorage.removeItem(SESSION_STORAGE_KEY);
    clearAuthToken();
  }, [session]);

  useEffect(() => {
    if (session?.jwt) {
      setAuthToken(session.jwt);
    }

    void refreshHealth();
  }, []);

  useEffect(() => {
    if (session) {
      void refreshChats();
      return;
    }

    setChats([]);
    setSelectedChatId("");
    setComposerText("");
  }, [session]);

  useEffect(() => {
    const viewport = messageViewportRef.current;

    if (!viewport) {
      return;
    }

    viewport.scrollTop = viewport.scrollHeight;
  }, [selectedChat?.id, selectedChat?.messages.length]);

  async function refreshHealth() {
    setHealth("checking");

    try {
      await checkHealth();
      setHealth("online");
    } catch {
      setHealth("offline");
    }
  }

  async function refreshChats() {
    if (!session) {
      return;
    }

    setIsRefreshingChats(true);

    try {
      const nextChats = sortChats(await listChats());
      setChats(nextChats);

      if (nextChats.length === 0) {
        setSelectedChatId("");
        return;
      }

      if (!nextChats.some((chat) => chat.id === selectedChatId)) {
        setSelectedChatId(nextChats[0].id);
      }
    } catch (error) {
      if (isUnauthorized(error)) {
        handleExpiredSession();
        return;
      }

      showError("Unable to load chats", error);
    } finally {
      setIsRefreshingChats(false);
    }
  }

  async function handleRegister(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsRegistering(true);

    try {
      const user = await registerUser(registrationDraft);
      setRegisteredUser(user);
      setAuthMode("login");
      setLoginDraft({
        email: user.email,
        password: registrationDraft.password,
      });
      setRegistrationDraft({
        name: "",
        lastName: "",
        email: "",
        password: "",
      });
      setNotice({
        tone: "success",
        title: "Account created",
        detail: `${user.name} ${user.lastName} can now sign in with ${user.email}.`,
      });
    } catch (error) {
      showError("Registration failed", error);
    } finally {
      setIsRegistering(false);
    }
  }

  async function handleLogin(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsLoggingIn(true);

    try {
      const response = await loginIn(loginDraft);
      setSession({
        email: loginDraft.email.trim().toLowerCase(),
        jwt: response.jwt,
      });
      setNotice({
        tone: "success",
        title: "Signed in",
        detail: "Your chat workspace is connected and ready.",
      });
    } catch (error) {
      showError("Login failed", error);
    } finally {
      setIsLoggingIn(false);
    }
  }

  async function handleSubmitMessage(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!session) {
      setNotice({
        tone: "error",
        title: "Sign in required",
        detail: "Open a session before sending a message to the assistant.",
      });
      return;
    }

    const text = composerText.trim();

    if (!text) {
      return;
    }

    if (!selectedChat) {
      setIsCreatingChat(true);

      try {
        const chat = await createChatWithAgent({ originalText: text });
        setChats((current) => sortChats([chat, ...current.filter((item) => item.id !== chat.id)]));
        setSelectedChatId(chat.id);
        setComposerText("");
        setNotice({
          tone: "success",
          title: "Conversation started",
          detail: "The assistant replied in a fresh chat.",
        });
      } catch (error) {
        if (isUnauthorized(error)) {
          handleExpiredSession();
          return;
        }

        showError("Unable to start chat", error);
      } finally {
        setIsCreatingChat(false);
      }

      return;
    }

    setIsContinuingChat(true);

    try {
      const updatedChat = await continueChatWithAgent(selectedChat.id, {
        originalText: text,
      });
      setChats((current) =>
        sortChats([updatedChat, ...current.filter((chat) => chat.id !== updatedChat.id)]),
      );
      setSelectedChatId(updatedChat.id);
      setComposerText("");
    } catch (error) {
      if (isUnauthorized(error)) {
        handleExpiredSession();
        return;
      }

      showError("Unable to send message", error);
    } finally {
      setIsContinuingChat(false);
    }
  }

  async function handleDeleteSelectedChat() {
    if (!selectedChat) {
      return;
    }

    setIsDeletingChat(true);

    try {
      await deleteChat(selectedChat.id);
      const nextChats = chats.filter((chat) => chat.id !== selectedChat.id);
      setChats(nextChats);
      setSelectedChatId(nextChats[0]?.id ?? "");
      setNotice({
        tone: "success",
        title: "Conversation deleted",
        detail: "The selected chat was removed from the workspace.",
      });
    } catch (error) {
      if (isUnauthorized(error)) {
        handleExpiredSession();
        return;
      }

      showError("Delete failed", error);
    } finally {
      setIsDeletingChat(false);
    }
  }

  function handleLogout() {
    setSession(null);
    setChats([]);
    setSelectedChatId("");
    setComposerText("");
    setNotice({
      tone: "info",
      title: "Signed out",
      detail: "The local session token has been cleared.",
    });
  }

  function handleExpiredSession() {
    setSession(null);
    setChats([]);
    setSelectedChatId("");
    setComposerText("");
    setNotice({
      tone: "error",
      title: "Session expired",
      detail: "The saved token is no longer valid. Sign in again to continue.",
    });
  }

  function handleStartNewChat() {
    setSelectedChatId("");
    setComposerText("");
    setNotice({
      tone: "info",
      title: "New chat",
      detail: "Write the first message below to start a fresh conversation.",
    });
  }

  function applyStarterPrompt(value: string) {
    setSelectedChatId("");
    setComposerText(value);
  }

  function showError(title: string, error: unknown) {
    if (error instanceof ApiClientError) {
      setNotice({
        tone: "error",
        title,
        detail: `${error.code}: ${error.message}`,
      });
      return;
    }

    if (error instanceof Error) {
      setNotice({
        tone: "error",
        title,
        detail: error.message,
      });
      return;
    }

    setNotice({
      tone: "error",
      title,
      detail: "Unknown error",
    });
  }

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(248,226,192,0.9),_transparent_28%),radial-gradient(circle_at_top_right,_rgba(214,228,255,0.9),_transparent_26%),linear-gradient(180deg,_#f7f3ec_0%,_#f3efe8_42%,_#ece8df_100%)] text-slate-900">
      <div className="mx-auto flex min-h-screen max-w-[1680px] flex-col lg:grid lg:grid-cols-[320px_minmax(0,1fr)]">
        <aside className="border-b border-slate-900/8 bg-white/65 backdrop-blur-xl lg:border-r lg:border-b-0">
          <div className="flex h-full flex-col gap-6 p-4 sm:p-6">
            <section className="rounded-[2rem] border border-slate-900/8 bg-slate-950 px-5 py-5 text-white shadow-[0_24px_60px_rgba(15,23,42,0.22)]">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="inline-flex items-center gap-2 rounded-full border border-white/12 bg-white/8 px-3 py-1 text-[11px] font-semibold tracking-[0.24em] uppercase text-white/80">
                    <ShieldCheck className="h-3.5 w-3.5" />
                    GTU AI Assistant
                  </div>
                  <h1 className="mt-4 font-['Sora'] text-[1.7rem] leading-tight">
                    Chat workspace instead of a test dashboard.
                  </h1>
                  <p className="mt-3 text-sm leading-6 text-white/68">
                    One sidebar, one conversation view, one composer. The API stays the same, the
                    product surface becomes familiar.
                  </p>
                </div>

                <button
                  type="button"
                  onClick={() => void refreshHealth()}
                  className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/12 bg-white/8 text-white transition hover:bg-white/14"
                  aria-label="Refresh backend health"
                >
                  <RefreshCw className={`h-4 w-4 ${health === "checking" ? "animate-spin" : ""}`} />
                </button>
              </div>

              <div className="mt-6 grid grid-cols-2 gap-3">
                <MiniStat
                  label="Backend"
                  value={formatHealthLabel(health)}
                  accent={health === "online" ? "emerald" : health === "offline" ? "rose" : "amber"}
                />
                <MiniStat
                  label="Chats"
                  value={String(chats.length)}
                  accent="slate"
                />
              </div>
            </section>

            {session ? (
              <>
                <button
                  type="button"
                  onClick={handleStartNewChat}
                  className="inline-flex items-center justify-center gap-2 rounded-[1.35rem] bg-slate-950 px-4 py-3 text-sm font-semibold text-white shadow-[0_16px_34px_rgba(15,23,42,0.15)] transition hover:-translate-y-0.5"
                >
                  <Sparkles className="h-4 w-4" />
                  New chat
                </button>

                <section className="rounded-[1.75rem] border border-slate-900/8 bg-white/85 p-4 shadow-[0_16px_32px_rgba(148,163,184,0.12)]">
                  <div className="relative">
                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                    <input
                      value={chatSearch}
                      onChange={(event) => setChatSearch(event.target.value)}
                      placeholder="Search chats"
                      className="w-full rounded-[1.1rem] border border-slate-200 bg-slate-50 py-2.5 pr-3 pl-9 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:bg-white"
                    />
                  </div>

                  <div className="mt-4 flex items-center justify-between text-xs font-medium text-slate-500">
                    <span>Recent conversations</span>
                    <span>{filteredChats.length}</span>
                  </div>

                  <div className="mt-3 flex max-h-[40vh] flex-col gap-2 overflow-y-auto pr-1 lg:max-h-none lg:flex-1">
                    {filteredChats.length === 0 ? (
                      <SidebarEmptyState
                        title="No conversations"
                        detail="Start a new chat to populate the workspace."
                      />
                    ) : (
                      filteredChats.map((chat) => {
                        const selected = chat.id === selectedChatId;

                        return (
                          <button
                            key={chat.id}
                            type="button"
                            onClick={() => setSelectedChatId(chat.id)}
                            className={`rounded-[1.3rem] border px-4 py-3 text-left transition ${
                              selected
                                ? "border-slate-900 bg-slate-950 text-white shadow-[0_14px_28px_rgba(15,23,42,0.16)]"
                                : "border-slate-200 bg-slate-50 text-slate-900 hover:border-slate-300 hover:bg-white"
                            }`}
                          >
                            <div className="flex items-start justify-between gap-3">
                              <div className="min-w-0">
                                <p className="truncate text-sm font-semibold">
                                  {getChatTitle(chat)}
                                </p>
                                <p
                                  className={`mt-1 text-xs leading-5 ${
                                    selected ? "text-white/72" : "text-slate-500"
                                  }`}
                                >
                                  {getChatPreview(chat)}
                                </p>
                              </div>
                              <span
                                className={`shrink-0 rounded-full px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.18em] ${
                                  selected ? "bg-white/10 text-white/80" : "bg-slate-200 text-slate-500"
                                }`}
                              >
                                {formatSidebarDate(chat.updatedAt)}
                              </span>
                            </div>
                          </button>
                        );
                      })
                    )}
                  </div>
                </section>

                <section className="mt-auto rounded-[1.75rem] border border-slate-900/8 bg-white/85 p-4 shadow-[0_16px_32px_rgba(148,163,184,0.12)]">
                  <div className="flex items-start gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-950 text-sm font-semibold text-white">
                      {session.email.slice(0, 2).toUpperCase()}
                    </div>
                    <div className="min-w-0">
                      <p className="text-xs font-semibold tracking-[0.18em] text-slate-500 uppercase">
                        Active session
                      </p>
                      <p className="truncate pt-1 text-sm font-semibold text-slate-900">
                        {session.email}
                      </p>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={handleLogout}
                    className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-[1.1rem] border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-100"
                  >
                    <LogOut className="h-4 w-4" />
                    Log out
                  </button>
                </section>
              </>
            ) : (
              <section className="rounded-[1.75rem] border border-slate-900/8 bg-white/85 p-5 text-sm text-slate-600 shadow-[0_16px_32px_rgba(148,163,184,0.12)]">
                <p className="text-xs font-semibold tracking-[0.18em] text-slate-500 uppercase">
                  Session
                </p>
                <p className="mt-2 leading-6">
                  Sign in to unlock the conversation list, message stream, and protected API routes.
                </p>
              </section>
            )}
          </div>
        </aside>

        <main className="flex min-h-[calc(100vh-120px)] flex-col lg:min-h-screen lg:overflow-hidden">
          {!session ? (
            <div className="flex flex-1 items-center px-4 py-8 sm:px-6 lg:px-10">
              <div className="grid w-full gap-6 xl:grid-cols-[minmax(0,1.1fr)_460px]">
                <section className="rounded-[2.25rem] border border-white/65 bg-white/70 p-6 shadow-[0_24px_70px_rgba(148,163,184,0.14)] backdrop-blur-xl sm:p-8 lg:p-10">
                  <div className="inline-flex items-center gap-2 rounded-full bg-amber-100 px-3 py-1 text-[11px] font-semibold tracking-[0.24em] text-amber-900 uppercase">
                    <Bot className="h-3.5 w-3.5" />
                    Chat-first frontend
                  </div>
                  <h2 className="mt-5 max-w-3xl font-['Sora'] text-4xl leading-tight text-slate-950 sm:text-5xl">
                    The product should feel like a real assistant, not a backend control panel.
                  </h2>
                  <p className="mt-5 max-w-2xl text-base leading-8 text-slate-600">
                    This layout mirrors the structure users expect from ChatGPT, Claude, and Gemini:
                    persistent conversations on the left, focused reading in the center, and one
                    clear place to type.
                  </p>

                  <div className="mt-8 grid gap-4 md:grid-cols-3">
                    <FeatureCard
                      title="Single composer"
                      detail="One input handles both new conversations and replies."
                    />
                    <FeatureCard
                      title="Conversation memory"
                      detail="Recent chats stay in a dedicated sidebar instead of being buried in forms."
                    />
                    <FeatureCard
                      title="Clean auth gate"
                      detail="Users sign in once, then stay inside the workspace."
                    />
                  </div>
                </section>

                <section className="rounded-[2.25rem] border border-white/65 bg-white/82 p-6 shadow-[0_24px_70px_rgba(148,163,184,0.14)] backdrop-blur-xl sm:p-8">
                  <div className="inline-flex rounded-full border border-slate-200 bg-slate-100 p-1">
                    <button
                      type="button"
                      onClick={() => setAuthMode("login")}
                      className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                        authMode === "login"
                          ? "bg-white text-slate-950 shadow-sm"
                          : "text-slate-500 hover:text-slate-800"
                      }`}
                    >
                      Log in
                    </button>
                    <button
                      type="button"
                      onClick={() => setAuthMode("register")}
                      className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                        authMode === "register"
                          ? "bg-white text-slate-950 shadow-sm"
                          : "text-slate-500 hover:text-slate-800"
                      }`}
                    >
                      Register
                    </button>
                  </div>

                  <div className="mt-6">
                    <NoticeBanner notice={notice} />
                  </div>

                  {authMode === "login" ? (
                    <form className="mt-6 space-y-4" onSubmit={(event) => void handleLogin(event)}>
                      <div>
                        <p className="font-['Sora'] text-2xl text-slate-950">Welcome back</p>
                        <p className="mt-2 text-sm leading-6 text-slate-500">
                          Sign in to continue with your existing JWT-backed session.
                        </p>
                      </div>

                      <Field
                        label="Email"
                        type="email"
                        value={loginDraft.email}
                        onChange={(value) => setLoginDraft((current) => ({ ...current, email: value }))}
                      />
                      <Field
                        label="Password"
                        type="password"
                        value={loginDraft.password}
                        onChange={(value) =>
                          setLoginDraft((current) => ({ ...current, password: value }))
                        }
                      />

                      <PrimaryButton
                        type="submit"
                        busy={isLoggingIn}
                        icon={<LogIn className="h-4 w-4" />}
                      >
                        Log in
                      </PrimaryButton>

                      <p className="text-sm leading-6 text-slate-500">
                        {registeredUser
                          ? `Latest registered account: ${registeredUser.email}`
                          : "After registration, the login form is prefilled automatically."}
                      </p>
                    </form>
                  ) : (
                    <form className="mt-6 space-y-4" onSubmit={(event) => void handleRegister(event)}>
                      <div>
                        <p className="font-['Sora'] text-2xl text-slate-950">Create account</p>
                        <p className="mt-2 text-sm leading-6 text-slate-500">
                          Open a user account first, then continue into the chat workspace.
                        </p>
                      </div>

                      <div className="grid gap-4 sm:grid-cols-2">
                        <Field
                          label="First name"
                          value={registrationDraft.name}
                          onChange={(value) =>
                            setRegistrationDraft((current) => ({ ...current, name: value }))
                          }
                        />
                        <Field
                          label="Last name"
                          value={registrationDraft.lastName}
                          onChange={(value) =>
                            setRegistrationDraft((current) => ({ ...current, lastName: value }))
                          }
                        />
                      </div>
                      <Field
                        label="Email"
                        type="email"
                        value={registrationDraft.email}
                        onChange={(value) =>
                          setRegistrationDraft((current) => ({ ...current, email: value }))
                        }
                      />
                      <Field
                        label="Password"
                        type="password"
                        value={registrationDraft.password}
                        onChange={(value) =>
                          setRegistrationDraft((current) => ({ ...current, password: value }))
                        }
                      />

                      <PrimaryButton
                        type="submit"
                        busy={isRegistering}
                        icon={<UserRoundPlus className="h-4 w-4" />}
                      >
                        Register
                      </PrimaryButton>
                    </form>
                  )}
                </section>
              </div>
            </div>
          ) : (
            <>
              <header className="border-b border-slate-900/8 bg-white/55 px-4 py-4 backdrop-blur-xl sm:px-6 lg:px-10">
                <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                  <div>
                    <p className="text-xs font-semibold tracking-[0.24em] text-slate-500 uppercase">
                      {selectedChat ? "Current conversation" : "Draft conversation"}
                    </p>
                    <h2 className="mt-2 font-['Sora'] text-2xl text-slate-950 sm:text-3xl">
                      {selectedChat ? getChatTitle(selectedChat) : "Start a new conversation"}
                    </h2>
                    <p className="mt-2 text-sm text-slate-500">
                      {selectedChat
                        ? `${selectedChat.messages.length} messages • updated ${formatFullDateTime(selectedChat.updatedAt)}`
                        : "Type the first prompt below and the backend will create the chat automatically."}
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-3">
                    <button
                      type="button"
                      onClick={() => void refreshChats()}
                      className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:border-slate-300 hover:bg-slate-50"
                    >
                      <RefreshCw
                        className={`h-4 w-4 ${isRefreshingChats ? "animate-spin" : ""}`}
                      />
                      Refresh
                    </button>

                    {selectedChat ? (
                      <button
                        type="button"
                        onClick={() => void handleDeleteSelectedChat()}
                        disabled={isDeletingChat}
                        className="inline-flex items-center gap-2 rounded-full border border-rose-200 bg-rose-50 px-4 py-2.5 text-sm font-medium text-rose-700 transition hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        <Trash2 className="h-4 w-4" />
                        {isDeletingChat ? "Deleting..." : "Delete chat"}
                      </button>
                    ) : null}
                  </div>
                </div>
              </header>

              <div className="flex flex-1 flex-col gap-5 px-4 py-5 sm:px-6 lg:min-h-0 lg:px-10 lg:py-6">
                <NoticeBanner notice={notice} />

                <section className="flex flex-1 flex-col overflow-hidden rounded-[2rem] border border-white/60 bg-white/70 shadow-[0_24px_70px_rgba(148,163,184,0.12)] backdrop-blur-xl">
                  <div
                    ref={messageViewportRef}
                    className="flex-1 space-y-4 overflow-y-auto px-4 py-5 sm:px-6 lg:px-8 lg:py-7"
                  >
                    {selectedChat ? (
                      selectedChat.messages.map((message) => {
                        const isUser = message.senderType === "USER";
                        const citations = message.citations ?? [];

                        return (
                          <article
                            key={message.id}
                            className={`flex ${isUser ? "justify-end" : "justify-start"}`}
                          >
                            <div
                              className={`max-w-3xl rounded-[1.75rem] px-4 py-3 sm:px-5 sm:py-4 ${
                                isUser
                                  ? "bg-slate-950 text-white shadow-[0_20px_40px_rgba(15,23,42,0.16)]"
                                  : "border border-slate-200 bg-white text-slate-800 shadow-[0_16px_34px_rgba(148,163,184,0.14)]"
                              }`}
                            >
                              <div className="mb-2 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em]">
                                <span className={isUser ? "text-white/72" : "text-slate-500"}>
                                  {isUser ? "You" : "Assistant"}
                                </span>
                                <span className={isUser ? "text-white/50" : "text-slate-400"}>
                                  {formatMessageTime(message.createdAt)}
                                </span>
                              </div>
                              <p className="text-sm leading-7 whitespace-pre-wrap sm:text-[15px]">
                                {message.originalText}
                              </p>
                              {!isUser && citations.length > 0 ? (
                                <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
                                  {citations.map((citation) => (
                                    <a
                                      key={`${message.id}-${citation.url}`}
                                      href={citation.url}
                                      target="_blank"
                                      rel="noreferrer"
                                      title={citation.snippet || citation.title}
                                      className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500 transition hover:border-slate-300 hover:bg-white hover:text-slate-700"
                                    >
                                      {citation.sourceType === "WEB" ? "Web" : "GTU"} ·{" "}
                                      {citation.title || new URL(citation.url).hostname}
                                    </a>
                                  ))}
                                </div>
                              ) : null}
                            </div>
                          </article>
                        );
                      })
                    ) : (
                      <div className="flex h-full min-h-[360px] flex-col items-center justify-center text-center">
                        <div className="flex h-16 w-16 items-center justify-center rounded-[1.5rem] bg-slate-950 text-white">
                          <MessageSquareText className="h-7 w-7" />
                        </div>
                        <h3 className="mt-6 font-['Sora'] text-3xl text-slate-950">
                          Start a chat like a real assistant app
                        </h3>
                        <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-500 sm:text-base">
                          Pick a conversation on the left or use one of these starters. Sending the
                          first message creates the chat automatically.
                        </p>
                        <div className="mt-8 grid w-full max-w-3xl gap-3 md:grid-cols-3">
                          {STARTER_PROMPTS.map((prompt) => (
                            <button
                              key={prompt}
                              type="button"
                              onClick={() => applyStarterPrompt(prompt)}
                              className="rounded-[1.5rem] border border-slate-200 bg-white px-4 py-4 text-left text-sm leading-6 text-slate-700 transition hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-[0_16px_28px_rgba(148,163,184,0.14)]"
                            >
                              {prompt}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  <form
                    onSubmit={(event) => void handleSubmitMessage(event)}
                    className="border-t border-slate-900/8 px-4 py-4 sm:px-6 lg:px-8"
                  >
                    <div className="rounded-[1.75rem] border border-slate-200 bg-white p-3 shadow-[0_18px_42px_rgba(148,163,184,0.14)]">
                      <textarea
                        value={composerText}
                        onChange={(event) => setComposerText(event.target.value)}
                        rows={3}
                        placeholder={
                          selectedChat
                            ? "Reply to the assistant..."
                            : "Message the assistant to begin a new conversation..."
                        }
                        className="min-h-24 w-full resize-none border-0 bg-transparent px-2 py-2 text-sm leading-7 text-slate-900 outline-none placeholder:text-slate-400 sm:text-[15px]"
                      />

                      <div className="mt-3 flex flex-col gap-3 border-t border-slate-100 pt-3 sm:flex-row sm:items-center sm:justify-between">
                        <div className="flex items-center gap-2 text-xs text-slate-500">
                          <Activity
                            className={`h-3.5 w-3.5 ${
                              isSending || isRefreshingChats ? "animate-pulse" : ""
                            }`}
                          />
                          {selectedChat
                            ? "Messages are appended to the current conversation."
                            : "The first message will create a new chat."}
                        </div>

                        <button
                          type="submit"
                          disabled={isSending || composerText.trim().length === 0}
                          className="inline-flex items-center justify-center gap-2 rounded-full bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white shadow-[0_16px_34px_rgba(15,23,42,0.18)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-55"
                        >
                          {isSending ? (
                            <RefreshCw className="h-4 w-4 animate-spin" />
                          ) : (
                            <Send className="h-4 w-4" />
                          )}
                          {selectedChat ? "Send message" : "Start chat"}
                        </button>
                      </div>
                    </div>
                  </form>
                </section>
              </div>
            </>
          )}
        </main>
      </div>
    </div>
  );
}

type FieldProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: React.HTMLInputTypeAttribute;
};

function Field({ label, value, onChange, type = "text" }: FieldProps) {
  return (
    <label className="block">
      <span className="mb-2 block text-xs font-semibold tracking-[0.18em] text-slate-500 uppercase">
        {label}
      </span>
      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        required
        className="w-full rounded-[1.1rem] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:bg-white"
      />
    </label>
  );
}

type PrimaryButtonProps = {
  type: "button" | "submit";
  busy: boolean;
  icon: React.ReactNode;
  children: React.ReactNode;
};

function PrimaryButton({ type, busy, icon, children }: PrimaryButtonProps) {
  return (
    <button
      type={type}
      disabled={busy}
      className="inline-flex w-full items-center justify-center gap-2 rounded-[1.1rem] bg-slate-950 px-4 py-3 text-sm font-semibold text-white shadow-[0_16px_34px_rgba(15,23,42,0.16)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
    >
      {busy ? <RefreshCw className="h-4 w-4 animate-spin" /> : icon}
      {busy ? "Working..." : children}
    </button>
  );
}

type FeatureCardProps = {
  title: string;
  detail: string;
};

function FeatureCard({ title, detail }: FeatureCardProps) {
  return (
    <div className="rounded-[1.6rem] border border-slate-200 bg-white/75 p-4">
      <p className="font-['Sora'] text-lg text-slate-950">{title}</p>
      <p className="mt-2 text-sm leading-6 text-slate-500">{detail}</p>
    </div>
  );
}

type NoticeBannerProps = {
  notice: Notice;
};

function NoticeBanner({ notice }: NoticeBannerProps) {
  const toneClasses =
    notice.tone === "success"
      ? "border-emerald-200 bg-emerald-50 text-emerald-900"
      : notice.tone === "error"
        ? "border-rose-200 bg-rose-50 text-rose-900"
        : "border-slate-200 bg-white text-slate-700";

  return (
    <div className={`rounded-[1.4rem] border px-4 py-3 ${toneClasses}`}>
      <p className="font-semibold">{notice.title}</p>
      <p className="mt-1 text-sm leading-6">{notice.detail}</p>
    </div>
  );
}

type MiniStatProps = {
  label: string;
  value: string;
  accent: "emerald" | "rose" | "amber" | "slate";
};

function MiniStat({ label, value, accent }: MiniStatProps) {
  const accentClasses =
    accent === "emerald"
      ? "bg-emerald-400/14 text-emerald-100"
      : accent === "rose"
        ? "bg-rose-400/14 text-rose-100"
        : accent === "amber"
          ? "bg-amber-300/14 text-amber-100"
          : "bg-white/8 text-white";

  return (
    <div className={`rounded-[1.3rem] px-3 py-3 ${accentClasses}`}>
      <p className="text-[11px] font-semibold tracking-[0.18em] uppercase text-white/64">{label}</p>
      <p className="mt-2 font-['Sora'] text-xl">{value}</p>
    </div>
  );
}

type SidebarEmptyStateProps = {
  title: string;
  detail: string;
};

function SidebarEmptyState({ title, detail }: SidebarEmptyStateProps) {
  return (
    <div className="rounded-[1.3rem] border border-dashed border-slate-200 bg-slate-50 px-4 py-6 text-center">
      <p className="font-semibold text-slate-700">{title}</p>
      <p className="mt-2 text-sm leading-6 text-slate-500">{detail}</p>
    </div>
  );
}

function sortChats(chats: ChatResponse[]) {
  return [...chats].sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt));
}

function getChatTitle(chat: ChatResponse) {
  const firstUserMessage = chat.messages.find((message) => message.senderType === "USER");

  if (!firstUserMessage) {
    return "New conversation";
  }

  return truncate(firstUserMessage.originalText.replace(/\s+/g, " ").trim(), 52);
}

function getChatPreview(chat: ChatResponse) {
  const lastMessage = chat.messages.at(-1);

  if (!lastMessage) {
    return "No messages yet";
  }

  return truncate(lastMessage.originalText.replace(/\s+/g, " ").trim(), 88);
}

function truncate(value: string, length: number) {
  if (value.length <= length) {
    return value;
  }

  return `${value.slice(0, length - 1)}...`;
}

function formatHealthLabel(health: HealthState) {
  if (health === "online") {
    return "Online";
  }

  if (health === "offline") {
    return "Offline";
  }

  if (health === "checking") {
    return "Checking";
  }

  return "Idle";
}

function formatSidebarDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
  }).format(new Date(value));
}

function formatMessageTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatFullDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function isUnauthorized(error: unknown) {
  return error instanceof ApiClientError && error.status === 401;
}

const STARTER_PROMPTS = [
  "Help me summarize the current project structure before we change anything.",
  "Draft a clean frontend architecture for this assistant app.",
  "List the next product-quality improvements for this chat experience.",
];

export default App;
