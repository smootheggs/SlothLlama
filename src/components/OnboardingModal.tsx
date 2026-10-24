import React, { useState } from 'react';
import {
  CheckCircle2,
  Cpu,
  HardDrive,
  Layers,
  Lock,
  ShieldCheck,
  WifiOff,
  X,
} from 'lucide-react';
import { LocalModel } from '../types/localmind';

interface OnboardingModalProps {
  isOpen: boolean;
  onClose: () => void;
  starterModel: LocalModel;
  deviceRamGb: number;
  deviceStorageFreeGb: number;
  isOfflineMode: boolean;
  onSetOfflineMode: (offline: boolean) => void;
  onAcceptStarterLicense: (modelId: string) => void;
}

export const OnboardingModal: React.FC<OnboardingModalProps> = ({
  isOpen,
  onClose,
  starterModel,
  deviceRamGb,
  deviceStorageFreeGb,
  isOfflineMode,
  onSetOfflineMode,
  onAcceptStarterLicense,
}) => {
  const [step, setStep] = useState<number>(1);
  const [licenseChecked, setLicenseChecked] = useState<boolean>(starterModel.licenseAccepted);
  const [sampleKnowledgeChecked, setSampleKnowledgeChecked] = useState<boolean>(true);

  if (!isOpen) return null;

  const handleFinish = () => {
    if (licenseChecked) {
      onAcceptStarterLicense(starterModel.modelId);
    }
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-sm p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="onboarding-title"
    >
      <div className="w-full max-w-2xl rounded-2xl bg-[#0B0C0E] border border-[#1E2028] p-6 md:p-8 text-[#F4F4F6]">
        <div className="flex items-center justify-between pb-4 border-b border-[#1E2028]">
          <div className="text-xs text-[#8E929F] tabular-nums">
            <span>First-Run Setup & Hardware Scan</span>
            <span className="mx-2" aria-hidden="true">·</span>
            <span>Step {step} of 4</span>
          </div>
          <button
            onClick={onClose}
            className="min-h-[44px] min-w-[44px] flex items-center justify-center text-[#8E929F] hover:text-white rounded-lg transition-colors"
            aria-label="Close onboarding"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {step === 1 && (
          <div className="py-6 space-y-5">
            <h2 id="onboarding-title" className="text-2xl font-semibold tracking-tight text-white">
              01. Private, On-Device Intelligence — Zero Cloud Account Required
            </h2>
            <p className="text-sm text-[#8E929F] leading-relaxed">
              SlothLlama runs compatible quantized language models directly on your device hardware using Google AI Edge LiteRT-LM. Your conversations, personas, and indexed documents remain in private app storage by default.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2">
              <div className="p-4 rounded-xl bg-[#121418] border border-[#1E2028]">
                <HardDrive className="w-5 h-5 text-[#38BDF8] mb-2" />
                <div className="text-xs text-[#8E929F]">Available Storage</div>
                <div className="text-lg font-semibold text-white font-mono tabular-nums mt-0.5">
                  {deviceStorageFreeGb.toFixed(1)} GB free
                </div>
                <div className="text-xs text-[#8E929F] mt-1">
                  Local models require 0.9 GB – 7.6 GB on disk
                </div>
              </div>

              <div className="p-4 rounded-xl bg-[#121418] border border-[#1E2028]">
                <Cpu className="w-5 h-5 text-[#34D399] mb-2" />
                <div className="text-xs text-[#8E929F]">Device Memory & Delegates</div>
                <div className="text-lg font-semibold text-white font-mono tabular-nums mt-0.5">
                  {deviceRamGb} GB RAM
                </div>
                <div className="text-xs text-[#8E929F] mt-1">
                  CPU · GPU · NPU Delegates Detected
                </div>
              </div>

              <div className="p-4 rounded-xl bg-[#121418] border border-[#1E2028]">
                <Lock className="w-5 h-5 text-[#FBBF24] mb-2" />
                <div className="text-xs text-[#8E929F]">Account Requirement</div>
                <div className="text-lg font-semibold text-white mt-0.5">None Required</div>
                <div className="text-xs text-[#8E929F] mt-1">
                  Works 100% in Airplane Mode
                </div>
              </div>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="py-6 space-y-4">
            <h2 id="onboarding-title" className="text-2xl font-semibold tracking-tight text-white">
              02. Five Strictly Separated Architectural Layers
            </h2>
            <p className="text-sm text-[#8E929F] leading-relaxed">
              SlothLlama never claims that downloading documents permanently trains or alters your base model. Every response is assembled transparently from five distinct layers:
            </p>

            <div className="space-y-2.5 pt-1">
              {[
                {
                  code: 'a. Base Model',
                  desc: 'Read-only quantized weights (.litertlm / .task) verified by SHA-256 checksum.',
                },
                {
                  code: 'b. Persona Instructions',
                  desc: 'System prompt, temperature, and tutor configuration referencing an installed model without duplicating files.',
                },
                {
                  code: 'c. Locally Indexed Knowledge (RAG)',
                  desc: 'Semantic chunks and on-device vector embeddings retrieved at query time inside isolated <untrusted_retrieved_evidence> tags.',
                },
                {
                  code: 'd. Conversation & Explicit Memory',
                  desc: 'Rolling conversation history plus reviewable personal facts you explicitly approve with "Remember this".',
                },
                {
                  code: 'e. Optional Model Adapters (Experimental)',
                  desc: 'Optional read-only LoRA delta overlays clearly labeled as experimental.',
                },
              ].map((layer) => (
                <div
                  key={layer.code}
                  className="p-3.5 rounded-xl bg-[#121418] border border-[#1E2028] flex flex-col sm:flex-row sm:items-center justify-between gap-2"
                >
                  <span className="text-sm font-medium text-white shrink-0">{layer.code}</span>
                  <span className="text-xs text-[#8E929F] sm:text-right">{layer.desc}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="py-6 space-y-5">
            <h2 id="onboarding-title" className="text-2xl font-semibold tracking-tight text-white">
              03. Recommended Starter Model & License Acceptance
            </h2>
            <p className="text-sm text-[#8E929F] leading-relaxed">
              Based on your {deviceRamGb} GB RAM and {deviceStorageFreeGb.toFixed(1)} GB free storage, SlothLlama recommends the following validated starter package:
            </p>

            <div className="p-4 rounded-xl bg-[#121418] border border-[#1E2028] space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-base font-semibold text-white">{starterModel.displayName}</span>
                <span className="text-xs font-mono text-[#34D399] tabular-nums">
                  {(starterModel.downloadSizeBytes / 1e9).toFixed(2)} GB · {starterModel.quantization}
                </span>
              </div>
              <div className="text-xs text-[#8E929F] flex flex-wrap items-center gap-2 font-mono tabular-nums">
                <span>Arch: {starterModel.architecture}</span>
                <span aria-hidden="true">·</span>
                <span>Context: {starterModel.contextWindow.toLocaleString()} tokens</span>
                <span aria-hidden="true">·</span>
                <span>Est. RAM: {(starterModel.estimatedRamBytes / 1e9).toFixed(2)} GB</span>
                <span aria-hidden="true">·</span>
                <span>License: {starterModel.licenseName}</span>
              </div>

              <label className="flex items-start gap-3 pt-2 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={licenseChecked}
                  onChange={(e) => setLicenseChecked(e.target.checked)}
                  className="mt-1 h-4 w-4 rounded border-[#2E333D] bg-black text-[#38BDF8]"
                />
                <span className="text-xs text-[#F4F4F6] leading-relaxed">
                  I have reviewed and accept the <strong>{starterModel.licenseName}</strong> ({starterModel.licenseSpdx}) required before first inference execution.
                </span>
              </label>

              <label className="flex items-start gap-3 pt-1 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={sampleKnowledgeChecked}
                  onChange={(e) => setSampleKnowledgeChecked(e.target.checked)}
                  className="mt-1 h-4 w-4 rounded border-[#2E333D] bg-black text-[#38BDF8]"
                />
                <span className="text-xs text-[#F4F4F6] leading-relaxed">
                  Keep sample openly licensed Knowledge Collections (OpenStax Physics, LibreTexts Calculus, CEFR B1 Japanese) indexed for immediate offline RAG testing.
                </span>
              </label>
            </div>
          </div>
        )}

        {step === 4 && (
          <div className="py-6 space-y-5">
            <h2 id="onboarding-title" className="text-2xl font-semibold tracking-tight text-white">
              04. Network & Offline Preference
            </h2>
            <p className="text-sm text-[#8E929F] leading-relaxed">
              You can operate SlothLlama strictly offline or allow user-approved downloads from your Source Policy allowlist. No sign-in or telemetry is ever required.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <button
                type="button"
                onClick={() => onSetOfflineMode(true)}
                className={`p-4 rounded-xl border text-left transition-colors ${
                  isOfflineMode
                    ? 'bg-[#121418] border-[#38BDF8] text-white'
                    : 'bg-[#0B0C0E] border-[#1E2028] text-[#8E929F] hover:text-white'
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <WifiOff className="w-5 h-5 text-[#38BDF8]" />
                  {isOfflineMode && <CheckCircle2 className="w-4 h-4 text-[#38BDF8]" />}
                </div>
                <div className="text-sm font-semibold text-white">Strict Offline Mode</div>
                <p className="text-xs text-[#8E929F] mt-1">
                  Skip all online features. Run installed models, personas, tutors, and local RAG with zero network traffic.
                </p>
              </button>

              <button
                type="button"
                onClick={() => onSetOfflineMode(false)}
                className={`p-4 rounded-xl border text-left transition-colors ${
                  !isOfflineMode
                    ? 'bg-[#121418] border-[#38BDF8] text-white'
                    : 'bg-[#0B0C0E] border-[#1E2028] text-[#8E929F] hover:text-white'
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <ShieldCheck className="w-5 h-5 text-[#34D399]" />
                  {!isOfflineMode && <CheckCircle2 className="w-4 h-4 text-[#34D399]" />}
                </div>
                <div className="text-sm font-semibold text-white">Local-First + Approved Downloads</div>
                <p className="text-xs text-[#8E929F] mt-1">
                  Prefer local inference by default while allowing manual model downloads and allowlisted HTTPS knowledge imports.
                </p>
              </button>
            </div>
          </div>
        )}

        <div className="flex items-center justify-between pt-5 border-t border-[#1E2028]">
          <button
            type="button"
            onClick={() => (step > 1 ? setStep(step - 1) : onClose())}
            className="px-4 py-2 text-xs font-medium text-[#8E929F] hover:text-white rounded-lg transition-colors whitespace-nowrap shrink-0"
          >
            {step > 1 ? 'Previous Step' : 'Skip Setup'}
          </button>

          <div className="flex items-center gap-3">
            {step < 4 ? (
              <button
                type="button"
                onClick={() => setStep(step + 1)}
                className="px-4 py-2 text-xs font-semibold bg-[#38BDF8] text-black rounded-lg hover:bg-[#38BDF8]/90 transition-colors whitespace-nowrap shrink-0"
              >
                Continue
              </button>
            ) : (
              <button
                type="button"
                onClick={handleFinish}
                className="px-4 py-2 text-xs font-semibold bg-[#38BDF8] text-black rounded-lg hover:bg-[#38BDF8]/90 transition-colors whitespace-nowrap shrink-0"
              >
                Open SlothLlama Chat
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
