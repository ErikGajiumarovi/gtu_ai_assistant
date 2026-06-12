import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Download,
  FileText,
  Loader2,
  LogOut,
  Menu,
  Plus,
  RefreshCw,
  Send,
  Square,
  Trash2,
  Upload,
  X
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useForm, type UseFormRegisterReturn } from "react-hook-form";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  ApiClientError,
  createMaterialCollection,
  deleteMaterial,
  deleteMaterialCollection,
  listChats,
  listMaterialCollections,
  listMaterials,
  login,
  openAuthenticatedDownload,
  registerUser,
  streamPaths,
  uploadMaterial
} from "./api/client";
import { streamChat } from "./api/stream";
import type {
  AgentSources,
  ArtifactResponse,
  ChatResponse,
  CitationResponse,
  LoginInRequest,
  MaterialCollectionResponse,
  MaterialResponse,
  RegisterUserRequest,
  UserResponse
} from "./api/types";
import { useAuthStore, type SessionState } from "./state/authStore";

type Notice = {
  tone: "success" | "error" | "info";
  title: string;
  detail: string;
};

const defaultSources: AgentSources = { gtu: true, materials: true, web: false };

export function App() {
  const queryClient = useQueryClient();
  const { session, setSession, logout } = useAuthStore();
  const [notice, setNotice] = useState<Notice | null>(null);
  const [authMode, setAuthMode] = useState<"login" | "register">("login");
  const [registeredUser, setRegisteredUser] = useState<UserResponse | null>(null);
  const [selectedChatId, setSelectedChatId] = useState("");
  const [chatSearch, setChatSearch] = useState("");
  const [selectedSources, setSelectedSources] = useState<AgentSources>(defaultSources);
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<Set<string>>(new Set());
  const [selectedCollectionIds, setSelectedCollectionIds] = useState<Set<string>>(new Set());
  const [collectionName, setCollectionName] = useState("");
  const [uploadCollectionId, setUploadCollectionId] = useState<string | null>(null);
  const [pendingUserText, setPendingUserText] = useState("");
  const [streamingText, setStreamingText] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const streamAbortRef = useRef<AbortController | null>(null);

  const chatsQuery = useQuery({
    queryKey: ["chats", session?.jwt],
    queryFn: async () => sortChats(await listChats()),
    enabled: Boolean(session)
  });

  const materialsQuery = useQuery({
    queryKey: ["materials", session?.jwt],
    queryFn: async () => (await listMaterials()).sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    enabled: Boolean(session)
  });

  const collectionsQuery = useQuery({
    queryKey: ["material-collections", session?.jwt],
    queryFn: async () => (await listMaterialCollections()).sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    enabled: Boolean(session)
  });

  const createCollectionMutation = useMutation({
    mutationFn: createMaterialCollection,
    onSuccess: (collection) => {
      setCollectionName("");
      setUploadCollectionId(collection.id);
      void queryClient.invalidateQueries({ queryKey: ["material-collections"] });
    },
    onError: (error) => showError("Create collection failed", error, setNotice)
  });

  const deleteCollectionMutation = useMutation({
    mutationFn: deleteMaterialCollection,
    onSuccess: (_result, collectionId) => {
      setSelectedCollectionIds((prev) => withoutValue(prev, collectionId));
      setUploadCollectionId((current) => (current === collectionId ? null : current));
      void queryClient.invalidateQueries({ queryKey: ["material-collections"] });
      void queryClient.invalidateQueries({ queryKey: ["materials"] });
    },
    onError: (error) => showError("Delete collection failed", error, setNotice)
  });

  const uploadMaterialMutation = useMutation({
    mutationFn: (file: File) => uploadMaterial(file, uploadCollectionId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["materials"] }),
    onError: (error) => showError("Upload failed", error, setNotice)
  });

  const deleteMaterialMutation = useMutation({
    mutationFn: deleteMaterial,
    onSuccess: (_result, materialId) => {
      setSelectedDocumentIds((prev) => withoutValue(prev, materialId));
      void queryClient.invalidateQueries({ queryKey: ["materials"] });
    },
    onError: (error) => showError("Delete failed", error, setNotice)
  });

  useEffect(() => {
    if (!session) {
      setSelectedChatId("");
      setPendingUserText("");
      setStreamingText("");
      setSelectedDocumentIds(new Set());
      setSelectedCollectionIds(new Set());
      setUploadCollectionId(null);
      setSelectedSources(defaultSources);
      queryClient.clear();
    }
  }, [queryClient, session]);

  const chats = chatsQuery.data ?? [];
  const materials = materialsQuery.data ?? [];
  const collections = collectionsQuery.data ?? [];
  const selectedChat = chats.find((chat) => chat.id === selectedChatId) ?? null;
  const filteredChats = filterChats(chats, chatSearch);

  async function handleSubmit(text: string) {
    const trimmed = text.trim();
    if (!trimmed || isStreaming || !hasAnySource(selectedSources)) return;

    const abortController = new AbortController();
    streamAbortRef.current = abortController;
    setPendingUserText(trimmed);
    setStreamingText("");
    setIsStreaming(true);
    setNotice(null);

    try {
      const payload = {
        originalText: trimmed,
        sources: selectedSources,
        collectionIds: [...selectedCollectionIds],
        documentIds: [...selectedDocumentIds]
      };

      await streamChat(
        selectedChat ? streamPaths.continue(selectedChat.id) : streamPaths.create,
        payload,
        {
          onToken: (token) => setStreamingText((current) => current + token),
          onDone: (chat) => {
            queryClient.setQueryData<ChatResponse[]>(["chats", session?.jwt], (current = []) =>
              sortChats([chat, ...current.filter((item) => item.id !== chat.id)])
            );
            setSelectedChatId(chat.id);
          }
        },
        abortController.signal
      );

      setPendingUserText("");
      setStreamingText("");
    } catch (error) {
      if (!abortController.signal.aborted) {
        showError("Response failed", error, setNotice);
      }
    } finally {
      if (streamAbortRef.current === abortController) streamAbortRef.current = null;
      setIsStreaming(false);
    }
  }

  function handleLogout() {
    streamAbortRef.current?.abort();
    logout();
  }

  if (!session) {
    return (
      <AuthPage
        authMode={authMode}
        notice={notice}
        registeredUser={registeredUser}
        onAuthModeChange={setAuthMode}
        onNotice={setNotice}
        onRegisteredUser={setRegisteredUser}
        onSession={setSession}
      />
    );
  }

  return (
    <div className="shell">
      <button className="mobile-menu-button" type="button" onClick={() => setSidebarOpen(true)} aria-label="Open sidebar">
        <Menu size={20} />
      </button>
      {sidebarOpen && <button className="sidebar-backdrop" type="button" onClick={() => setSidebarOpen(false)} aria-label="Close sidebar" />}
      <Sidebar
        open={sidebarOpen}
        session={session}
        chats={filteredChats}
        selectedChatId={selectedChatId}
        chatSearch={chatSearch}
        materials={materials}
        collections={collections}
        selectedDocumentIds={selectedDocumentIds}
        selectedCollectionIds={selectedCollectionIds}
        collectionName={collectionName}
        uploadCollectionId={uploadCollectionId}
        isStreaming={isStreaming}
        isRefreshingChats={chatsQuery.isFetching}
        isLoadingMaterials={materialsQuery.isFetching}
        isUploadingMaterial={uploadMaterialMutation.isPending}
        isLoadingCollections={collectionsQuery.isFetching}
        isSavingCollection={createCollectionMutation.isPending}
        onClose={() => setSidebarOpen(false)}
        onSelectChat={(chatId) => {
          setSelectedChatId(chatId);
          setPendingUserText("");
          setStreamingText("");
          setSidebarOpen(false);
        }}
        onNewChat={() => {
          setSelectedChatId("");
          setPendingUserText("");
          setStreamingText("");
          setChatSearch("");
          setNotice(null);
          setSidebarOpen(false);
        }}
        onLogout={handleLogout}
        onSearchChange={setChatSearch}
        onRefreshChats={() => void chatsQuery.refetch()}
        onRefreshMaterials={() => void materialsQuery.refetch()}
        onCollectionNameChange={setCollectionName}
        onUploadCollectionChange={setUploadCollectionId}
        onCreateCollection={() => {
          const name = collectionName.trim();
          if (name) createCollectionMutation.mutate(name);
        }}
        onDeleteCollection={(collection) => deleteCollectionMutation.mutate(collection.id)}
        onUploadMaterial={(file) => uploadMaterialMutation.mutate(file)}
        onDownloadMaterial={(material) =>
          void openAuthenticatedDownload(`/api/materials/${material.id}/download`).catch((error) =>
            showError("Download failed", error, setNotice)
          )
        }
        onDeleteMaterial={(material) => deleteMaterialMutation.mutate(material.id)}
        onToggleMaterial={(materialId, checked) => setSelectedDocumentIds((prev) => toggleValue(prev, materialId, checked))}
        onToggleCollection={(collectionId, checked) => setSelectedCollectionIds((prev) => toggleValue(prev, collectionId, checked))}
      />
      <ChatScreen
        notice={notice}
        selectedChat={selectedChat}
        isStreaming={isStreaming}
        streamingText={streamingText}
        pendingUserText={pendingUserText}
        selectedSources={selectedSources}
        selectedDocumentCount={selectedDocumentIds.size}
        selectedCollectionCount={selectedCollectionIds.size}
        onDismissNotice={() => setNotice(null)}
        onSourcesChange={setSelectedSources}
        onSubmit={(text) => void handleSubmit(text)}
        onStop={() => streamAbortRef.current?.abort()}
        onOpenMaterialCitation={(url) => {
          if (isAuthenticatedApiUrl(url)) {
            void openAuthenticatedDownload(url).catch((error) => showError("Download failed", error, setNotice));
            return;
          }
          if (url.startsWith("http://") || url.startsWith("https://")) {
            window.open(url, "_blank", "noopener,noreferrer");
            return;
          }
          void openAuthenticatedDownload(url).catch((error) => showError("Download failed", error, setNotice));
        }}
      />
    </div>
  );
}

function AuthPage({
  authMode,
  notice,
  registeredUser,
  onAuthModeChange,
  onNotice,
  onRegisteredUser,
  onSession
}: {
  authMode: "login" | "register";
  notice: Notice | null;
  registeredUser: UserResponse | null;
  onAuthModeChange: (mode: "login" | "register") => void;
  onNotice: (notice: Notice | null) => void;
  onRegisteredUser: (user: UserResponse | null) => void;
  onSession: (session: SessionState) => void;
}) {
  const loginForm = useForm<LoginInRequest>({ defaultValues: { email: registeredUser?.email ?? "", password: "" } });
  const registerForm = useForm<RegisterUserRequest>({ defaultValues: { name: "", lastName: "", email: "", password: "" } });
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [isRegistering, setIsRegistering] = useState(false);

  useEffect(() => {
    if (registeredUser) loginForm.setValue("email", registeredUser.email);
  }, [loginForm, registeredUser]);

  async function handleLogin(payload: LoginInRequest) {
    setIsLoggingIn(true);
    try {
      const response = await login(payload);
      onSession({ email: payload.email.trim().toLowerCase(), jwt: response.jwt });
    } catch (error) {
      showError("Login failed", error, onNotice);
    } finally {
      setIsLoggingIn(false);
    }
  }

  async function handleRegister(payload: RegisterUserRequest) {
    setIsRegistering(true);
    try {
      const user = await registerUser(payload);
      onRegisteredUser(user);
      onAuthModeChange("login");
      loginForm.reset({ email: user.email, password: payload.password });
      registerForm.reset();
      onNotice({ tone: "success", title: "Account created", detail: `${user.name} ${user.lastName} can now sign in.` });
    } catch (error) {
      showError("Registration failed", error, onNotice);
    } finally {
      setIsRegistering(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card-wrap">
        <div className="brand-block">
          <div className="brand-mark">GTU</div>
          <h1>GTU Assistant</h1>
          <p>Sign in to ask questions, search GTU sources and use your own materials.</p>
        </div>
        <div className="auth-card">
          <div className="auth-tabs">
            <button className={authMode === "login" ? "active" : ""} type="button" onClick={() => onAuthModeChange("login")}>
              Sign In
            </button>
            <button className={authMode === "register" ? "active" : ""} type="button" onClick={() => onAuthModeChange("register")}>
              Sign Up
            </button>
          </div>
          {notice && <NoticeBanner notice={notice} onDismiss={() => onNotice(null)} />}
          {authMode === "login" ? (
            <form className="form-stack" onSubmit={loginForm.handleSubmit(handleLogin)}>
              <Field label="Email" type="email" registration={loginForm.register("email", { required: true })} />
              <Field label="Password" type="password" registration={loginForm.register("password", { required: true })} />
              <button className="primary-button" type="submit" disabled={isLoggingIn}>
                {isLoggingIn ? "Signing in..." : "Sign in"}
              </button>
            </form>
          ) : (
            <form className="form-stack" onSubmit={registerForm.handleSubmit(handleRegister)}>
              <Field label="First name" registration={registerForm.register("name", { required: true })} />
              <Field label="Last name" registration={registerForm.register("lastName", { required: true })} />
              <Field label="Email" type="email" registration={registerForm.register("email", { required: true })} />
              <Field label="Password" type="password" registration={registerForm.register("password", { required: true })} />
              <button className="primary-button" type="submit" disabled={isRegistering}>
                {isRegistering ? "Creating account..." : "Create account"}
              </button>
            </form>
          )}
        </div>
      </section>
    </main>
  );
}

function Sidebar({
  open,
  session,
  chats,
  selectedChatId,
  chatSearch,
  materials,
  collections,
  selectedDocumentIds,
  selectedCollectionIds,
  collectionName,
  uploadCollectionId,
  isStreaming,
  isRefreshingChats,
  isLoadingMaterials,
  isUploadingMaterial,
  isLoadingCollections,
  isSavingCollection,
  onClose,
  onSelectChat,
  onNewChat,
  onLogout,
  onSearchChange,
  onRefreshChats,
  onRefreshMaterials,
  onCollectionNameChange,
  onUploadCollectionChange,
  onCreateCollection,
  onDeleteCollection,
  onUploadMaterial,
  onDownloadMaterial,
  onDeleteMaterial,
  onToggleMaterial,
  onToggleCollection
}: {
  open: boolean;
  session: SessionState;
  chats: ChatResponse[];
  selectedChatId: string;
  chatSearch: string;
  materials: MaterialResponse[];
  collections: MaterialCollectionResponse[];
  selectedDocumentIds: Set<string>;
  selectedCollectionIds: Set<string>;
  collectionName: string;
  uploadCollectionId: string | null;
  isStreaming: boolean;
  isRefreshingChats: boolean;
  isLoadingMaterials: boolean;
  isUploadingMaterial: boolean;
  isLoadingCollections: boolean;
  isSavingCollection: boolean;
  onClose: () => void;
  onSelectChat: (chatId: string) => void;
  onNewChat: () => void;
  onLogout: () => void;
  onSearchChange: (value: string) => void;
  onRefreshChats: () => void;
  onRefreshMaterials: () => void;
  onCollectionNameChange: (value: string) => void;
  onUploadCollectionChange: (value: string | null) => void;
  onCreateCollection: () => void;
  onDeleteCollection: (collection: MaterialCollectionResponse) => void;
  onUploadMaterial: (file: File) => void;
  onDownloadMaterial: (material: MaterialResponse) => void;
  onDeleteMaterial: (material: MaterialResponse) => void;
  onToggleMaterial: (materialId: string, checked: boolean) => void;
  onToggleCollection: (collectionId: string, checked: boolean) => void;
}) {
  return (
    <aside className={`sidebar ${open ? "open" : ""}`}>
      <div className="sidebar-header">
        <button className="new-chat-button" type="button" onClick={onNewChat}>
          <Plus size={16} /> New chat
        </button>
        <button className="sidebar-close" type="button" onClick={onClose} aria-label="Close sidebar">
          <X size={18} />
        </button>
      </div>
      <input
        className="sidebar-search"
        value={chatSearch}
        onChange={(event) => onSearchChange(event.target.value)}
        placeholder="Search conversations"
      />
      <div className="sidebar-section-title">
        <span>Conversations</span>
        <button className="ghost-icon-button" type="button" onClick={onRefreshChats} disabled={isRefreshingChats} aria-label="Refresh chats">
          <RefreshCw size={14} className={isRefreshingChats ? "spin" : ""} />
        </button>
      </div>
      <div className="chat-list">
        {chats.length === 0 ? (
          <p className="sidebar-empty">No conversations yet</p>
        ) : (
          chats.map((chat) => (
            <button
              key={chat.id}
              className={`chat-list-item ${chat.id === selectedChatId ? "selected" : ""}`}
              type="button"
              disabled={isStreaming}
              onClick={() => onSelectChat(chat.id)}
            >
              <span>{getChatTitle(chat)}</span>
              <small>{getChatPreview(chat)}</small>
            </button>
          ))
        )}
      </div>
      <MaterialsPanel
        materials={materials}
        collections={collections}
        selectedDocumentIds={selectedDocumentIds}
        selectedCollectionIds={selectedCollectionIds}
        collectionName={collectionName}
        uploadCollectionId={uploadCollectionId}
        isLoadingMaterials={isLoadingMaterials}
        isUploadingMaterial={isUploadingMaterial}
        isLoadingCollections={isLoadingCollections}
        isSavingCollection={isSavingCollection}
        onRefreshMaterials={onRefreshMaterials}
        onCollectionNameChange={onCollectionNameChange}
        onUploadCollectionChange={onUploadCollectionChange}
        onCreateCollection={onCreateCollection}
        onDeleteCollection={onDeleteCollection}
        onUploadMaterial={onUploadMaterial}
        onDownloadMaterial={onDownloadMaterial}
        onDeleteMaterial={onDeleteMaterial}
        onToggleMaterial={onToggleMaterial}
        onToggleCollection={onToggleCollection}
      />
      <div className="account-block">
        <div className="account-row">
          <div className="avatar">{session.email.slice(0, 2).toUpperCase()}</div>
          <span>{session.email}</span>
        </div>
        <button className="logout-button" type="button" onClick={onLogout}>
          <LogOut size={14} /> Log out
        </button>
      </div>
    </aside>
  );
}

function MaterialsPanel({
  materials,
  collections,
  selectedDocumentIds,
  selectedCollectionIds,
  collectionName,
  uploadCollectionId,
  isLoadingMaterials,
  isUploadingMaterial,
  isLoadingCollections,
  isSavingCollection,
  onRefreshMaterials,
  onCollectionNameChange,
  onUploadCollectionChange,
  onCreateCollection,
  onDeleteCollection,
  onUploadMaterial,
  onDownloadMaterial,
  onDeleteMaterial,
  onToggleMaterial,
  onToggleCollection
}: {
  materials: MaterialResponse[];
  collections: MaterialCollectionResponse[];
  selectedDocumentIds: Set<string>;
  selectedCollectionIds: Set<string>;
  collectionName: string;
  uploadCollectionId: string | null;
  isLoadingMaterials: boolean;
  isUploadingMaterial: boolean;
  isLoadingCollections: boolean;
  isSavingCollection: boolean;
  onRefreshMaterials: () => void;
  onCollectionNameChange: (value: string) => void;
  onUploadCollectionChange: (value: string | null) => void;
  onCreateCollection: () => void;
  onDeleteCollection: (collection: MaterialCollectionResponse) => void;
  onUploadMaterial: (file: File) => void;
  onDownloadMaterial: (material: MaterialResponse) => void;
  onDeleteMaterial: (material: MaterialResponse) => void;
  onToggleMaterial: (materialId: string, checked: boolean) => void;
  onToggleCollection: (collectionId: string, checked: boolean) => void;
}) {
  return (
    <section className="materials-panel">
      <div className="sidebar-section-title">
        <span>Materials</span>
        <button className="ghost-icon-button" type="button" onClick={onRefreshMaterials} disabled={isLoadingMaterials} aria-label="Refresh materials">
          <RefreshCw size={14} className={isLoadingMaterials ? "spin" : ""} />
        </button>
      </div>
      <div className="collection-create-row">
        <input value={collectionName} onChange={(event) => onCollectionNameChange(event.target.value)} placeholder="New collection" />
        <button type="button" disabled={isSavingCollection || !collectionName.trim()} onClick={onCreateCollection}>
          Add
        </button>
      </div>
      <select
        className="sidebar-select"
        value={uploadCollectionId ?? ""}
        disabled={isUploadingMaterial || isLoadingCollections}
        onChange={(event) => onUploadCollectionChange(event.target.value || null)}
      >
        <option value="">Upload without collection</option>
        {collections.map((collection) => (
          <option key={collection.id} value={collection.id}>
            Upload to {collection.name}
          </option>
        ))}
      </select>
      {collections.length > 0 && (
        <div className="collection-list">
          {collections.map((collection) => (
            <label className="collection-row" key={collection.id}>
              <input
                type="checkbox"
                checked={selectedCollectionIds.has(collection.id)}
                onChange={(event) => onToggleCollection(collection.id, event.target.checked)}
              />
              <span>{collection.name}</span>
              <button type="button" onClick={() => onDeleteCollection(collection)} aria-label={`Delete ${collection.name}`}>
                <X size={12} />
              </button>
            </label>
          ))}
        </div>
      )}
      <label className={`upload-target ${isUploadingMaterial ? "disabled" : ""}`}>
        <Upload size={15} /> {isUploadingMaterial ? "Uploading..." : "Upload .md, .txt, .pdf, .docx"}
        <input
          type="file"
          accept=".md,.txt,.pdf,.docx"
          disabled={isUploadingMaterial}
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) onUploadMaterial(file);
            event.target.value = "";
          }}
        />
      </label>
      <div className="materials-list">
        {materials.length === 0 ? (
          <p className="sidebar-empty">{isLoadingMaterials ? "Loading materials..." : "No files uploaded"}</p>
        ) : (
          materials.map((material) => (
            <MaterialRow
              key={material.id}
              material={material}
              selected={selectedDocumentIds.has(material.id)}
              onToggle={onToggleMaterial}
              onDownload={onDownloadMaterial}
              onDelete={onDeleteMaterial}
            />
          ))
        )}
      </div>
    </section>
  );
}

function MaterialRow({
  material,
  selected,
  onToggle,
  onDownload,
  onDelete
}: {
  material: MaterialResponse;
  selected: boolean;
  onToggle: (materialId: string, checked: boolean) => void;
  onDownload: (material: MaterialResponse) => void;
  onDelete: (material: MaterialResponse) => void;
}) {
  return (
    <article className="material-row">
      <label>
        <input type="checkbox" checked={selected} onChange={(event) => onToggle(material.id, event.target.checked)} />
        <div>
          <strong>{material.originalFileName || material.title}</strong>
          <small>
            {material.ingestionStatus} · {formatBytes(material.sizeBytes)} · {formatShortDate(material.createdAt)}
          </small>
        </div>
      </label>
      {material.ingestionError && <p className="material-error">{material.ingestionError}</p>}
      {material.ocrMetadata && <p className="material-meta">OCR: {material.ocrMetadata}</p>}
      <div className="material-actions">
        <button type="button" onClick={() => onDownload(material)}>
          <Download size={12} /> Download
        </button>
        <button type="button" onClick={() => onDelete(material)}>
          <Trash2 size={12} /> Delete
        </button>
      </div>
    </article>
  );
}

function ChatScreen({
  notice,
  selectedChat,
  isStreaming,
  streamingText,
  pendingUserText,
  selectedSources,
  selectedDocumentCount,
  selectedCollectionCount,
  onDismissNotice,
  onSourcesChange,
  onSubmit,
  onStop,
  onOpenMaterialCitation
}: {
  notice: Notice | null;
  selectedChat: ChatResponse | null;
  isStreaming: boolean;
  streamingText: string;
  pendingUserText: string;
  selectedSources: AgentSources;
  selectedDocumentCount: number;
  selectedCollectionCount: number;
  onDismissNotice: () => void;
  onSourcesChange: (sources: AgentSources) => void;
  onSubmit: (text: string) => void;
  onStop: () => void;
  onOpenMaterialCitation: (url: string) => void;
}) {
  const [composerText, setComposerText] = useState("");
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const existingMessages = selectedChat?.messages ?? [];
  const hasAnyContent = existingMessages.length > 0 || pendingUserText || isStreaming;
  const canSubmit = hasAnySource(selectedSources);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ block: "end" });
  }, [selectedChat?.id, streamingText, pendingUserText]);

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = "0px";
    textarea.style.height = `${Math.min(textarea.scrollHeight, 200)}px`;
    textarea.style.overflowY = textarea.scrollHeight > 200 ? "auto" : "hidden";
  }, [composerText]);

  return (
    <main className="chat-screen">
      <section className="messages-pane">
        <div className="messages-inner">
          {notice && notice.tone !== "info" && <NoticeBanner notice={notice} onDismiss={onDismissNotice} />}
          {!hasAnyContent && (
            <div className="empty-state">
              <div className="empty-icon">GTU</div>
              <h2>How can I help you?</h2>
              <p>Ask about Georgian Technical University or search through your uploaded materials.</p>
            </div>
          )}
          {existingMessages.map((message) => (
            <MessageBubble
              key={message.id}
              text={message.originalText}
              isUser={message.senderType === "USER"}
              time={formatMessageTime(message.createdAt)}
              citations={message.senderType === "USER" ? [] : message.citations}
              artifacts={message.senderType === "USER" ? [] : message.artifacts ?? []}
              onOpenMaterialCitation={onOpenMaterialCitation}
            />
          ))}
          {pendingUserText && <MessageBubble text={pendingUserText} isUser time="" />}
          {isStreaming && <MessageBubble text={streamingText} isUser={false} time="" isStreaming />}
          <div ref={scrollRef} />
        </div>
      </section>
      <section className="composer-wrap">
        <form
          className="composer"
          onSubmit={(event) => {
            event.preventDefault();
            if (canSubmit) {
              onSubmit(composerText);
              setComposerText("");
            }
          }}
        >
          <SourceModeSelector
            selectedSources={selectedSources}
            selectedDocumentCount={selectedDocumentCount}
            selectedCollectionCount={selectedCollectionCount}
            disabled={isStreaming}
            onSourcesChange={onSourcesChange}
          />
          <div className="composer-row">
            <textarea
              ref={textareaRef}
              rows={1}
              value={composerText}
              disabled={isStreaming}
              placeholder="Message GTU Assistant..."
              onChange={(event) => setComposerText(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  if (canSubmit) {
                    onSubmit(composerText);
                    setComposerText("");
                  }
                }
              }}
            />
            {isStreaming ? (
              <button className="stop-button" type="button" onClick={onStop} aria-label="Stop response">
                <Square size={16} />
              </button>
            ) : (
              <button className="send-button" type="submit" disabled={!composerText.trim() || !canSubmit} aria-label="Send message">
                <Send size={17} />
              </button>
            )}
          </div>
        </form>
      </section>
    </main>
  );
}

function MessageBubble({
  text,
  isUser,
  time,
  citations = [],
  artifacts = [],
  isStreaming = false,
  onOpenMaterialCitation = () => undefined
}: {
  text: string;
  isUser: boolean;
  time: string;
  citations?: CitationResponse[];
  artifacts?: ArtifactResponse[];
  isStreaming?: boolean;
  onOpenMaterialCitation?: (url: string) => void;
}) {
  return (
    <article className={`message-row ${isUser ? "user" : "assistant"}`}>
      <div className="message-bubble">
        {isUser ? (
          <p>
            {text || (isStreaming ? "" : " ")}
            {isStreaming && <span className="cursor">|</span>}
          </p>
        ) : (
          <div className="markdown-content">
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                a: ({ node: _node, href, ...props }) => {
                  const isAuthenticatedUrl = href ? isAuthenticatedApiUrl(href) : false;
                  return (
                    <a
                      {...props}
                      href={href}
                      target="_blank"
                      rel="noreferrer"
                      onClick={(event) => {
                        if (!href || !isAuthenticatedUrl) return;
                        event.preventDefault();
                        onOpenMaterialCitation(href);
                      }}
                    />
                  );
                }
              }}
            >
              {text || (isStreaming ? "" : " ")}
            </ReactMarkdown>
            {isStreaming && <span className="cursor">|</span>}
          </div>
        )}
        {!isUser && citations.length > 0 && (
          <div className="citations">
            {citations.map((citation, index) => (
              <button key={`${citation.url}-${index}`} type="button" title={citation.snippet || citation.title} onClick={() => onOpenMaterialCitation(citation.url)}>
                <FileText size={12} /> {citationLabel(citation)}
              </button>
            ))}
          </div>
        )}
        {!isUser && artifacts.length > 0 && <ArtifactList artifacts={artifacts} onOpenArtifact={onOpenMaterialCitation} />}
        {time && <small className="message-time">{time}</small>}
      </div>
    </article>
  );
}

function ArtifactList({ artifacts, onOpenArtifact }: { artifacts: ArtifactResponse[]; onOpenArtifact: (url: string) => void }) {
  return (
    <div className="artifacts">
      {artifacts.map((artifact) => (
        <div className="artifact-card" key={artifact.id}>
          <div>
            <strong>{artifact.fileName}</strong>
            <small>
              {artifact.contentType} · {formatBytes(artifact.sizeBytes)}
            </small>
          </div>
          <div className="artifact-actions">
            {artifact.viewUrl && (
              <button type="button" onClick={() => onOpenArtifact(artifact.viewUrl!)}>
                Open
              </button>
            )}
            <button type="button" onClick={() => onOpenArtifact(artifact.downloadUrl)}>
              Download
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

function isAuthenticatedApiUrl(url: string): boolean {
  try {
    const parsed = new URL(url, window.location.href);
    return parsed.origin === window.location.origin && (parsed.pathname.startsWith("/api/artifacts/") || parsed.pathname.startsWith("/api/materials/"));
  } catch {
    return url.startsWith("/api/artifacts/") || url.startsWith("/api/materials/");
  }
}

function SourceModeSelector({
  selectedSources,
  selectedDocumentCount,
  selectedCollectionCount,
  disabled,
  onSourcesChange
}: {
  selectedSources: AgentSources;
  selectedDocumentCount: number;
  selectedCollectionCount: number;
  disabled: boolean;
  onSourcesChange: (sources: AgentSources) => void;
}) {
  const filters = [
    selectedDocumentCount > 0 ? `${selectedDocumentCount} file(s)` : null,
    selectedCollectionCount > 0 ? `${selectedCollectionCount} collection(s)` : null
  ].filter(Boolean);
  const selectedLabels = [
    selectedSources.gtu ? "GTU" : null,
    selectedSources.materials ? "my materials" : null,
    selectedSources.web ? "web" : null
  ].filter(Boolean);

  function toggleSource(key: keyof AgentSources): void {
    onSourcesChange({ ...selectedSources, [key]: !selectedSources[key] });
  }

  return (
    <div className="source-mode-row">
      <span className="source-mode-title">Sources</span>
      <div className="source-toggles" role="group" aria-label="Sources">
        <button type="button" className={selectedSources.gtu ? "active" : ""} disabled={disabled} onClick={() => toggleSource("gtu")}>
          GTU
        </button>
        <button type="button" className={selectedSources.materials ? "active" : ""} disabled={disabled} onClick={() => toggleSource("materials")}>
          My materials
        </button>
        <button type="button" className={selectedSources.web ? "active" : ""} disabled={disabled} onClick={() => toggleSource("web")}>
          Web
        </button>
      </div>
      <span>{selectedLabels.length ? selectedLabels.join(" + ") : "Select at least one source"}</span>
      {selectedSources.materials && <span>{filters.length ? filters.join(" + ") : "All ready materials"}</span>}
      {selectedSources.materials && filters.length === 0 && <small>No files selected: assistant will search all READY materials.</small>}
    </div>
  );
}

function Field({
  label,
  type = "text",
  registration
}: {
  label: string;
  type?: "text" | "email" | "password";
  registration: UseFormRegisterReturn;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input type={type} required {...registration} />
    </label>
  );
}

function NoticeBanner({ notice, onDismiss }: { notice: Notice; onDismiss: () => void }) {
  return (
    <div className={`notice ${notice.tone}`}>
      <div>
        <strong>{notice.title}</strong>
        {notice.detail && <p>{notice.detail}</p>}
      </div>
      <button type="button" onClick={onDismiss} aria-label="Dismiss notice">
        <X size={14} />
      </button>
    </div>
  );
}

function showError(title: string, error: unknown, setNotice: (notice: Notice) => void): void {
  if (error instanceof ApiClientError) {
    setNotice({ tone: "error", title, detail: `${error.code}: ${stripHtml(error.message)}` });
    return;
  }

  if (error instanceof Error) {
    setNotice({ tone: "error", title, detail: error.message || "Unknown error" });
    return;
  }

  setNotice({ tone: "error", title, detail: "Unknown error" });
}

function sortChats(chats: ChatResponse[]): ChatResponse[] {
  return [...chats].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

function filterChats(chats: ChatResponse[], query: string): ChatResponse[] {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return chats;
  return chats.filter((chat) => `${getChatTitle(chat)} ${getChatPreview(chat)}`.toLowerCase().includes(normalized));
}

function getChatTitle(chat: ChatResponse): string {
  const firstUserMessage = chat.messages.find((message) => message.senderType === "USER");
  return firstUserMessage ? truncate(firstUserMessage.originalText.replace(/\s+/g, " ").trim(), 52) : "New conversation";
}

function getChatPreview(chat: ChatResponse): string {
  const lastMessage = chat.messages.at(-1);
  return lastMessage ? truncate(lastMessage.originalText.replace(/\s+/g, " ").trim(), 88) : "No messages yet";
}

function truncate(value: string, length: number): string {
  return value.length <= length ? value : `${value.slice(0, length - 1)}...`;
}

function formatMessageTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

function formatShortDate(value: string): string {
  return value.split("T")[0] || value;
}

function formatBytes(value: number): string {
  if (value >= 1024 * 1024) return `${roundOne(value / 1024 / 1024)} MB`;
  if (value >= 1024) return `${roundOne(value / 1024)} KB`;
  return `${value} B`;
}

function roundOne(value: number): string {
  return String(Math.round(value * 10) / 10);
}

function citationLabel(citation: CitationResponse): string {
  const prefix = citation.sourceType === "WEB" ? "Web" : citation.sourceType === "USER_MATERIAL" ? "File" : "GTU";
  const location = citation.pageStart && citation.pageEnd && citation.pageEnd !== citation.pageStart
    ? ` pp. ${citation.pageStart}-${citation.pageEnd}`
    : citation.pageStart
      ? ` p. ${citation.pageStart}`
      : "";
  return `${prefix}: ${citation.title || hostname(citation.url)}${location}`;
}

function hostname(url: string): string {
  try {
    return new URL(url).hostname;
  } catch {
    return url.replace(/^https?:\/\//, "").split("/")[0] || url;
  }
}

function hasAnySource(sources: AgentSources): boolean {
  return sources.gtu || sources.materials || sources.web;
}

function toggleValue(current: Set<string>, value: string, checked: boolean): Set<string> {
  const next = new Set(current);
  if (checked) next.add(value);
  else next.delete(value);
  return next;
}

function withoutValue(current: Set<string>, value: string): Set<string> {
  const next = new Set(current);
  next.delete(value);
  return next;
}

function stripHtml(value: string): string {
  return value.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
}
