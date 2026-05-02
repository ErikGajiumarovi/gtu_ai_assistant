export type HealthResponse = {
  status: string;
};

export type ApiErrorResponse = {
  code: string;
  message: string;
};

export type RegisterUserRequest = {
  name: string;
  lastName: string;
  email: string;
  password: string;
};

export type LoginInRequest = {
  email: string;
  password: string;
};

export type LoginInResponse = {
  jwt: string;
};

export type UserResponse = {
  id: string;
  version: number;
  name: string;
  lastName: string;
  email: string;
};

export type CreateChatWithAgentRequest = {
  originalText: string;
};

export type ContinueChatWithAgentRequest = {
  originalText: string;
};

export type CitationResponse = {
  title: string;
  url: string;
  snippet: string;
  sourceType: "RAG" | "WEB";
};

export type MessageResponse = {
  id: string;
  originalText: string;
  senderType: "USER" | "AI";
  createdAt: string;
  citations?: CitationResponse[];
};

export type ChatResponse = {
  id: string;
  version: number;
  ownedBy: string;
  createdAt: string;
  updatedAt: string;
  messages: MessageResponse[];
};

export type ListChatsResponse = {
  chats: ChatResponse[];
};

export type DeleteChatResponse = {
  deleted: boolean;
};

type RequestOptions = RequestInit & {
  parseAsText?: boolean;
};

export class ApiClientError extends Error {
  readonly code: string;
  readonly status: number;

  constructor(message: string, code: string, status: number) {
    super(message);
    this.name = "ApiClientError";
    this.code = code;
    this.status = status;
  }
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, "") ?? "";
let authToken: string | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
}

export function clearAuthToken() {
  authToken = null;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers ?? {});
  headers.set("Content-Type", "application/json");

  if (authToken) {
    headers.set("Authorization", `Bearer ${authToken}`);
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    const fallback = `HTTP ${response.status}`;

    try {
      const error = (await response.json()) as ApiErrorResponse;
      throw new ApiClientError(error.message, error.code, response.status);
    } catch (reason) {
      if (reason instanceof ApiClientError) {
        throw reason;
      }

      throw new ApiClientError(fallback, "transport_error", response.status);
    }
  }

  if (options.parseAsText) {
    return (await response.text()) as T;
  }

  return (await response.json()) as T;
}

export function checkHealth(): Promise<HealthResponse> {
  return request<HealthResponse>("/health", { method: "GET" });
}

export function registerUser(payload: RegisterUserRequest): Promise<UserResponse> {
  return request<UserResponse>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function loginIn(payload: LoginInRequest): Promise<LoginInResponse> {
  return request<LoginInResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function createChatWithAgent(payload: CreateChatWithAgentRequest): Promise<ChatResponse> {
  return request<ChatResponse>("/api/chats/with-agent", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function continueChatWithAgent(
  chatId: string,
  payload: ContinueChatWithAgentRequest,
): Promise<ChatResponse> {
  return request<ChatResponse>(`/api/chats/${chatId}/continue`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function listChats(): Promise<ChatResponse[]> {
  const result = await request<ListChatsResponse>("/api/chats", {
    method: "GET",
  });

  return result.chats;
}

export function deleteChat(chatId: string): Promise<DeleteChatResponse> {
  return request<DeleteChatResponse>(`/api/chats/${chatId}`, {
    method: "DELETE",
  });
}
