export interface AgentSources {
  gtu: boolean;
  materials: boolean;
  web: boolean;
}

export interface RegisterUserRequest {
  name: string;
  lastName: string;
  email: string;
  password: string;
}

export interface LoginInRequest {
  email: string;
  password: string;
}

export interface LoginInResponse {
  jwt: string;
}

export interface CreateChatWithAgentRequest {
  originalText: string;
  sources: AgentSources;
  collectionIds: string[];
  documentIds: string[];
}

export interface ContinueChatWithAgentRequest extends CreateChatWithAgentRequest {}

export interface UserResponse {
  id: string;
  version: number;
  name: string;
  lastName: string;
  email: string;
}

export interface CitationResponse {
  title: string;
  url: string;
  snippet: string;
  sourceType: string;
  documentId?: string | null;
  pageStart?: number | null;
  pageEnd?: number | null;
}

export interface MessageResponse {
  id: string;
  originalText: string;
  senderType: string;
  createdAt: string;
  citations: CitationResponse[];
  artifacts: ArtifactResponse[];
}

export interface ArtifactResponse {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  downloadUrl: string;
  viewUrl?: string | null;
}

export interface ChatResponse {
  id: string;
  version: number;
  ownedBy: string;
  createdAt: string;
  updatedAt: string;
  messages: MessageResponse[];
}

export interface ListChatsResponse {
  chats: ChatResponse[];
}

export interface DeleteChatResponse {
  deleted: boolean;
}

export interface MaterialResponse {
  id: string;
  version: number;
  ownerUserId: string;
  collectionId?: string | null;
  title: string;
  originalFileName: string;
  contentType: string;
  sizeBytes: number;
  ingestionStatus: string;
  ingestionError?: string | null;
  ocrMetadata?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MaterialCollectionResponse {
  id: string;
  version: number;
  ownerUserId: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMaterialCollectionRequest {
  name: string;
}

export interface ListMaterialsResponse {
  materials: MaterialResponse[];
}

export interface ListMaterialCollectionsResponse {
  collections: MaterialCollectionResponse[];
}

export interface DeleteMaterialResponse {
  deleted: boolean;
}

export interface DeleteMaterialCollectionResponse {
  deleted: boolean;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
}
