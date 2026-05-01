import {
  Activity,
  Bot,
  MessageSquareText,
  Plus,
  RefreshCw,
  Send,
  Sparkles,
  Trash2,
  UserRoundPlus,
  Users,
} from "lucide-react";
import { useDeferredValue, useEffect, useState } from "react";
import {
  ApiClientError,
  type ChatResponse,
  checkHealth,
  continueChatWithAgent,
  createChatWithAgent,
  createUser,
  deleteChat,
  listChats,
  type UserResponse,
} from "./lib/api";

type Notice = {
  tone: "success" | "error" | "info";
  title: string;
  detail: string;
};

type HealthState = "idle" | "online" | "offline" | "checking";

type UserDraft = {
  id: string;
  name: string;
  lastName: string;
  email: string;
};

const STORAGE_KEY = "gtu-ai-assistant.users";

function App() {
  const [health, setHealth] = useState<HealthState>("idle");
  const [notice, setNotice] = useState<Notice>({
    tone: "info",
    title: "Frontend ready",
    detail: "Create a user first, then start a chat with the backend agent.",
  });

  const [users, setUsers] = useState<UserResponse[]>(() => {
    const saved = localStorage.getItem(STORAGE_KEY);

    if (!saved) {
      return [];
    }

    try {
      return JSON.parse(saved) as UserResponse[];
    } catch {
      return [];
    }
  });

  const [selectedUserId, setSelectedUserId] = useState<string>("");
  const [chats, setChats] = useState<ChatResponse[]>([]);
  const [selectedChatId, setSelectedChatId] = useState<string>("");
  const [chatSearch, setChatSearch] = useState("");
  const deferredChatSearch = useDeferredValue(chatSearch);

  const [userDraft, setUserDraft] = useState<UserDraft>({
    id: crypto.randomUUID(),
    name: "",
    lastName: "",
    email: "",
  });
  const [createChatText, setCreateChatText] = useState("");
  const [continueText, setContinueText] = useState("");

  const [isCreatingUser, setIsCreatingUser] = useState(false);
  const [isRefreshingChats, setIsRefreshingChats] = useState(false);
  const [isCreatingChat, setIsCreatingChat] = useState(false);
  const [isContinuingChat, setIsContinuingChat] = useState(false);
  const [isDeletingChat, setIsDeletingChat] = useState(false);

  const selectedUser = users.find((user) => user.id === selectedUserId) ?? null;
  const selectedChat = chats.find((chat) => chat.id === selectedChatId) ?? null;

  const filteredChats = (() => {
    const query = deferredChatSearch.trim().toLowerCase();

    if (!query) {
      return chats;
    }

    return chats.filter((chat) =>
      chat.messages.some((message) => message.originalText.toLowerCase().includes(query)),
    );
  })();

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(users));
  }, [users]);

  useEffect(() => {
    void refreshHealth();
  }, []);

  useEffect(() => {
    if (selectedUserId) {
      void refreshChats(selectedUserId);
    } else {
      setChats([]);
      setSelectedChatId("");
    }
  }, [selectedUserId]);

  async function refreshHealth() {
    setHealth("checking");

    try {
      await checkHealth();
      setHealth("online");
    } catch {
      setHealth("offline");
    }
  }

  async function refreshChats(userId: string) {
    setIsRefreshingChats(true);

    try {
      const nextChats = await listChats(userId);
      setChats(nextChats);

      if (nextChats.length === 0) {
        setSelectedChatId("");
      } else if (!nextChats.some((chat) => chat.id === selectedChatId)) {
        setSelectedChatId(nextChats[0].id);
      }
    } catch (error) {
      showError("Unable to load chats", error);
    } finally {
      setIsRefreshingChats(false);
    }
  }

  async function handleCreateUser(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsCreatingUser(true);

    try {
      const createdUser = await createUser(userDraft);

      setUsers((current) => {
        const next = current.filter((user) => user.id !== createdUser.id);
        return [createdUser, ...next];
      });
      setSelectedUserId(createdUser.id);
      setUserDraft({
        id: crypto.randomUUID(),
        name: "",
        lastName: "",
        email: "",
      });
      setNotice({
        tone: "success",
        title: "User created",
        detail: `${createdUser.name} ${createdUser.lastName} is now ready for chat sessions.`,
      });
    } catch (error) {
      showError("User creation failed", error);
    } finally {
      setIsCreatingUser(false);
    }
  }

  async function handleCreateChat(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!selectedUser) {
      setNotice({
        tone: "error",
        title: "Select a user first",
        detail: "Chat creation needs an existing user identity.",
      });
      return;
    }

    setIsCreatingChat(true);

    try {
      const chat = await createChatWithAgent({
        userId: selectedUser.id,
        originalText: createChatText,
      });

      setCreateChatText("");
      setChats((current) => [chat, ...current.filter((item) => item.id !== chat.id)]);
      setSelectedChatId(chat.id);
      setNotice({
        tone: "success",
        title: "Chat created",
        detail: "The backend created the conversation and generated the first AI answer.",
      });
    } catch (error) {
      showError("Chat creation failed", error);
    } finally {
      setIsCreatingChat(false);
    }
  }

  async function handleContinueChat(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!selectedUser || !selectedChat) {
      setNotice({
        tone: "error",
        title: "Choose a chat first",
        detail: "Continuing a conversation requires an active chat and an owner.",
      });
      return;
    }

    setIsContinuingChat(true);

    try {
      const updatedChat = await continueChatWithAgent(selectedChat.id, {
        userId: selectedUser.id,
        originalText: continueText,
      });

      setContinueText("");
      setChats((current) =>
        current.map((chat) => (chat.id === updatedChat.id ? updatedChat : chat)),
      );
      setSelectedChatId(updatedChat.id);
      setNotice({
        tone: "success",
        title: "Conversation continued",
        detail: "The backend accepted the new user message and generated another reply.",
      });
    } catch (error) {
      showError("Continuation failed", error);
    } finally {
      setIsContinuingChat(false);
    }
  }

  async function handleDeleteChat() {
    if (!selectedUser || !selectedChat) {
      return;
    }

    setIsDeletingChat(true);

    try {
      await deleteChat(selectedUser.id, selectedChat.id);

      const nextChats = chats.filter((chat) => chat.id !== selectedChat.id);
      setChats(nextChats);
      setSelectedChatId(nextChats[0]?.id ?? "");
      setNotice({
        tone: "success",
        title: "Chat deleted",
        detail: "The conversation was removed from the backend.",
      });
    } catch (error) {
      showError("Delete failed", error);
    } finally {
      setIsDeletingChat(false);
    }
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
    <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(255,180,118,0.22),_transparent_28%),radial-gradient(circle_at_top_right,_rgba(73,211,255,0.16),_transparent_22%),linear-gradient(180deg,_#07111f_0%,_#0b1527_45%,_#10192b_100%)] text-slate-100">
      <div className="mx-auto flex max-w-7xl flex-col gap-8 px-4 py-6 sm:px-6 lg:px-8">
        <header className="overflow-hidden rounded-[2rem] border border-white/10 bg-white/7 shadow-[0_24px_80px_rgba(1,6,19,0.45)] backdrop-blur-xl">
          <div className="grid gap-6 px-6 py-7 lg:grid-cols-[1.3fr_0.7fr] lg:px-8">
            <div className="space-y-4">
              <div className="inline-flex items-center gap-2 rounded-full border border-amber-300/20 bg-amber-300/10 px-3 py-1 text-xs font-semibold tracking-[0.24em] text-amber-100 uppercase">
                <Sparkles className="h-3.5 w-3.5" />
                GTU AI Assistant
              </div>
              <div className="space-y-3">
                <h1 className="max-w-3xl font-['Space_Grotesk'] text-4xl leading-none tracking-tight sm:text-5xl">
                  One control surface for every backend endpoint you already have.
                </h1>
                <p className="max-w-2xl text-sm leading-6 text-slate-300 sm:text-base">
                  Create users, open agent chats, continue conversations, inspect persisted history,
                  and remove chats through a single React interface wired directly to the current
                  Ktor backend.
                </p>
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
              <StatCard
                icon={<Users className="h-5 w-5" />}
                label="Stored frontend users"
                value={String(users.length)}
                accent="from-cyan-400/35 to-sky-500/10"
              />
              <StatCard
                icon={<MessageSquareText className="h-5 w-5" />}
                label="Loaded chats"
                value={String(chats.length)}
                accent="from-orange-400/35 to-rose-500/10"
              />
            </div>
          </div>
        </header>

        <div className="grid gap-8 xl:grid-cols-[360px_minmax(0,1fr)]">
          <aside className="space-y-6">
            <Panel
              title="Backend Pulse"
              subtitle="Live check against the Ktor service"
              icon={<Activity className="h-4 w-4" />}
              actions={
                <button
                  type="button"
                  onClick={() => void refreshHealth()}
                  className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/8 px-3 py-1.5 text-xs font-medium text-slate-200 transition hover:bg-white/14"
                >
                  <RefreshCw className={`h-3.5 w-3.5 ${health === "checking" ? "animate-spin" : ""}`} />
                  Refresh
                </button>
              }
            >
              <div className="flex items-center justify-between rounded-3xl border border-white/10 bg-slate-950/25 px-4 py-4">
                <div>
                  <p className="text-xs uppercase tracking-[0.2em] text-slate-400">Health status</p>
                  <p className="mt-1 font-['Space_Grotesk'] text-2xl">
                    {health === "online" && "Online"}
                    {health === "offline" && "Offline"}
                    {health === "checking" && "Checking"}
                    {health === "idle" && "Idle"}
                  </p>
                </div>
                <div
                  className={`h-4 w-4 rounded-full ${
                    health === "online"
                      ? "bg-emerald-400 shadow-[0_0_28px_rgba(52,211,153,0.8)]"
                      : health === "offline"
                        ? "bg-rose-400 shadow-[0_0_28px_rgba(251,113,133,0.65)]"
                        : "bg-amber-300 shadow-[0_0_28px_rgba(252,211,77,0.65)]"
                  }`}
                />
              </div>
            </Panel>

            <Panel
              title="Create User"
              subtitle="Calls POST /api/users"
              icon={<UserRoundPlus className="h-4 w-4" />}
            >
              <form className="space-y-3" onSubmit={(event) => void handleCreateUser(event)}>
                <Field
                  label="User UUID"
                  value={userDraft.id}
                  onChange={(value) => setUserDraft((current) => ({ ...current, id: value }))}
                />
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field
                    label="First name"
                    value={userDraft.name}
                    onChange={(value) => setUserDraft((current) => ({ ...current, name: value }))}
                  />
                  <Field
                    label="Last name"
                    value={userDraft.lastName}
                    onChange={(value) =>
                      setUserDraft((current) => ({ ...current, lastName: value }))
                    }
                  />
                </div>
                <Field
                  label="Email"
                  type="email"
                  value={userDraft.email}
                  onChange={(value) => setUserDraft((current) => ({ ...current, email: value }))}
                />
                <div className="flex gap-3">
                  <ActionButton type="submit" busy={isCreatingUser} icon={<Plus className="h-4 w-4" />}>
                    Create user
                  </ActionButton>
                  <SecondaryButton
                    type="button"
                    onClick={() =>
                      setUserDraft((current) => ({
                        ...current,
                        id: crypto.randomUUID(),
                      }))
                    }
                  >
                    New UUID
                  </SecondaryButton>
                </div>
              </form>
            </Panel>

            <Panel
              title="Workspace Users"
              subtitle="Stored locally for quick reuse"
              icon={<Users className="h-4 w-4" />}
            >
              <div className="space-y-3">
                {users.length === 0 ? (
                  <EmptyState
                    title="No users yet"
                    detail="Create the first user to unlock the chat workbench."
                  />
                ) : (
                  users.map((user) => {
                    const selected = selectedUserId === user.id;

                    return (
                      <button
                        key={user.id}
                        type="button"
                        onClick={() => setSelectedUserId(user.id)}
                        className={`w-full rounded-3xl border px-4 py-4 text-left transition ${
                          selected
                            ? "border-cyan-300/40 bg-cyan-300/12"
                            : "border-white/10 bg-white/6 hover:bg-white/10"
                        }`}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="font-semibold text-white">
                              {user.name} {user.lastName}
                            </p>
                            <p className="mt-1 text-xs text-slate-400">{user.email}</p>
                          </div>
                          <span className="rounded-full bg-slate-950/35 px-2.5 py-1 text-[10px] font-semibold tracking-[0.2em] text-slate-300 uppercase">
                            v{user.version}
                          </span>
                        </div>
                      </button>
                    );
                  })
                )}
              </div>
            </Panel>
          </aside>

          <main className="space-y-6">
            <Panel
              title="Agent Workbench"
              subtitle="Create, continue, list, and delete chat sessions"
              icon={<Bot className="h-4 w-4" />}
              actions={
                selectedUser ? (
                  <button
                    type="button"
                    onClick={() => void refreshChats(selectedUser.id)}
                    className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/8 px-3 py-1.5 text-xs font-medium text-slate-200 transition hover:bg-white/14"
                  >
                    <RefreshCw
                      className={`h-3.5 w-3.5 ${isRefreshingChats ? "animate-spin" : ""}`}
                    />
                    Reload chats
                  </button>
                ) : null
              }
            >
              {selectedUser ? (
                <div className="space-y-5">
                  <div className="rounded-[1.75rem] border border-cyan-300/15 bg-[linear-gradient(135deg,rgba(34,211,238,0.16),rgba(15,23,42,0.15))] p-4">
                    <p className="text-xs uppercase tracking-[0.2em] text-cyan-100/80">Active user</p>
                    <div className="mt-2 flex flex-wrap items-center gap-3">
                      <h2 className="font-['Space_Grotesk'] text-2xl text-white">
                        {selectedUser.name} {selectedUser.lastName}
                      </h2>
                      <span className="rounded-full border border-white/10 bg-slate-950/25 px-3 py-1 text-xs text-slate-300">
                        {selectedUser.email}
                      </span>
                    </div>
                  </div>

                  <form
                    className="rounded-[1.75rem] border border-white/10 bg-slate-950/25 p-4"
                    onSubmit={(event) => void handleCreateChat(event)}
                  >
                    <div className="mb-3 flex items-center justify-between gap-3">
                      <div>
                        <p className="font-semibold text-white">Start a new chat</p>
                        <p className="text-sm text-slate-400">Calls POST /api/chats/with-agent</p>
                      </div>
                    </div>
                    <textarea
                      value={createChatText}
                      onChange={(event) => setCreateChatText(event.target.value)}
                      required
                      rows={4}
                      placeholder="Write the first user message for a new conversation..."
                      className="min-h-32 w-full rounded-[1.5rem] border border-white/10 bg-white/6 px-4 py-3 text-sm text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-cyan-300/50 focus:bg-white/9"
                    />
                    <div className="mt-4">
                      <ActionButton
                        type="submit"
                        busy={isCreatingChat}
                        icon={<Sparkles className="h-4 w-4" />}
                      >
                        Create chat with agent
                      </ActionButton>
                    </div>
                  </form>

                  <div className="grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)]">
                    <section className="rounded-[1.75rem] border border-white/10 bg-slate-950/25 p-4">
                      <div className="flex items-center justify-between gap-3">
                        <div>
                          <p className="font-semibold text-white">Chat list</p>
                          <p className="text-sm text-slate-400">Calls GET /api/users/{selectedUser.id}/chats</p>
                        </div>
                        <span className="rounded-full bg-white/8 px-2.5 py-1 text-xs text-slate-300">
                          {filteredChats.length}
                        </span>
                      </div>

                      <input
                        value={chatSearch}
                        onChange={(event) => setChatSearch(event.target.value)}
                        placeholder="Filter by message content"
                        className="mt-4 w-full rounded-2xl border border-white/10 bg-white/6 px-4 py-2.5 text-sm text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-cyan-300/45"
                      />

                      <div className="mt-4 space-y-3">
                        {filteredChats.length === 0 ? (
                          <EmptyState
                            title="No chats loaded"
                            detail="Create a conversation or reload the list from backend."
                          />
                        ) : (
                          filteredChats.map((chat) => {
                            const selected = chat.id === selectedChatId;
                            const preview = chat.messages.at(-1)?.originalText ?? "No messages";

                            return (
                              <button
                                key={chat.id}
                                type="button"
                                onClick={() => setSelectedChatId(chat.id)}
                                className={`w-full rounded-3xl border px-4 py-4 text-left transition ${
                                  selected
                                    ? "border-orange-300/45 bg-orange-300/12"
                                    : "border-white/10 bg-white/6 hover:bg-white/10"
                                }`}
                              >
                                <div className="flex items-start justify-between gap-3">
                                  <div className="min-w-0">
                                    <p className="truncate font-semibold text-white">{chat.id}</p>
                                    <p className="mt-1 line-clamp-2 text-sm text-slate-400">{preview}</p>
                                  </div>
                                  <span className="rounded-full bg-slate-950/30 px-2.5 py-1 text-[10px] font-semibold tracking-[0.18em] text-slate-300 uppercase">
                                    v{chat.version}
                                  </span>
                                </div>
                              </button>
                            );
                          })
                        )}
                      </div>
                    </section>

                    <section className="rounded-[1.75rem] border border-white/10 bg-slate-950/25 p-4">
                      {selectedChat ? (
                        <div className="space-y-4">
                          <div className="flex flex-wrap items-start justify-between gap-3">
                            <div>
                              <p className="font-semibold text-white">Conversation detail</p>
                              <p className="mt-1 text-sm text-slate-400">{selectedChat.id}</p>
                            </div>
                            <button
                              type="button"
                              onClick={() => void handleDeleteChat()}
                              disabled={isDeletingChat}
                              className="inline-flex items-center gap-2 rounded-full border border-rose-300/20 bg-rose-300/10 px-3 py-1.5 text-xs font-semibold text-rose-100 transition hover:bg-rose-300/18 disabled:opacity-60"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                              {isDeletingChat ? "Deleting..." : "Delete chat"}
                            </button>
                          </div>

                          <div className="grid gap-3 rounded-[1.5rem] border border-white/8 bg-slate-950/22 p-3 sm:grid-cols-3">
                            <MetaPill label="Created" value={formatDate(selectedChat.createdAt)} />
                            <MetaPill label="Updated" value={formatDate(selectedChat.updatedAt)} />
                            <MetaPill label="Messages" value={String(selectedChat.messages.length)} />
                          </div>

                          <div className="space-y-3 rounded-[1.75rem] border border-white/8 bg-[linear-gradient(180deg,rgba(6,11,23,0.48),rgba(14,20,36,0.7))] p-4">
                            {selectedChat.messages.map((message) => {
                              const isUser = message.senderType === "USER";

                              return (
                                <article
                                  key={message.id}
                                  className={`max-w-[88%] rounded-[1.5rem] px-4 py-3 ${
                                    isUser
                                      ? "ml-auto bg-cyan-300/12 text-cyan-50 ring-1 ring-cyan-200/10"
                                      : "bg-white/7 text-slate-100 ring-1 ring-white/8"
                                  }`}
                                >
                                  <div className="mb-2 flex items-center justify-between gap-3 text-[11px] font-semibold uppercase tracking-[0.18em]">
                                    <span>{isUser ? "User" : "AI"}</span>
                                    <span className="text-slate-400">{formatTime(message.createdAt)}</span>
                                  </div>
                                  <p className="text-sm leading-6 whitespace-pre-wrap">{message.originalText}</p>
                                </article>
                              );
                            })}
                          </div>

                          <form
                            className="rounded-[1.75rem] border border-white/10 bg-white/6 p-4"
                            onSubmit={(event) => void handleContinueChat(event)}
                          >
                            <div className="mb-3">
                              <p className="font-semibold text-white">Continue chat</p>
                              <p className="text-sm text-slate-400">
                                Calls POST /api/chats/{selectedChat.id}/continue
                              </p>
                            </div>

                            <textarea
                              value={continueText}
                              onChange={(event) => setContinueText(event.target.value)}
                              required
                              rows={4}
                              placeholder="Add the next user message..."
                              className="min-h-28 w-full rounded-[1.5rem] border border-white/10 bg-slate-950/28 px-4 py-3 text-sm text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-cyan-300/50"
                            />
                            <div className="mt-4">
                              <ActionButton
                                type="submit"
                                busy={isContinuingChat}
                                icon={<Send className="h-4 w-4" />}
                              >
                                Send to agent
                              </ActionButton>
                            </div>
                          </form>
                        </div>
                      ) : (
                        <EmptyState
                          title="Select a chat"
                          detail="Choose an item from the list or create a fresh conversation for the selected user."
                        />
                      )}
                    </section>
                  </div>
                </div>
              ) : (
                <EmptyState
                  title="No active user"
                  detail="Use the left panel to create a user or pick one from the saved workspace list."
                />
              )}
            </Panel>

            <div
              className={`rounded-[1.75rem] border px-5 py-4 shadow-[0_24px_60px_rgba(0,0,0,0.18)] ${
                notice.tone === "success"
                  ? "border-emerald-300/18 bg-emerald-300/10"
                  : notice.tone === "error"
                    ? "border-rose-300/18 bg-rose-300/10"
                    : "border-cyan-300/18 bg-cyan-300/10"
              }`}
            >
              <p className="font-['Space_Grotesk'] text-xl text-white">{notice.title}</p>
              <p className="mt-1 text-sm text-slate-200/90">{notice.detail}</p>
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}

type PanelProps = {
  title: string;
  subtitle: string;
  icon: React.ReactNode;
  children: React.ReactNode;
  actions?: React.ReactNode;
};

function Panel({ title, subtitle, icon, children, actions }: PanelProps) {
  return (
    <section className="rounded-[2rem] border border-white/10 bg-white/7 p-5 shadow-[0_24px_80px_rgba(1,6,19,0.45)] backdrop-blur-xl">
      <div className="mb-5 flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="mt-0.5 rounded-2xl border border-white/10 bg-white/9 p-2.5 text-cyan-100">
            {icon}
          </div>
          <div>
            <h2 className="font-['Space_Grotesk'] text-2xl text-white">{title}</h2>
            <p className="mt-1 text-sm text-slate-400">{subtitle}</p>
          </div>
        </div>
        {actions}
      </div>
      {children}
    </section>
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
      <span className="mb-2 block text-xs font-semibold tracking-[0.18em] text-slate-400 uppercase">
        {label}
      </span>
      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        required
        className="w-full rounded-2xl border border-white/10 bg-white/6 px-4 py-3 text-sm text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-cyan-300/45 focus:bg-white/10"
      />
    </label>
  );
}

type ActionButtonProps = {
  type: "button" | "submit";
  busy: boolean;
  icon: React.ReactNode;
  children: React.ReactNode;
};

function ActionButton({ type, busy, icon, children }: ActionButtonProps) {
  return (
    <button
      type={type}
      disabled={busy}
      className="inline-flex items-center gap-2 rounded-full bg-[linear-gradient(135deg,#f59e0b,#f97316)] px-4 py-2.5 text-sm font-semibold text-slate-950 shadow-[0_18px_36px_rgba(249,115,22,0.35)] transition hover:scale-[1.01] disabled:opacity-65"
    >
      {busy ? <RefreshCw className="h-4 w-4 animate-spin" /> : icon}
      {busy ? "Working..." : children}
    </button>
  );
}

type SecondaryButtonProps = {
  type: "button" | "submit";
  onClick?: () => void;
  children: React.ReactNode;
};

function SecondaryButton({ type, onClick, children }: SecondaryButtonProps) {
  return (
    <button
      type={type}
      onClick={onClick}
      className="inline-flex items-center justify-center rounded-full border border-white/15 bg-white/6 px-4 py-2.5 text-sm font-medium text-slate-100 transition hover:bg-white/10"
    >
      {children}
    </button>
  );
}

type StatCardProps = {
  icon: React.ReactNode;
  label: string;
  value: string;
  accent: string;
};

function StatCard({ icon, label, value, accent }: StatCardProps) {
  return (
    <div className={`rounded-[1.75rem] border border-white/10 bg-gradient-to-br ${accent} p-4`}>
      <div className="mb-4 inline-flex rounded-2xl border border-white/10 bg-white/10 p-2.5 text-white">
        {icon}
      </div>
      <p className="text-xs uppercase tracking-[0.18em] text-slate-300">{label}</p>
      <p className="mt-2 font-['Space_Grotesk'] text-4xl text-white">{value}</p>
    </div>
  );
}

type EmptyStateProps = {
  title: string;
  detail: string;
};

function EmptyState({ title, detail }: EmptyStateProps) {
  return (
    <div className="rounded-[1.75rem] border border-dashed border-white/14 bg-slate-950/22 px-5 py-8 text-center">
      <p className="font-semibold text-white">{title}</p>
      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-400">{detail}</p>
    </div>
  );
}

type MetaPillProps = {
  label: string;
  value: string;
};

function MetaPill({ label, value }: MetaPillProps) {
  return (
    <div className="rounded-2xl border border-white/8 bg-white/6 px-4 py-3">
      <p className="text-[11px] uppercase tracking-[0.18em] text-slate-400">{label}</p>
      <p className="mt-1 text-sm text-white">{value}</p>
    </div>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export default App;
