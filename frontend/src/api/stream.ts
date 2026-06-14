import { useAuthStore } from "../state/authStore";
import { ApiClientError, parseAndHandleApiError } from "./client";
import type { ChatResponse, CreateChatWithAgentRequest } from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

interface StreamCallbacks {
  onToken: (token: string) => void;
  onStatus?: (status: StreamStatus) => void;
  onDone: (chat: ChatResponse) => void;
}

export interface StreamStatus {
  phase: string;
  message: string;
}

export async function streamChat(
  path: string,
  payload: CreateChatWithAgentRequest,
  callbacks: StreamCallbacks,
  signal?: AbortSignal
): Promise<void> {
  const token = useAuthStore.getState().session?.jwt;
  const headers = new Headers({ "Content-Type": "application/json" });
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
    signal
  });

  if (!response.ok) {
    throw await parseAndHandleApiError(response, "stream_error");
  }

  if (!response.body) {
    throw new ApiClientError("Response body is not readable", "stream_error", response.status);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() ?? "";

      for (const line of lines) {
        processLine(line, callbacks);
      }
    }

    buffer += decoder.decode();
    if (buffer.trim()) processLine(buffer, callbacks);
  } finally {
    reader.releaseLock();
  }
}

function processLine(line: string, callbacks: StreamCallbacks): void {
  const trimmed = line.trim();
  if (!trimmed) return;

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch (error) {
    throw new ApiClientError(`Stream parse error: ${String(error)}`, "parse_error", 500);
  }

  if (!parsed || typeof parsed !== "object") return;
  const packet = parsed as { t?: unknown; d?: unknown; e?: unknown; h?: unknown; s?: unknown };

  if (packet.h === true) return;

  if (packet.s && typeof packet.s === "object") {
    const status = packet.s as { phase?: unknown; message?: unknown };
    if (typeof status.phase === "string" && typeof status.message === "string") {
      callbacks.onStatus?.({ phase: status.phase, message: status.message });
    }
    return;
  }

  if (typeof packet.t === "string") {
    callbacks.onToken(packet.t);
    return;
  }

  if (packet.d) {
    callbacks.onDone(packet.d as ChatResponse);
    return;
  }

  if (typeof packet.e === "string") {
    throw new ApiClientError(packet.e, "stream_error", 500);
  }
}
