import { create } from "zustand";
import { z } from "zod";

const SESSION_KEY = "gtu-ai-assistant.session";

const sessionSchema = z.object({
  email: z.string(),
  jwt: z.string()
});

export type SessionState = z.infer<typeof sessionSchema>;

interface AuthState {
  session: SessionState | null;
  authExpired: boolean;
  setSession: (session: SessionState) => void;
  expireSession: () => void;
  acknowledgeSessionExpired: () => void;
  logout: () => void;
}

function loadSession(): SessionState | null {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) return null;

  try {
    const parsed = sessionSchema.safeParse(JSON.parse(raw));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  session: loadSession(),
  authExpired: false,
  setSession: (session) => {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    set({ session, authExpired: false });
  },
  expireSession: () => {
    localStorage.removeItem(SESSION_KEY);
    set({ session: null, authExpired: true });
  },
  acknowledgeSessionExpired: () => {
    set({ authExpired: false });
  },
  logout: () => {
    localStorage.removeItem(SESSION_KEY);
    set({ session: null, authExpired: false });
  }
}));
