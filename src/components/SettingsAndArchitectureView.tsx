import React, { useState } from 'react';
import {
  CheckCircle2,
  Code2,
  Cpu,
  Database,
  Download,
  FileCode,
  HardDrive,
  Lock,
  Play,
  Shield,
  Sliders,
  Trash2,
  WifiOff,
} from 'lucide-react';
import {
  AndroidProjectFile,
  CloudRoutingPolicy,
  KnowledgeChunk,
  KnowledgeCollection,
  KnowledgeSource,
  LocalModel,
  Persona,
  SourcePolicyDomain,
  UserMemory,
} from '../types/localmind';
import { ANDROID_PROJECT_FILES } from '../data/seedData';
import {
  runAllVerificationSuites,
  VerificationSuiteResult,
} from '../engine/localMindEngine';

interface SettingsAndArchitectureViewProps {
  models: LocalModel[];
  personas: Persona[];
  collections: KnowledgeCollection[];
  sources: KnowledgeSource[];
  chunks: KnowledgeChunk[];
  memories: UserMemory[];
  allowedDomains: SourcePolicyDomain[];
  isOfflineMode: boolean;
  onToggleOfflineMode: (offline: boolean) => void;
  cloudPolicy: CloudRoutingPolicy;
  onChangeCloudPolicy: (policy: CloudRoutingPolicy) => void;
  batterySaverMode: boolean;
  onToggleBatterySaver: (enabled: boolean) => void;
  voiceFeatureFlag: boolean;
  onToggleVoiceFeatureFlag: (enabled: boolean) => void;
  onAddMemory: (factText: string, scope: 'GLOBAL' | 'PERSONA', personaId?: string) => void;
  onDeleteMemory: (id: string) => void;
  onDeleteAllMemories: () => void;
  onWipeAllLocalData: () => void;
  onOpenOnboarding: () => void;
}

export const SettingsAndArchitectureView: React.FC<SettingsAndArchitectureViewProps> = ({
  models,
  personas,
  collections,
  sources,
  chunks,
  memories,
  allowedDomains,
  isOfflineMode,
  onToggleOfflineMode,
  cloudPolicy,
  onChangeCloudPolicy,
  batterySaverMode,
  onToggleBatterySaver,
  voiceFeatureFlag,
  onToggleVoiceFeatureFlag,
  onAddMemory,
  onDeleteMemory,
  onDeleteAllMemories,
  onWipeAllLocalData,
  onOpenOnboarding,
}) => {
  const [subTab, setSubTab] = useState<
    'privacy_storage' | 'memory_review' | 'diagnostics_tests' | 'android_studio_code'
  >('privacy_storage');
  const [newMemoryInput, setNewMemoryInput] = useState('');
  const [confirmWipe, setConfirmWipe] = useState(false);
  const [selectedAndroidFile, setSelectedAndroidFile] = useState<AndroidProjectFile>(
    ANDROID_PROJECT_FILES[0]
  );
  const [testResults, setTestResults] = useState<VerificationSuiteResult[]>(() =>
    runAllVerificationSuites({
      models,
      personas,
      collections,
      sources,
      chunks,
      allowedDomains,
    })
  );

  const installedModelsBytes = models
    .filter((m) => m.downloadStatus === 'INSTALLED')
    .reduce((acc, m) => acc + m.downloadSizeBytes, 0);
  const ragSourcesBytes = sources.reduce((acc, s) => acc + s.sizeBytes, 0);
  const vectorIndexBytes = chunks.length * 64 * 4;

  const handleRunAllTests = () => {
    const fresh = runAllVerificationSuites({
      models,
      personas,
      collections,
      sources,
      chunks,
      allowedDomains,
    });
    setTestResults(fresh);
  };

  const handleDownloadAndroidFile = (file: AndroidProjectFile) => {
    const blob = new Blob([file.content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = file.path.split('/').pop() || 'LocalMindFile.kt';
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 md:px-8 py-8 space-y-8 bg-black">
      {/* Header & Section Tabs */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-6 border-b border-[#1E2028]">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-white">
            Privacy, Storage, Diagnostics & Android Studio Project
          </h1>
          <p className="text-sm text-[#8E929F] mt-1">
            Full control over local data, explicit memory review, 18-suite automated verification, and Kotlin/Compose source code.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-1 p-1 bg-[#0B0C0E] border border-[#1E2028] rounded-xl self-start">
          {[
            { id: 'privacy_storage', label: 'Storage & Privacy' },
            { id: 'memory_review', label: `Memory Review (${memories.length})` },
            { id: 'diagnostics_tests', label: 'Diagnostics & 18 Tests' },
            { id: 'android_studio_code', label: 'Android Studio Kotlin Code' },
          ].map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setSubTab(t.id as any)}
              className={`px-3.5 py-2 text-xs font-medium rounded-lg transition-colors whitespace-nowrap ${
                subTab === t.id
                  ? 'bg-[#38BDF8] text-black font-semibold'
                  : 'text-[#8E929F] hover:text-white'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {/* TAB 1: STORAGE & PRIVACY DASHBOARD */}
      {subTab === 'privacy_storage' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
            <div className="p-5 rounded-2xl bg-[#0B0C0E] border border-[#1E2028] space-y-2">
              <div className="text-xs text-[#8E929F]">Installed Local Model Weights</div>
              <div className="text-2xl font-semibold text-white font-mono tabular-nums">
                {(installedModelsBytes / 1e9).toFixed(2)} GB
              </div>
              <div className="text-xs text-[#8E929F] font-mono tabular-nums">
                {models.filter((m) => m.downloadStatus === 'INSTALLED').length} verified .litertlm packages
              </div>
            </div>

            <div className="p-5 rounded-2xl bg-[#0B0C0E] border border-[#1E2028] space-y-2">
              <div className="text-xs text-[#8E929F]">Local RAG Chunks & Vector Index</div>
              <div className="text-2xl font-semibold text-white font-mono tabular-nums">
                {((ragSourcesBytes + vectorIndexBytes) / 1024).toFixed(1)} KB
              </div>
              <div className="text-xs text-[#8E929F] font-mono tabular-nums">
                {sources.length} sources · {chunks.length} semantic chunks (64-D float32)
              </div>
            </div>

            <div className="p-5 rounded-2xl bg-[#0B0C0E] border border-[#1E2028] space-y-2">
              <div className="text-xs text-[#8E929F]">Telemetry & Crash Reporting</div>
              <div className="text-2xl font-semibold text-[#34D399]">0 Bytes Sent</div>
              <div className="text-xs text-[#8E929F]">
                Analytics disabled by default · Keys redacted in Keystore
              </div>
            </div>
          </div>

          {/* Offline, Battery, Voice & Cloud Routing Controls */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="p-6 rounded-2xl bg-[#0B0C0E] border border-[#1E2028] space-y-4">
              <h2 className="text-base font-semibold text-white">
                Runtime & Hardware Controls
              </h2>

              <div className="space-y-3">
                <div className="flex items-center justify-between py-2 border-b border-[#1E2028]">
                  <div>
                    <div className="text-xs font-semibold text-white">
                      Airplane / Strict Offline Mode
                    </div>
                    <div className="text-[11px] text-[#8E929F]">
                      Simulates zero network connectivity; queues downloads and runs local inference only.
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => onToggleOfflineMode(!isOfflineMode)}
                    className={`px-3 py-1.5 text-xs font-semibold rounded-lg border ${
                      isOfflineMode
                        ? 'bg-[#FBBF24]/15 border-[#FBBF24] text-[#FBBF24]'
                        : 'bg-[#121418] border-[#1E2028] text-white'
                    }`}
                  >
                    {isOfflineMode ? 'Offline Active' : 'Online'}
                  </button>
                </div>

                <div className="flex items-center justify-between py-2 border-b border-[#1E2028]">
                  <div>
                    <div className="text-xs font-semibold text-white">Battery-Saver Mode</div>
                    <div className="text-[11px] text-[#8E929F]">
                      Caps generation thread pool and prefers NPU/low-power delegates to prevent thermal throttling.
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => onToggleBatterySaver(!batterySaverMode)}
                    className={`px-3 py-1.5 text-xs font-semibold rounded-lg border ${
                      batterySaverMode
                        ? 'bg-[#34D399]/15 border-[#34D399] text-[#34D399]'
                        : 'bg-[#121418] border-[#1E2028] text-[#8E929F]'
                    }`}
                  >
                    {batterySaverMode ? 'Enabled' : 'Disabled'}
                  </button>
                </div>

                <div className="flex items-center justify-between py-2 border-b border-[#1E2028]">
                  <div>
                    <div className="text-xs font-semibold text-white">
                      Offline Voice-Input Placeholder (Feature Flag)
                    </div>
                    <div className="text-[11px] text-[#8E929F]">
                      Enables the on-device speech-to-text microphone trigger inside the chat composer.
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => onToggleVoiceFeatureFlag(!voiceFeatureFlag)}
                    className={`px-3 py-1.5 text-xs font-semibold rounded-lg border ${
                      voiceFeatureFlag
                        ? 'bg-[#38BDF8]/15 border-[#38BDF8] text-[#38BDF8]'
                        : 'bg-[#121418] border-[#1E2028] text-[#8E929F]'
                    }`}
                  >
                    {voiceFeatureFlag ? 'Enabled' : 'Disabled'}
                  </button>
                </div>

                <div className="flex items-center justify-between pt-2">
                  <div>
                    <div className="text-xs font-semibold text-white">
                      First-Run Onboarding & Device Scan
                    </div>
                    <div className="text-[11px] text-[#8E929F]">
                      Re-open the 4-step hardware scan and 5-layer architecture guide.
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={onOpenOnboarding}
                    className="px-3 py-1.5 text-xs font-medium bg-[#121418] hover:bg-[#191C22] text-white border border-[#1E2028] rounded-lg"
                  >
                    Launch Onboarding
                  </button>
                </div>
              </div>
            </div>

            {/* Cloud Provider Policy & Delete All Local Data */}
            <div className="p-6 rounded-2xl bg-[#0B0C0E] border border-[#1E2028] flex flex-col justify-between space-y-6">
              <div className="space-y-3">
                <h2 className="text-base font-semibold text-white">
                  Optional Cloud Provider Routing Policy
                </h2>
                <p className="text-xs text-[#8E929F] leading-relaxed">
                  LocalMind never uploads prompts unless you explicitly configure a cloud provider policy. Raw API keys are never embedded in the APK; Firebase AI Logic + App Check or Android Keystore credentials are used.
                </p>

                <select
                  value={cloudPolicy}
                  onChange={(e) => onChangeCloudPolicy(e.target.value as CloudRoutingPolicy)}
                  className="w-full bg-[#121418] text-white text-xs px-3 py-2.5 rounded-xl border border-[#1E2028] font-mono"
                >
                  <option value="NEVER_USE_CLOUD">NEVER_USE_CLOUD (Default — 100% Local)</option>
                  <option value="ASK_EVERY_TIME">ASK_EVERY_TIME (Confirm Before Prompt Upload)</option>
                  <option value="ALLOW_ONLY_SELECTED_PERSONAS">
                    ALLOW_ONLY_SELECTED_PERSONAS
                  </option>
                  <option value="MANUAL_CLOUD_SELECTION">
                    MANUAL_CLOUD_SELECTION (Visibly Labeled)
                  </option>
                </select>
              </div>

              {/* Delete All Local Data */}
              <div className="p-4 rounded-xl bg-[#121418] border border-[#F87171]/40 space-y-3">
                <div className="text-xs font-semibold text-[#F87171]">
                  Danger Zone: Delete All Local Data
                </div>
                <p className="text-xs text-[#8E929F]">
                  Permanently purges all local conversations, explicit user memories, custom personas, and user-imported knowledge chunks, restoring clean factory defaults.
                </p>
                {!confirmWipe ? (
                  <button
                    type="button"
                    onClick={() => setConfirmWipe(true)}
                    className="px-4 py-2 text-xs font-semibold bg-[#F87171] text-black rounded-lg"
                  >
                    Delete All Local Data
                  </button>
                ) : (
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => {
                        onWipeAllLocalData();
                        setConfirmWipe(false);
                      }}
                      className="px-4 py-2 text-xs font-semibold bg-[#F87171] text-black rounded-lg"
                    >
                      Confirm Permanent Wipe
                    </button>
                    <button
                      type="button"
                      onClick={() => setConfirmWipe(false)}
                      className="text-xs text-[#8E929F] hover:text-white"
                    >
                      Cancel
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: EXPLICIT USER-APPROVED MEMORY REVIEW */}
      {subTab === 'memory_review' && (
        <div className="p-6 rounded-2xl bg-[#0B0C0E] border border-[#1E2028] space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-[#1E2028]">
            <div>
              <h2 className="text-lg font-semibold text-white">
                Explicit Long-Term Memory Review (Zero Hidden Profiling)
              </h2>
              <p className="text-xs text-[#8E929F]">
                LocalMind never silently profiles you. Every fact below was explicitly approved by you and can be edited or deleted at any time.
              </p>
            </div>
            {memories.length > 0 && (
              <button
                type="button"
                onClick={onDeleteAllMemories}
                className="px-3.5 py-2 text-xs font-medium text-[#F87171] bg-[#121418] border border-[#1E2028] rounded-lg whitespace-nowrap"
              >
                Delete All Memories
              </button>
            )}
          </div>

          <div className="flex flex-col sm:flex-row gap-3">
            <input
              type="text"
              value={newMemoryInput}
              onChange={(e) => setNewMemoryInput(e.target.value)}
              placeholder="Add an explicit personal preference (e.g., 'Explain calculus steps using Leibniz notation')..."
              className="flex-1 bg-[#121418] text-white text-xs px-3.5 py-2.5 rounded-xl border border-[#1E2028]"
            />
            <button
              type="button"
              onClick={() => {
                if (!newMemoryInput.trim()) return;
                onAddMemory(newMemoryInput.trim(), 'GLOBAL');
                setNewMemoryInput('');
              }}
              className="px-4 py-2.5 text-xs font-semibold bg-[#38BDF8] text-black rounded-xl whitespace-nowrap"
            >
              Add Approved Fact
            </button>
          </div>

          <div className="space-y-2.5">
            {memories.length === 0 ? (
              <div className="p-6 rounded-xl bg-[#121418] text-center text-xs text-[#8E929F]">
                No explicit long-term memories stored. Use "Remember This" on any chat message or add a preference above.
              </div>
            ) : (
              memories.map((mem) => (
                <div
                  key={mem.id}
                  className="p-4 rounded-xl bg-[#121418] border border-[#1E2028] flex items-center justify-between gap-4"
                >
                  <div>
                    <div className="text-xs font-mono text-[#38BDF8]">
                      Scope: {mem.scope} {mem.personaId ? `(${mem.personaId})` : ''} · Approved
                    </div>
                    <div className="text-sm text-white mt-0.5">{mem.factText}</div>
                  </div>
                  <button
                    type="button"
                    onClick={() => onDeleteMemory(mem.id)}
                    className="p-2 text-[#8E929F] hover:text-[#F87171]"
                    aria-label="Delete memory"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* TAB 3: DIAGNOSTICS & 18 AUTOMATED VERIFICATION SUITES */}
      {subTab === 'diagnostics_tests' && (
        <div className="space-y-6">
          <div className="p-5 rounded-2xl bg-[#0B0C0E] border border-[#1E2028] flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold text-white">
                18 Automated Unit, Security & UI Verification Suites
              </h2>
              <p className="text-xs text-[#8E929F]">
                Executes live assertions against `LocalInferenceEngine`, `SourcePolicyValidator`, `SecurePromptAssembler`, `LocalVectorStore`, and `DeterministicCalculatorTool`.
              </p>
            </div>
            <button
              type="button"
              onClick={handleRunAllTests}
              className="px-4 py-2 text-xs font-semibold bg-[#38BDF8] text-black rounded-xl flex items-center gap-1.5 whitespace-nowrap"
            >
              <Play className="w-3.5 h-3.5 fill-current" />
              <span>Re-Run All 18 Verification Suites</span>
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {testResults.map((tr) => (
              <div
                key={tr.id}
                className="p-4 rounded-xl bg-[#0B0C0E] border border-[#1E2028] flex flex-col justify-between gap-2"
              >
                <div className="flex items-center justify-between text-xs font-mono tabular-nums">
                  <span className="font-sans font-semibold text-white">{tr.name}</span>
                  <span className={tr.passed ? 'text-[#34D399]' : 'text-[#F87171]'}>
                    {tr.passed ? 'PASS' : 'FAIL'} · {tr.durationMs}ms
                  </span>
                </div>
                <p className="text-xs text-[#8E929F] leading-relaxed">{tr.assertionSummary}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 4: ANDROID STUDIO KOTLIN + JETPACK COMPOSE SOURCE & ARCHITECTURE EXPLORER */}
      {subTab === 'android_studio_code' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div className="lg:col-span-4 space-y-2">
            <div className="text-xs font-semibold text-white pb-1">
              Android Studio Project Deliverables (`/android-project/`)
            </div>
            {ANDROID_PROJECT_FILES.map((file) => {
              const isSelected = selectedAndroidFile.path === file.path;
              return (
                <button
                  key={file.path}
                  type="button"
                  onClick={() => setSelectedAndroidFile(file)}
                  className={`w-full p-3 rounded-xl border text-left transition-colors ${
                    isSelected
                      ? 'bg-[#121418] border-[#38BDF8] text-white'
                      : 'bg-[#0B0C0E] border-[#1E2028] text-[#8E929F] hover:text-white'
                  }`}
                >
                  <div className="text-[11px] text-[#38BDF8] font-mono">{file.category}</div>
                  <div className="text-xs font-mono font-semibold text-white truncate mt-0.5">
                    {file.path}
                  </div>
                </button>
              );
            })}
          </div>

          <div className="lg:col-span-8">
            <div className="p-5 rounded-2xl bg-[#0B0C0E] border border-[#1E2028] space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-3 border-b border-[#1E2028]">
                <div>
                  <div className="text-sm font-mono font-semibold text-white">
                    {selectedAndroidFile.path}
                  </div>
                  <p className="text-xs text-[#8E929F] mt-0.5">
                    {selectedAndroidFile.description}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => handleDownloadAndroidFile(selectedAndroidFile)}
                  className="px-3.5 py-1.5 text-xs font-semibold bg-[#38BDF8] text-black rounded-lg flex items-center gap-1.5 self-start shrink-0"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Download File</span>
                </button>
              </div>

              <pre className="p-4 rounded-xl bg-black border border-[#1E2028] text-xs font-mono text-[#F4F4F6] overflow-x-auto leading-relaxed max-h-[540px]">
                {selectedAndroidFile.content}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
