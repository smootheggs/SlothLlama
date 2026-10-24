export type NavigationTab = 'chat' | 'personas' | 'knowledge' | 'models' | 'settings';

export type CompatibilityStatus =
  | 'SUPPORTED'
  | 'UNSUPPORTED'
  | 'POSSIBLY_SUPPORTED'
  | 'MISSING_TOKENIZER'
  | 'UNSUPPORTED_QUANTIZATION'
  | 'INSUFFICIENT_STORAGE'
  | 'ESTIMATED_INSUFFICIENT_MEMORY'
  | 'INCOMPATIBLE_RUNTIME'
  | 'INVALID_MANIFEST'
  | 'LICENSE_NOT_ACCEPTED';

export type HardwareAccelerator = 'CPU' | 'GPU' | 'NPU';

export interface ModelCompatibilityReport {
  status: CompatibilityStatus;
  isCompatible: boolean;
  detailedReason: string;
  availableStorageBytes: number;
  requiredStorageBytes: number;
  availableMemoryBytes: number;
  estimatedMemoryBytes: number;
  recommendedAccelerators: HardwareAccelerator[];
  warnings: string[];
}

export type DownloadStatus =
  | 'NOT_INSTALLED'
  | 'QUEUED'
  | 'DOWNLOADING'
  | 'PAUSED'
  | 'VERIFYING_CHECKSUM'
  | 'INSTALLED'
  | 'FAILED'
  | 'CANCELED';

export interface LocalModel {
  modelId: string;
  displayName: string;
  version: string;
  architecture: 'gemma2' | 'gemma' | 'llama3' | 'phi3' | 'qwen2' | 'unknown';
  format: 'litertlm' | 'task' | 'tflite' | 'gguf' | 'safetensors';
  quantization: 'INT4' | 'INT8' | 'Q4_K_M' | 'FP16' | 'FP32';
  contextWindow: number;
  downloadSizeBytes: number;
  estimatedRamBytes: number;
  sha256Checksum: string;
  tokenizerIncluded: boolean;
  sourceUrl: string;
  licenseName: string;
  licenseSpdx: string;
  licenseUrl: string;
  requiresExplicitAcceptance: boolean;
  licenseAccepted: boolean;
  licenseAcceptedAt?: string;
  hardwareAcceleration: HardwareAccelerator[];
  recommendedStarter?: boolean;
  thermalWarning?: boolean;
  downloadStatus: DownloadStatus;
  downloadProgress: number; // 0 to 100
  bytesDownloaded: number;
  isIntegrityVerified: boolean;
  installedAt?: string;
  errorMessage?: string;
  adapterAttached?: string | null; // Optional experimental LoRA adapter
}

export type ContextWindowStrategy = 'SLIDING_WINDOW' | 'ROLLING_SUMMARY_WITH_RAG' | 'STRICT_LOCAL_ONLY';

export interface LanguageTutorConfig {
  targetLanguage: 'Japanese' | 'Spanish' | 'French' | 'German' | 'Mandarin';
  nativeLanguage: string;
  cefrLevel: 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2';
  correctionMode: 'IMMEDIATE' | 'AFTER_FINISH';
  transliterationEnabled: boolean;
  rolePlayScenario: string;
}

export interface MathTutorConfig {
  socraticStepMode: boolean;
  concealFinalAnswer: boolean;
  topicFocus: 'Arithmetic' | 'Algebra' | 'Calculus' | 'Linear Algebra' | 'Probability';
  currentHintLevel: number; // 0, 1, 2, 3
}

export interface Persona {
  id: string;
  name: string;
  description: string;
  category: 'general' | 'language_tutor' | 'math_tutor' | 'writing_coach' | 'study_coach' | 'specialist';
  accentColor: string;
  selectedModelId: string;
  systemInstruction: string;
  temperature: number;
  maxOutputTokens: number;
  contextWindowStrategy: ContextWindowStrategy;
  enabledCollectionIds: string[];
  enabledTools: ('DETERMINISTIC_CALCULATOR' | 'SYMBOLIC_MATH_INTERFACE')[];
  memoryEnabled: boolean;
  isBuiltIn: boolean;
  isArchived: boolean;
  languageConfig?: LanguageTutorConfig;
  mathConfig?: MathTutorConfig;
  createdAt: string;
}

export interface KnowledgeChunk {
  chunkId: string;
  sourceId: string;
  collectionId: string;
  heading: string;
  normalizedText: string;
  tokenCount: number;
  embedding: number[];
}

export interface KnowledgeSource {
  id: string;
  collectionId: string;
  title: string;
  author: string;
  sourceType: 'TEXT' | 'MARKDOWN' | 'PDF' | 'EPUB' | 'HTML' | 'URL' | 'WIKIPEDIA_DUMP';
  sourceUrl?: string;
  license: string;
  retrievedAt: string;
  language: string;
  sha256Checksum: string;
  indexStatus: 'INSPECTING' | 'INDEXING' | 'PAUSED' | 'READY' | 'FAILED';
  extractedText: string;
  sizeBytes: number;
  chunkIds: string[];
}

export interface KnowledgeCollection {
  id: string;
  name: string;
  description: string;
  license: string;
  createdAt: string;
  updatedAt: string;
}

export interface Citation {
  id: string;
  chunkId: string;
  sourceId: string;
  sourceTitle: string;
  sectionHeading: string;
  collectionName: string;
  originalUrl?: string;
  retrievalDate: string;
  supportingExcerpt: string;
  similarityScore: number;
  whyUsedExplanation: string;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  role: 'user' | 'assistant';
  content: string;
  modelIdUsed: string;
  personaIdUsed: string;
  isCloudResponse: boolean;
  cloudProviderName?: string;
  promptTokens: number;
  completionTokens: number;
  generationSpeedTps: number;
  citations: Citation[];
  calculatorVerification?: {
    expression: string;
    result: string;
  };
  injectionNeutralizedCount?: number;
  concealedFinalAnswer?: string;
  extractedVocab?: {
    term: string;
    transliteration?: string;
    translation: string;
  }[];
  createdAt: string;
}

export interface Conversation {
  id: string;
  title: string;
  personaId: string;
  modelId: string;
  rollingSummary: string;
  parentConversationId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserMemory {
  id: string;
  factText: string;
  scope: 'GLOBAL' | 'PERSONA';
  personaId?: string;
  isApprovedByUser: boolean;
  createdAt: string;
}

export interface VocabularyItem {
  id: string;
  targetLanguage: string;
  term: string;
  transliteration: string;
  translation: string;
  exampleSentence: string;
  cefrLevel: string;
  intervalDays: number;
  easeFactor: number;
  dueLabel: string;
}

export interface SourcePolicyDomain {
  domain: string;
  licenseType: string;
  rateLimitPerMinute: number;
  robotsVerified: boolean;
  enabled: boolean;
}

export type CloudRoutingPolicy =
  | 'NEVER_USE_CLOUD'
  | 'ASK_EVERY_TIME'
  | 'ALLOW_ONLY_SELECTED_PERSONAS'
  | 'MANUAL_CLOUD_SELECTION';

export interface AppEventLog {
  id: string;
  category: 'SECURITY' | 'DOWNLOAD' | 'INDEXING' | 'INFERENCE' | 'POLICY';
  severity: 'INFO' | 'WARN' | 'ERROR';
  message: string;
  technicalDetails?: string;
  timestamp: string;
}

export interface AndroidProjectFile {
  path: string;
  category: 'Gradle & Manifest' | 'Domain & Inference' | 'Room & RAG Data' | 'Compose UI & Tests' | 'Docs & Security';
  language: 'kotlin' | 'toml' | 'xml' | 'json' | 'markdown' | 'proguard';
  description: string;
  content: string;
}
