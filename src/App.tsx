import React, { useEffect, useState } from 'react';
import {
  BookOpen,
  Cpu,
  MessageSquare,
  Moon,
  Rocket,
  Settings,
  Sun,
  Users,
} from 'lucide-react';
import {
  AppTheme,
  ChatMessage,
  CloudRoutingPolicy,
  Conversation,
  KnowledgeChunk,
  KnowledgeCollection,
  KnowledgeSource,
  LocalModel,
  NavigationTab,
  Persona,
  SourcePolicyDomain,
  UserMemory,
  VocabularyItem,
} from './types/localmind';
import {
  INITIAL_ALLOWED_DOMAINS,
  INITIAL_CHUNKS,
  INITIAL_COLLECTIONS,
  INITIAL_CONVERSATIONS,
  INITIAL_MEMORIES,
  INITIAL_MESSAGES,
  INITIAL_MODELS,
  INITIAL_PERSONAS,
  INITIAL_SOURCES,
  INITIAL_VOCABULARY,
} from './data/seedData';
import { ChatWorkspace } from './components/ChatWorkspace';
import { PersonasView } from './components/PersonasView';
import { KnowledgeView } from './components/KnowledgeView';
import { ModelsView } from './components/ModelsView';
import { SettingsAndArchitectureView } from './components/SettingsAndArchitectureView';
import { OnboardingModal } from './components/OnboardingModal';
import { ModelStatusIndicator } from './components/ModelStatusIndicator';
import { AndroidLaunchModal } from './components/AndroidLaunchModal';

const STORAGE_KEY = 'slothllama_state_v1';
const LEGACY_STORAGE_KEY = 'localmind_state_v1';
const THEME_STORAGE_KEY = 'slothllama_theme';

export default function App() {
  const [activeTab, setActiveTab] = useState<NavigationTab>('chat');
  const [isOnboardingOpen, setIsOnboardingOpen] = useState<boolean>(false);
  const [isAndroidLaunchOpen, setIsAndroidLaunchOpen] = useState<boolean>(false);
  const [theme, setTheme] = useState<AppTheme>(() => {
    try {
      const saved = localStorage.getItem(THEME_STORAGE_KEY);
      if (saved === 'light' || saved === 'dark') return saved;
    } catch {}
    return 'dark';
  });

  // Sync theme with document classList
  useEffect(() => {
    if (theme === 'dark') {
      document.documentElement.classList.add('dark');
      document.documentElement.classList.remove('light');
    } else {
      document.documentElement.classList.remove('dark');
      document.documentElement.classList.add('light');
    }
    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {}
  }, [theme]);

  const handleToggleTheme = () => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  };

  // Core Persistent Application State
  const [models, setModels] = useState<LocalModel[]>(INITIAL_MODELS);
  const [loadedModelId, setLoadedModelId] = useState<string | null>(
    'gemma-2-2b-it-litert-int4'
  );
  const [personas, setPersonas] = useState<Persona[]>(INITIAL_PERSONAS);
  const [activePersonaId, setActivePersonaId] = useState<string>(
    'persona-general-assistant'
  );
  const [collections, setCollections] =
    useState<KnowledgeCollection[]>(INITIAL_COLLECTIONS);
  const [sources, setSources] = useState<KnowledgeSource[]>(INITIAL_SOURCES);
  const [chunks, setChunks] = useState<KnowledgeChunk[]>(INITIAL_CHUNKS);
  const [conversations, setConversations] =
    useState<Conversation[]>(INITIAL_CONVERSATIONS);
  const [activeConversationId, setActiveConversationId] =
    useState<string>('conv-starter-1');
  const [messages, setMessages] = useState<ChatMessage[]>(INITIAL_MESSAGES);
  const [memories, setMemories] = useState<UserMemory[]>(INITIAL_MEMORIES);
  const [vocabulary, setVocabulary] =
    useState<VocabularyItem[]>(INITIAL_VOCABULARY);
  const [allowedDomains, setAllowedDomains] = useState<SourcePolicyDomain[]>(
    INITIAL_ALLOWED_DOMAINS
  );

  // Privacy & Runtime Flags
  const [isOfflineMode, setIsOfflineMode] = useState<boolean>(false);
  const [networkAcquisitionEnabled, setNetworkAcquisitionEnabled] =
    useState<boolean>(true);
  const [cloudPolicy, setCloudPolicy] =
    useState<CloudRoutingPolicy>('NEVER_USE_CLOUD');
  const [batterySaverMode, setBatterySaverMode] = useState<boolean>(false);
  const [voiceFeatureFlag, setVoiceFeatureFlag] = useState<boolean>(false);

  // Load persisted state from localStorage
  useEffect(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY) || localStorage.getItem(LEGACY_STORAGE_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        if (parsed.models) setModels(parsed.models);
        if (parsed.personas) setPersonas(parsed.personas);
        if (parsed.collections) setCollections(parsed.collections);
        if (parsed.sources) setSources(parsed.sources);
        if (parsed.chunks) setChunks(parsed.chunks);
        if (parsed.conversations) setConversations(parsed.conversations);
        if (parsed.messages) setMessages(parsed.messages);
        if (parsed.memories) setMemories(parsed.memories);
        if (parsed.vocabulary) setVocabulary(parsed.vocabulary);
      }
    } catch {
      // Fallback to verified seed data if storage is unavailable
    }
  }, []);

  // Save state changes to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          models,
          personas,
          collections,
          sources,
          chunks,
          conversations,
          messages,
          memories,
          vocabulary,
        })
      );
    } catch {
      // Ignore quota errors
    }
  }, [
    models,
    personas,
    collections,
    sources,
    chunks,
    conversations,
    messages,
    memories,
    vocabulary,
  ]);

  const handleCreateConversation = (personaId?: string, modelId?: string) => {
    const targetPersonaId = personaId ?? activePersonaId;
    const targetModelId =
      modelId ?? loadedModelId ?? 'gemma-2-2b-it-litert-int4';
    const newConv: Conversation = {
      id: `conv-${Date.now()}`,
      title: 'New Offline Session',
      personaId: targetPersonaId,
      modelId: targetModelId,
      rollingSummary: '',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    setConversations((prev) => [newConv, ...prev]);
    setActiveConversationId(newConv.id);
    setActiveTab('chat');
  };

  const handleBranchConversation = (
    conversationId: string,
    upToMessageId: string
  ) => {
    const parent = conversations.find((c) => c.id === conversationId);
    if (!parent) return;
    const convMsgs = messages.filter((m) => m.conversationId === conversationId);
    const cutoffIndex = convMsgs.findIndex((m) => m.id === upToMessageId);
    const sliced =
      cutoffIndex >= 0 ? convMsgs.slice(0, cutoffIndex + 1) : convMsgs;

    const branchedId = `conv-branch-${Date.now()}`;
    const branchedConv: Conversation = {
      id: branchedId,
      title: `${parent.title} (Branched)`,
      personaId: parent.personaId,
      modelId: parent.modelId,
      rollingSummary: parent.rollingSummary,
      parentConversationId: parent.id,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    const clonedMsgs: ChatMessage[] = sliced.map((m, i) => ({
      ...m,
      id: `msg-br-${Date.now()}-${i}`,
      conversationId: branchedId,
    }));

    setConversations((prev) => [branchedConv, ...prev]);
    setMessages((prev) => [...prev, ...clonedMsgs]);
    setActiveConversationId(branchedId);
  };

  const handleDeleteConversation = (id: string) => {
    setConversations((prev) => {
      const remaining = prev.filter((c) => c.id !== id);
      if (activeConversationId === id && remaining.length > 0) {
        setActiveConversationId(remaining[0].id);
      }
      return remaining;
    });
    setMessages((prev) => prev.filter((m) => m.conversationId !== id));
  };

  const handleAddMessage = (msg: ChatMessage) => {
    setMessages((prev) => [...prev, msg]);
    if (msg.role === 'user') {
      setConversations((prev) =>
        prev.map((c) =>
          c.id === msg.conversationId
            ? {
                ...c,
                title:
                  c.title === 'New Offline Session'
                    ? msg.content.slice(0, 46)
                    : c.title,
                updatedAt: new Date().toISOString(),
              }
            : c
        )
      );
    }
  };

  const handleUpdateMessage = (
    msgId: string,
    updater: (prev: ChatMessage) => ChatMessage
  ) => {
    setMessages((prev) => prev.map((m) => (m.id === msgId ? updater(m) : m)));
  };

  const handleDeleteMessage = (msgId: string) => {
    setMessages((prev) => prev.filter((m) => m.id !== msgId));
  };

  const handleSavePersona = (persona: Persona) => {
    setPersonas((prev) => {
      const exists = prev.some((p) => p.id === persona.id);
      return exists
        ? prev.map((p) => (p.id === persona.id ? persona : p))
        : [...prev, persona];
    });
  };

  const handleDeletePersona = (personaId: string) => {
    setPersonas((prev) => prev.filter((p) => p.id !== personaId || p.isBuiltIn));
  };

  const handleDeleteSourceCascade = (sourceId: string) => {
    setSources((prev) => prev.filter((s) => s.id !== sourceId));
    setChunks((prev) => prev.filter((c) => c.sourceId !== sourceId));
  };

  const handleDeleteCollection = (collectionId: string) => {
    setCollections((prev) => prev.filter((c) => c.id !== collectionId));
    setSources((prev) => prev.filter((s) => s.collectionId !== collectionId));
    setChunks((prev) => prev.filter((c) => c.collectionId !== collectionId));
  };

  const handleAddMemory = (
    factText: string,
    scope: 'GLOBAL' | 'PERSONA',
    personaId?: string
  ) => {
    const newMem: UserMemory = {
      id: `mem-${Date.now()}`,
      factText,
      scope,
      personaId: scope === 'PERSONA' ? personaId : undefined,
      isApprovedByUser: true,
      createdAt: new Date().toISOString(),
    };
    setMemories((prev) => [newMem, ...prev]);
  };

  const handleReviewVocabularyItem = (
    id: string,
    rating: 'HARD' | 'GOOD' | 'EASY'
  ) => {
    setVocabulary((prev) =>
      prev.map((v) => {
        if (v.id !== id) return v;
        const mult = rating === 'EASY' ? 2.2 : rating === 'GOOD' ? 1.5 : 1.0;
        const nextInterval = Math.max(1, Math.round(v.intervalDays * mult));
        return {
          ...v,
          intervalDays: nextInterval,
          dueLabel: `Reviewed (${rating}) · Next in ${nextInterval}d`,
        };
      })
    );
  };

  const handleWipeAllLocalData = () => {
    localStorage.removeItem(STORAGE_KEY);
    setModels(INITIAL_MODELS);
    setPersonas(INITIAL_PERSONAS);
    setCollections(INITIAL_COLLECTIONS);
    setSources(INITIAL_SOURCES);
    setChunks(INITIAL_CHUNKS);
    setConversations(INITIAL_CONVERSATIONS);
    setMessages(INITIAL_MESSAGES);
    setMemories([]);
    setVocabulary(INITIAL_VOCABULARY);
    setActiveConversationId(INITIAL_CONVERSATIONS[0].id);
  };

  const navItems: { id: NavigationTab; label: string; icon: React.ReactNode }[] = [
    { id: 'chat', label: 'Chat', icon: <MessageSquare className="w-5 h-5" /> },
    { id: 'personas', label: 'Personas', icon: <Users className="w-5 h-5" /> },
    { id: 'knowledge', label: 'Knowledge', icon: <BookOpen className="w-5 h-5" /> },
    { id: 'models', label: 'Models', icon: <Cpu className="w-5 h-5" /> },
    { id: 'settings', label: 'Settings', icon: <Settings className="w-5 h-5" /> },
  ];

  return (
    <div
      className={`min-h-screen flex flex-col pb-16 md:pb-0 transition-colors ${
        theme === 'dark' ? 'bg-black text-[#F4F4F6]' : 'bg-[#F8FAFC] text-[#0F172A]'
      }`}
    >
      {/* Strict 3-Zone Top Bar Contract with Model Status Indicator & Theme Toggle */}
      <header
        className={`sticky top-0 z-30 min-h-[56px] sm:min-h-[64px] h-14 sm:h-16 flex items-center justify-between flex-nowrap gap-2 sm:gap-3 md:gap-4 px-3 sm:px-4 md:px-6 border-b transition-colors backdrop-blur-md pt-[env(safe-area-inset-top,0px)] shrink-0 ${
          theme === 'dark'
            ? 'border-[#1E2028] bg-black/95 text-white'
            : 'border-slate-200 bg-white/95 text-slate-900 shadow-xs'
        }`}
      >
        {/* Zone 1: Single text wordmark & live on-device Model Status Indicator */}
        <div className="flex items-center gap-2 sm:gap-3 shrink-0">
          <a
            href="#chat"
            onClick={(e) => {
              e.preventDefault();
              setActiveTab('chat');
            }}
            className="text-base sm:text-lg font-bold tracking-tight whitespace-nowrap shrink-0 flex items-center gap-2"
          >
            <span className={theme === 'dark' ? 'text-white' : 'text-slate-900'}>SlothLlama</span>
          </a>

          {/* Model Status Indicator */}
          <ModelStatusIndicator
            models={models}
            loadedModelId={loadedModelId}
            onSelectLoadedModel={setLoadedModelId}
            onNavigateToModels={() => setActiveTab('models')}
          />
        </div>

        {/* Zone 2: 5 concise single-line text navigation links */}
        <nav
          aria-label="Primary navigation"
          className="hidden md:flex items-center gap-2 md:gap-3 lg:gap-5 xl:gap-6 text-xs lg:text-sm font-medium shrink-0"
        >
          {navItems.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setActiveTab(item.id)}
              className={`py-1 sm:py-1.5 transition-colors whitespace-nowrap shrink-0 border-b-2 cursor-pointer ${
                activeTab === item.id
                  ? theme === 'dark'
                    ? 'text-white border-[#38BDF8]'
                    : 'text-slate-950 border-[#0284C7] font-semibold'
                  : theme === 'dark'
                    ? 'text-[#8E929F] border-transparent hover:text-white'
                    : 'text-slate-500 border-transparent hover:text-slate-900'
              }`}
            >
              {item.label}
            </button>
          ))}
        </nav>

        {/* Zone 3: Actions (Theme Toggle, Android Launch Center, Hardware Scan) */}
        <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
          {/* Light / Dark Mode Toggle */}
          <button
            type="button"
            onClick={handleToggleTheme}
            className={`w-9 h-9 sm:w-10 sm:h-10 rounded-xl flex items-center justify-center border transition-colors cursor-pointer shrink-0 ${
              theme === 'dark'
                ? 'border-[#1E2028] bg-[#121418] text-[#8E929F] hover:text-white'
                : 'border-slate-200 bg-slate-100 text-slate-700 hover:text-slate-950'
            }`}
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to OLED dark mode'}
            title={theme === 'dark' ? 'Switch to light mode' : 'Switch to OLED dark mode'}
          >
            {theme === 'dark' ? (
              <Sun className="w-4 h-4 text-[#FBBF24]" />
            ) : (
              <Moon className="w-4 h-4 text-[#0284C7]" />
            )}
          </button>

          {/* Android Launch CTA Button */}
          <button
            type="button"
            onClick={() => setIsAndroidLaunchOpen(true)}
            className="min-h-[36px] sm:min-h-[40px] px-2.5 sm:px-3 py-1.5 text-xs font-semibold bg-[#38BDF8] text-black hover:bg-[#38BDF8]/90 rounded-xl flex items-center gap-1.5 transition-all shadow-sm shadow-[#38BDF8]/20 whitespace-nowrap shrink-0 cursor-pointer"
            title="Prepare for deployment and launch to Android Studio"
          >
            <Rocket className="w-3.5 h-3.5" />
            <span className="hidden xl:inline">Android Launch</span>
          </button>

          {/* Hardware & Setup Scan Button */}
          <button
            type="button"
            onClick={() => setIsOnboardingOpen(true)}
            className={`hidden xl:flex min-h-[40px] px-3 py-1.5 text-xs font-semibold rounded-xl border transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
              theme === 'dark'
                ? 'bg-[#121418] hover:bg-[#191C22] text-white border-[#1E2028]'
                : 'bg-white hover:bg-slate-100 text-slate-800 border-slate-200'
            }`}
          >
            Device Scan
          </button>
        </div>
      </header>

      {/* Main Viewport Content */}
      <main className="flex-1 bg-black">
        {activeTab === 'chat' && (
          <ChatWorkspace
            conversations={conversations}
            activeConversationId={activeConversationId}
            onSelectConversation={setActiveConversationId}
            onCreateConversation={handleCreateConversation}
            onBranchConversation={handleBranchConversation}
            onDeleteConversation={handleDeleteConversation}
            messages={messages}
            onAddMessage={handleAddMessage}
            onUpdateMessage={handleUpdateMessage}
            onDeleteMessage={handleDeleteMessage}
            models={models}
            loadedModelId={loadedModelId}
            onSelectLoadedModel={(modelId) => setLoadedModelId(modelId)}
            personas={personas}
            activePersonaId={activePersonaId}
            onSelectPersona={(pid) => setActivePersonaId(pid)}
            onUpdatePersona={handleSavePersona}
            collections={collections}
            sources={sources}
            chunks={chunks}
            memories={memories}
            onRequestRememberFact={handleAddMemory}
            vocabulary={vocabulary}
            onAddVocabularyItem={(item) =>
              setVocabulary((prev) => [item, ...prev])
            }
            onReviewVocabularyItem={handleReviewVocabularyItem}
            isOfflineMode={isOfflineMode}
            cloudPolicy={cloudPolicy}
            voiceFeatureFlag={voiceFeatureFlag}
            onNavigateToModels={() => setActiveTab('models')}
          />
        )}

        {activeTab === 'personas' && (
          <PersonasView
            personas={personas}
            activePersonaId={activePersonaId}
            models={models}
            collections={collections}
            onSelectPersonaForChat={(pid) => {
              setActivePersonaId(pid);
              setActiveTab('chat');
            }}
            onSavePersona={handleSavePersona}
            onDeletePersona={handleDeletePersona}
          />
        )}

        {activeTab === 'knowledge' && (
          <KnowledgeView
            collections={collections}
            sources={sources}
            chunks={chunks}
            allowedDomains={allowedDomains}
            networkAcquisitionEnabled={networkAcquisitionEnabled}
            isOfflineMode={isOfflineMode}
            onToggleNetworkAcquisition={setNetworkAcquisitionEnabled}
            onUpdateAllowedDomains={setAllowedDomains}
            onCreateCollection={(col) =>
              setCollections((prev) => [col, ...prev])
            }
            onDeleteCollection={handleDeleteCollection}
            onAddIndexedSourceAndChunks={(src, newChunks) => {
              setSources((prev) => [src, ...prev]);
              setChunks((prev) => [...newChunks, ...prev]);
            }}
            onDeleteSourceCascade={handleDeleteSourceCascade}
          />
        )}

        {activeTab === 'models' && (
          <ModelsView
            models={models}
            loadedModelId={loadedModelId}
            deviceRamGb={8.0}
            deviceStorageFreeGb={28.4}
            isOfflineMode={isOfflineMode}
            onUpdateModel={(modelId, updater) =>
              setModels((prev) =>
                prev.map((m) => (m.modelId === modelId ? updater(m) : m))
              )
            }
            onAddCustomModel={(model) => setModels((prev) => [model, ...prev])}
            onLoadModelIntoMemory={setLoadedModelId}
          />
        )}

        {activeTab === 'settings' && (
          <SettingsAndArchitectureView
            models={models}
            personas={personas}
            collections={collections}
            sources={sources}
            chunks={chunks}
            memories={memories}
            allowedDomains={allowedDomains}
            isOfflineMode={isOfflineMode}
            onToggleOfflineMode={setIsOfflineMode}
            cloudPolicy={cloudPolicy}
            onChangeCloudPolicy={setCloudPolicy}
            batterySaverMode={batterySaverMode}
            onToggleBatterySaver={setBatterySaverMode}
            voiceFeatureFlag={voiceFeatureFlag}
            onToggleVoiceFeatureFlag={setVoiceFeatureFlag}
            onAddMemory={handleAddMemory}
            onDeleteMemory={(id) =>
              setMemories((prev) => prev.filter((m) => m.id !== id))
            }
            onDeleteAllMemories={() => setMemories([])}
            onWipeAllLocalData={handleWipeAllLocalData}
            onOpenOnboarding={() => setIsOnboardingOpen(true)}
            onOpenAndroidLaunch={() => setIsAndroidLaunchOpen(true)}
            theme={theme}
            onToggleTheme={handleToggleTheme}
          />
        )}
      </main>

      {/* Mobile Bottom Navigation Bar (5 Tabs: Chat, Personas, Knowledge, Models, Settings) */}
      <nav
        aria-label="Mobile bottom navigation"
        className={`md:hidden fixed bottom-0 left-0 right-0 z-40 h-16 backdrop-blur-md border-t grid grid-cols-5 items-center transition-colors ${
          theme === 'dark'
            ? 'bg-[#0B0C0E]/95 border-[#1E2028]'
            : 'bg-white/95 border-slate-200 shadow-lg'
        }`}
      >
        {navItems.map((item) => {
          const active = activeTab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => setActiveTab(item.id)}
              className={`min-h-[48px] flex flex-col items-center justify-center transition-colors ${
                active
                  ? theme === 'dark'
                    ? 'text-[#38BDF8]'
                    : 'text-[#0284C7]'
                  : theme === 'dark'
                    ? 'text-[#8E929F] hover:text-white'
                    : 'text-slate-500 hover:text-slate-900'
              }`}
            >
              {item.icon}
              <span className="text-[10px] font-medium tracking-tight mt-1 whitespace-nowrap">
                {item.label}
              </span>
            </button>
          );
        })}
      </nav>

      {/* First-Run Onboarding & Device Capability Scan Modal */}
      <OnboardingModal
        isOpen={isOnboardingOpen}
        onClose={() => setIsOnboardingOpen(false)}
        starterModel={models[0]}
        deviceRamGb={8.0}
        deviceStorageFreeGb={28.4}
        isOfflineMode={isOfflineMode}
        onSetOfflineMode={setIsOfflineMode}
        onAcceptStarterLicense={(modelId) =>
          setModels((prev) =>
            prev.map((m) =>
              m.modelId === modelId
                ? {
                    ...m,
                    licenseAccepted: true,
                    licenseAcceptedAt: new Date().toISOString(),
                  }
                : m
            )
          )
        }
      />

      {/* Android Deployment & Launch Center Modal */}
      <AndroidLaunchModal
        isOpen={isAndroidLaunchOpen}
        onClose={() => setIsAndroidLaunchOpen(false)}
      />
    </div>
  );
}
