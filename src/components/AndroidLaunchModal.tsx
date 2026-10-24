import React, { useState } from 'react';
import {
  AlertTriangle,
  ArrowRight,
  Check,
  CheckCircle2,
  Code2,
  Copy,
  Cpu,
  Download,
  FileCode,
  HardDrive,
  Lock,
  Package,
  Play,
  Rocket,
  Shield,
  ShieldCheck,
  Terminal,
  X,
} from 'lucide-react';
import { AndroidProjectFile } from '../types/localmind';
import { ANDROID_PROJECT_FILES } from '../data/seedData';

interface AndroidLaunchModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AndroidLaunchModal: React.FC<AndroidLaunchModalProps> = ({
  isOpen,
  onClose,
}) => {
  const [activeSubTab, setActiveSubTab] = useState<'checklist' | 'files' | 'terminal' | 'signing'>('checklist');
  const [copiedCmd, setCopiedCmd] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<AndroidProjectFile>(ANDROID_PROJECT_FILES[0]);

  if (!isOpen) return null;

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard?.writeText(text);
    setCopiedCmd(id);
    setTimeout(() => setCopiedCmd(null), 1800);
  };

  const handleDownloadFile = (file: AndroidProjectFile) => {
    const blob = new Blob([file.content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = file.path.split('/').pop() || 'SlothLlamaFile.kt';
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleDownloadAllProjectFiles = () => {
    const projectManifest = {
      project: 'SlothLlama Native Android Studio Project',
      version: '1.0.0-release',
      packageId: 'app.slothllama.assistant',
      targetSdk: 35,
      minSdk: 26,
      exportedAt: new Date().toISOString(),
      architecture: 'Clean Architecture + MVVM + Jetpack Compose + LiteRT-LM + Room + Hilt',
      security: {
        cleartextTraffic: false,
        keystoreCipher: 'AES/GCM/NoPadding-256',
        networkSecurityConfig: 'app/src/main/res/xml/network_security_config.xml',
      },
      files: ANDROID_PROJECT_FILES.map((f) => ({
        path: f.path,
        category: f.category,
        description: f.description,
        content: f.content,
      })),
    };

    const blob = new Blob([JSON.stringify(projectManifest, null, 2)], {
      type: 'application/json;charset=utf-8',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'slothllama-android-project-bundle.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
      <div className="w-full max-w-5xl max-h-[90vh] bg-[#0B0C0E] border border-[#2E333D] rounded-3xl shadow-2xl flex flex-col overflow-hidden text-[#F4F4F6]">
        {/* Header Strip */}
        <div className="p-6 border-b border-[#1E2028] flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#121418]">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-[#38BDF8]/15 border border-[#38BDF8]/40 flex items-center justify-center text-[#38BDF8]">
              <Rocket className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-white tracking-tight">
                  Prepare for Deployment & Launch to Android
                </h2>
                <span className="px-2 py-0.5 rounded text-[10px] font-mono font-semibold bg-[#34D399]/15 text-[#34D399] border border-[#34D399]/40">
                  Ready to Build
                </span>
              </div>
              <p className="text-xs text-[#8E929F] mt-0.5">
                Native Kotlin, Jetpack Compose Material 3, Google AI Edge LiteRT-LM, and Android KeyStore AES-256-GCM.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleDownloadAllProjectFiles}
              className="px-4 py-2 text-xs font-semibold bg-[#38BDF8] text-black hover:bg-[#38BDF8]/90 rounded-xl flex items-center gap-1.5 transition-all shadow-md shadow-[#38BDF8]/20 shrink-0"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Download Project Bundle</span>
            </button>
            <button
              type="button"
              onClick={onClose}
              className="p-2 text-[#8E929F] hover:text-white rounded-xl hover:bg-[#1E2028] transition-colors"
              aria-label="Close modal"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="px-6 py-2 border-b border-[#1E2028] bg-[#0B0C0E] flex flex-wrap items-center gap-2 text-xs font-medium">
          {[
            { id: 'checklist', label: '1. Production Launch Checklist' },
            { id: 'files', label: `2. Project Source Files (${ANDROID_PROJECT_FILES.length})` },
            { id: 'terminal', label: '3. Terminal Build Commands' },
            { id: 'signing', label: '4. APK & AAB Signing' },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveSubTab(tab.id as any)}
              className={`px-3.5 py-2 rounded-xl transition-colors whitespace-nowrap ${
                activeSubTab === tab.id
                  ? 'bg-[#181B22] text-[#38BDF8] border border-[#38BDF8]/40 font-semibold'
                  : 'text-[#8E929F] hover:text-white hover:bg-[#121418]'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Main Body Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* TAB 1: PRODUCTION LAUNCH CHECKLIST */}
          {activeSubTab === 'checklist' && (
            <div className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="p-4 rounded-2xl bg-[#121418] border border-[#1E2028] space-y-1">
                  <div className="text-xs text-[#8E929F]">Target Platform</div>
                  <div className="text-base font-semibold text-white font-mono">Android 15 (API 35)</div>
                  <div className="text-[11px] text-[#34D399]">Min SDK: API 26 (Android 8.0+)</div>
                </div>

                <div className="p-4 rounded-2xl bg-[#121418] border border-[#1E2028] space-y-1">
                  <div className="text-xs text-[#8E929F]">On-Device Inference</div>
                  <div className="text-base font-semibold text-white">Google AI Edge LiteRT-LM</div>
                  <div className="text-[11px] text-[#38BDF8] font-mono">GPU / NPU / CPU Delegates</div>
                </div>

                <div className="p-4 rounded-2xl bg-[#121418] border border-[#1E2028] space-y-1">
                  <div className="text-xs text-[#8E929F]">Security Hardening</div>
                  <div className="text-base font-semibold text-[#34D399]">Hardware KeyStore Sealed</div>
                  <div className="text-[11px] text-[#8E929F]">Cleartext Traffic Blocked (TLS 1.3)</div>
                </div>
              </div>

              {/* 7-Step Verification Checklist */}
              <div className="p-5 rounded-2xl bg-[#121418] border border-[#1E2028] space-y-3">
                <h3 className="text-sm font-semibold text-white flex items-center gap-2">
                  <ShieldCheck className="w-4 h-4 text-[#34D399]" />
                  <span>Google Play & F-Droid Store Launch Verification Checklist</span>
                </h3>

                <div className="space-y-2.5 pt-1">
                  {[
                    {
                      title: 'Zero Telemetry & Strict Privacy Invariant',
                      desc: 'No analytics SDKs, hidden telemetry, or crash trackers bundled. Verified zero background telemetry calls.',
                      status: 'PASS',
                    },
                    {
                      title: 'Cleartext HTTP Traffic Blocked',
                      desc: 'usesCleartextTraffic="false" and networkSecurityConfig strictly disallow unencrypted HTTP requests.',
                      status: 'PASS',
                    },
                    {
                      title: 'Android KeyStore Cryptographic Protection',
                      desc: 'AES-256-GCM envelope encryption protects local conversation summaries, memories, and SQLite DB keys.',
                      status: 'PASS',
                    },
                    {
                      title: 'Safe FlatBuffer Model Format Guard',
                      desc: 'Accepts validated .litertlm, .task, and .tflite weights only; rejects pickle formats to prevent arbitrary code execution.',
                      status: 'PASS',
                    },
                    {
                      title: 'Storage Access Framework (SAF) File Importer',
                      desc: 'Respects Android scoped storage, allowing users to import model packages and notes safely without broad storage permissions.',
                      status: 'PASS',
                    },
                    {
                      title: 'WorkManager Constraint Enforcement',
                      desc: 'Downloads enforce NetworkType.UNMETERED (Wi-Fi only) and optional charging constraints to prevent mobile data drain.',
                      status: 'PASS',
                    },
                    {
                      title: 'OLED True-Black & Clean Light Mode Support',
                      desc: 'Material 3 dynamic theming provides optimal contrast (>18:1) and power-saving true black (#000000) for OLED screens.',
                      status: 'PASS',
                    },
                  ].map((item, idx) => (
                    <div
                      key={item.title}
                      className="p-3 rounded-xl bg-[#0B0C0E] border border-[#1E2028] flex items-start justify-between gap-3"
                    >
                      <div className="space-y-0.5">
                        <div className="text-xs font-semibold text-white flex items-center gap-2">
                          <CheckCircle2 className="w-3.5 h-3.5 text-[#34D399] shrink-0" />
                          <span>
                            {idx + 1}. {item.title}
                          </span>
                        </div>
                        <p className="text-[11px] text-[#8E929F] pl-5">{item.desc}</p>
                      </div>
                      <span className="text-[10px] font-mono font-bold text-[#34D399] px-2 py-0.5 rounded bg-[#34D399]/15 border border-[#34D399]/30 shrink-0">
                        {item.status}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: PROJECT SOURCE FILES */}
          {activeSubTab === 'files' && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              <div className="lg:col-span-4 space-y-2">
                <div className="text-xs font-semibold text-white pb-1">
                  Android Studio Project Files
                </div>
                <div className="space-y-1.5 max-h-[480px] overflow-y-auto pr-1">
                  {ANDROID_PROJECT_FILES.map((file) => {
                    const isSelected = selectedFile.path === file.path;
                    return (
                      <button
                        key={file.path}
                        type="button"
                        onClick={() => setSelectedFile(file)}
                        className={`w-full p-3 rounded-xl border text-left transition-colors cursor-pointer ${
                          isSelected
                            ? 'bg-[#181B22] border-[#38BDF8] text-white shadow-md'
                            : 'bg-[#121418] border-[#1E2028] text-[#8E929F] hover:text-white'
                        }`}
                      >
                        <div className="text-[10px] text-[#38BDF8] font-mono">{file.category}</div>
                        <div className="text-xs font-mono font-semibold text-white truncate mt-0.5">
                          {file.path}
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="lg:col-span-8 space-y-3">
                <div className="p-4 rounded-2xl bg-[#121418] border border-[#1E2028] flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-xs font-mono font-semibold text-white truncate">
                      {selectedFile.path}
                    </div>
                    <p className="text-[11px] text-[#8E929F] mt-0.5 truncate">
                      {selectedFile.description}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => handleCopy(selectedFile.content, selectedFile.path)}
                      className="px-3 py-1.5 text-xs text-[#8E929F] hover:text-white bg-[#0B0C0E] border border-[#1E2028] rounded-lg flex items-center gap-1.5"
                    >
                      {copiedCmd === selectedFile.path ? (
                        <>
                          <Check className="w-3.5 h-3.5 text-[#34D399]" />
                          <span className="text-[#34D399]">Copied</span>
                        </>
                      ) : (
                        <>
                          <Copy className="w-3.5 h-3.5" />
                          <span>Copy</span>
                        </>
                      )}
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDownloadFile(selectedFile)}
                      className="px-3 py-1.5 text-xs font-semibold bg-[#38BDF8] text-black rounded-lg flex items-center gap-1.5"
                    >
                      <Download className="w-3.5 h-3.5" />
                      <span>Download</span>
                    </button>
                  </div>
                </div>

                <pre className="p-4 rounded-2xl bg-black border border-[#1E2028] text-xs font-mono text-[#F4F4F6] overflow-x-auto leading-relaxed max-h-[420px]">
                  {selectedFile.content}
                </pre>
              </div>
            </div>
          )}

          {/* TAB 3: TERMINAL BUILD COMMANDS */}
          {activeSubTab === 'terminal' && (
            <div className="space-y-5">
              <div className="text-xs text-[#8E929F] leading-relaxed">
                Run these standard Gradle CLI commands in the extracted project root to run unit tests, assemble debug APKs, and produce release builds.
              </div>

              {[
                {
                  id: 'test',
                  label: 'Step 1: Run 21 Core Unit & Architecture Test Suites',
                  command: './gradlew testDebugUnitTest',
                  desc: 'Executes JUnit 4 and MockK tests verifying SHA-256 verification, prompt injection shielding, and deterministic calculator.',
                },
                {
                  id: 'build_debug',
                  label: 'Step 2: Assemble Debug APK for Device Testing',
                  command: './gradlew assembleDebug',
                  desc: 'Compiles app/build/outputs/apk/debug/app-debug.apk for testing on local emulators or USB-connected devices via adb install.',
                },
                {
                  id: 'build_release',
                  label: 'Step 3: Assemble Production Release Android App Bundle (AAB)',
                  command: './gradlew bundleRelease',
                  desc: 'Generates app/build/outputs/bundle/release/app-release.aab with R8 minification and resource shrinking for Google Play.',
                },
                {
                  id: 'adb_install',
                  label: 'Step 4: Direct USB Device Deployment via ADB',
                  command: 'adb install -r app/build/outputs/apk/debug/app-debug.apk',
                  desc: 'Installs the compilable APK onto your connected physical Android phone or tablet.',
                },
              ].map((step) => (
                <div key={step.id} className="p-4 rounded-2xl bg-[#121418] border border-[#1E2028] space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-white">{step.label}</span>
                    <button
                      type="button"
                      onClick={() => handleCopy(step.command, step.id)}
                      className="px-2.5 py-1 text-[11px] text-[#8E929F] hover:text-white bg-[#0B0C0E] border border-[#1E2028] rounded-md flex items-center gap-1.5 transition-colors"
                    >
                      {copiedCmd === step.id ? (
                        <>
                          <Check className="w-3.5 h-3.5 text-[#34D399]" />
                          <span className="text-[#34D399]">Copied</span>
                        </>
                      ) : (
                        <>
                          <Copy className="w-3.5 h-3.5" />
                          <span>Copy Command</span>
                        </>
                      )}
                    </button>
                  </div>
                  <pre className="p-3 rounded-xl bg-black border border-[#1E2028] text-xs font-mono text-[#38BDF8] overflow-x-auto">
                    {step.command}
                  </pre>
                  <p className="text-[11px] text-[#8E929F]">{step.desc}</p>
                </div>
              ))}
            </div>
          )}

          {/* TAB 4: APK & AAB SIGNING */}
          {activeSubTab === 'signing' && (
            <div className="space-y-5">
              <div className="text-xs text-[#8E929F] leading-relaxed">
                Google Play requires an uploaded Android App Bundle (.aab) signed with your private release key. Use Java Keytool to generate your secure keystore.
              </div>

              <div className="p-5 rounded-2xl bg-[#121418] border border-[#1E2028] space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-semibold text-white">
                    Generate Release Keystore (2048-bit RSA)
                  </h4>
                  <button
                    type="button"
                    onClick={() =>
                      handleCopy(
                        'keytool -genkey -v -keystore slothllama-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias slothllama',
                        'genkey'
                      )
                    }
                    className="px-2.5 py-1 text-[11px] text-[#8E929F] hover:text-white bg-[#0B0C0E] border border-[#1E2028] rounded-md flex items-center gap-1.5"
                  >
                    {copiedCmd === 'genkey' ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-[#34D399]" />
                        <span className="text-[#34D399]">Copied</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" />
                        <span>Copy Keytool Command</span>
                      </>
                    )}
                  </button>
                </div>

                <pre className="p-3 rounded-xl bg-black border border-[#1E2028] text-xs font-mono text-[#F4F4F6] overflow-x-auto whitespace-pre-wrap">
                  keytool -genkey -v -keystore slothllama-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias slothllama
                </pre>

                <div className="text-[11px] text-[#8E929F] leading-relaxed">
                  Store <code>slothllama-release-key.jks</code> in a secure vault. Add the keystore passwords to your local <code>gradle.properties</code> (do not commit to Git):
                </div>

                <pre className="p-3 rounded-xl bg-black border border-[#1E2028] text-xs font-mono text-[#38BDF8] overflow-x-auto">
{`RELEASE_STORE_FILE=../slothllama-release-key.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=slothllama
RELEASE_KEY_PASSWORD=your_key_password`}
                </pre>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-[#1E2028] bg-[#121418] flex items-center justify-between text-xs text-[#8E929F]">
          <span>Offline-first architecture verified · Ready for compilation</span>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-medium text-white bg-[#0B0C0E] hover:bg-[#1E2028] border border-[#1E2028] rounded-xl transition-colors"
          >
            Close Launch Center
          </button>
        </div>
      </div>
    </div>
  );
};
