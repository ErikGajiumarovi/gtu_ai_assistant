export type HealthResponse = {
  status: string;
};

export type ApiErrorResponse = {
  code: string;
  message: string;
};

export type CreateUserRequest = {
  id: string;
  name: string;
  lastName: string;
  email: string;
};

export type UserResponse = {
  id: string;
  version: number;
  name: string;
  lastName: string;
  email: string;
};

export type CreateChatWithAgentRequest = {
  userId: string;
  originalText: string;
};

export type ContinueChatWithAgentRequest = {
  userId: string;
  originalText: string;
};

export type MessageResponse = {
  id: string;
  originalText: string;
  senderType: "USER" | "AI";
  createdAt: string;
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

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers ?? {}),
    },
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

export function createUser(payload: CreateUserRequest): Promise<UserResponse> {
  return request<UserResponse>("/api/users", {
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

export async function listChats(userId: string): Promise<ChatResponse[]> {
  const result = await request<ListChatsResponse>(`/api/users/${userId}/chats`, {
    method: "GET",
  });

  return result.chats;
}

export function deleteChat(userId: string, chatId: string): Promise<DeleteChatResponse> {
  return request<DeleteChatResponse>(`/api/users/${userId}/chats/${chatId}`, {
    method: "DELETE",
  });
}
