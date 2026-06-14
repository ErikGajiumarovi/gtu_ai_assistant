import { useAuthStore } from "../state/authStore";
import type {
  ApiErrorResponse,
  ChatResponse,
  CreateChatWithAgentRequest,
  CreateMaterialCollectionRequest,
  DeleteChatResponse,
  DeleteMaterialCollectionResponse,
  DeleteMaterialResponse,
  ListChatsResponse,
  ListMaterialCollectionsResponse,
  ListMaterialsResponse,
  LoginInRequest,
  LoginInResponse,
  MaterialCollectionResponse,
  MaterialResponse,
  RegisterUserRequest,
  UserResponse
} from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiClientError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly status: number
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

function authHeader(): HeadersInit {
  const token = useAuthStore.getState().session?.jwt;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function apiUrl(pathOrUrl: string): string {
  return pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") ? pathOrUrl : `${API_BASE_URL}${pathOrUrl}`;
}

async function parseApiError(response: Response, defaultCode: string): Promise<ApiClientError> {
  const text = await response.text();
  try {
    const body = JSON.parse(text) as ApiErrorResponse;
    return new ApiClientError(body.message || text, body.code || defaultCode, response.status);
  } catch {
    return new ApiClientError(text || `HTTP ${response.status}`, defaultCode, response.status);
  }
}

export function isUnauthorizedApiError(error: unknown): error is ApiClientError {
  return error instanceof ApiClientError && error.status === 401 && error.code === "unauthorized";
}

export function handleUnauthorizedApiError(error: unknown): void {
  if (isUnauthorizedApiError(error)) {
    useAuthStore.getState().expireSession();
  }
}

export async function parseAndHandleApiError(response: Response, defaultCode: string): Promise<ApiClientError> {
  const error = await parseApiError(response, defaultCode);
  handleUnauthorizedApiError(error);
  return error;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  Object.entries(authHeader()).forEach(([key, value]) => headers.set(key, value));

  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  if (!response.ok) {
    throw await parseAndHandleApiError(response, "transport_error");
  }

  return (await response.json()) as T;
}

export function registerUser(payload: RegisterUserRequest): Promise<UserResponse> {
  return request("/api/auth/register", { method: "POST", body: JSON.stringify(payload) });
}

export function login(payload: LoginInRequest): Promise<LoginInResponse> {
  return request("/api/auth/login", { method: "POST", body: JSON.stringify(payload) });
}

export async function listChats(): Promise<ChatResponse[]> {
  const response = await request<ListChatsResponse>("/api/chats");
  return response.chats;
}

export function deleteChat(chatId: string): Promise<DeleteChatResponse> {
  return request(`/api/chats/${chatId}`, { method: "DELETE" });
}

export async function listMaterials(): Promise<MaterialResponse[]> {
  const response = await request<ListMaterialsResponse>("/api/materials");
  return response.materials;
}

export async function listMaterialCollections(): Promise<MaterialCollectionResponse[]> {
  const response = await request<ListMaterialCollectionsResponse>("/api/material-collections");
  return response.collections;
}

export function createMaterialCollection(name: string): Promise<MaterialCollectionResponse> {
  const payload: CreateMaterialCollectionRequest = { name };
  return request("/api/material-collections", { method: "POST", body: JSON.stringify(payload) });
}

export function deleteMaterialCollection(collectionId: string): Promise<DeleteMaterialCollectionResponse> {
  return request(`/api/material-collections/${collectionId}`, { method: "DELETE" });
}

export function deleteMaterial(materialId: string): Promise<DeleteMaterialResponse> {
  return request(`/api/materials/${materialId}`, { method: "DELETE" });
}

export function uploadMaterial(file: File, collectionId?: string | null): Promise<MaterialResponse> {
  const formData = new FormData();
  formData.append("file", file);
  if (collectionId) formData.append("collectionId", collectionId);

  return request("/api/materials", { method: "POST", body: formData });
}

export async function openAuthenticatedDownload(url: string): Promise<void> {
  const response = await fetch(apiUrl(url), { headers: authHeader() });
  if (!response.ok) {
    throw await parseAndHandleApiError(response, "download_error");
  }

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  window.open(objectUrl, "_blank", "noopener,noreferrer");
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
}

export async function downloadAuthenticatedFile(url: string, fileName?: string): Promise<void> {
  const response = await fetch(apiUrl(url), { headers: authHeader() });
  if (!response.ok) {
    throw await parseAndHandleApiError(response, "download_error");
  }

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = fileName?.trim() || fileNameFromContentDisposition(response.headers.get("Content-Disposition")) || "download";
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
}

export async function readAuthenticatedText(url: string): Promise<string> {
  const response = await fetch(apiUrl(url), { headers: authHeader() });
  if (!response.ok) {
    throw await parseAndHandleApiError(response, "read_error");
  }

  return response.text();
}

function fileNameFromContentDisposition(value: string | null): string | null {
  if (!value) return null;
  const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(value);
  if (utf8Match?.[1]) return decodeURIComponent(utf8Match[1].trim().replace(/^"|"$/g, ""));
  const asciiMatch = /filename="?([^";]+)"?/i.exec(value);
  return asciiMatch?.[1]?.trim() || null;
}

export const streamPaths = {
  create: "/api/chats/with-agent/stream",
  continue: (chatId: string) => `/api/chats/${chatId}/continue/stream`
};

export type ChatStreamPayload = CreateChatWithAgentRequest;
