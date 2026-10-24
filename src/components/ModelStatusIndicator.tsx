import React, { useState, useRef, useEffect } from 'react';
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Cpu,
  HardDrive,
  Layers,
  Power,
  RefreshCw,
  ShieldCheck,
  Sliders,
  X,
  ChevronDown,
} from 'lucide-react';
import { LocalModel } from '../types/localmind';

interface ModelStatusIndicatorProps {
  models: LocalModel[];
  loadedModelId: string | null;
  onSelectLoadedModel: (modelId: string | null) => void;
  onNavigateToModels: () => void;
  isStreaming?: boolean;
}

export const ModelStatusIndicator: React.FC<ModelStatusIndicatorProps> = ({
  models,
  loadedModelId,
  onSelectLoadedModel,
  onNavigateToModels,
  isStreaming = false,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const activeModel = models.find((m) => m.modelId === loadedModelId) ?? null;
  const isDownloading = models.some((m) => m.downloadStatus === 'DOWNLOADING');
  const downloadingModel = models.find((m) => m.downloadStatus === 'DOWNLOADING');

  // Close popover when clicking outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  let statusLabel = 'Standby (RAM Free)';
  let dotColor = 'bg-[#64748B]';
  let badgeColor = 'text-[#8E929F] border-[#1E2028]';

  if (isStreaming) {
    statusLabel = 'Generating Tokens...';
    dotColor = 'bg-[#38BDF8] animate-ping';
    badgeColor = 'text-[#38BDF8] border-[#38BDF8]/40 bg-[#38BDF8]/10';
  } else if (isDownloading && downloadingModel) {
    statusLabel = `Downloading ${downloadingModel.downloadProgress}%`;
    dotColor = 'bg-[#FBBF24] animate-pulse';
    badgeColor = 'text-[#FBBF24] border-[#FBBF24]/40 bg-[#FBBF24]/10';
  } else if (activeModel) {
    statusLabel = 'Loaded in RAM';
    dotColor = 'bg-[#34D399] animate-pulse';
    badgeColor = 'text-[#34D399] border-[#34D399]/40 bg-[#34D399]/10';
  }

  return (
    <div className="relative" ref={containerRef}>
      {/* Trigger Button */}
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        className={`min-h-[40px] px-2.5 sm:px-3 py-1.5 rounded-xl border flex items-center gap-2 transition-all text-xs font-mono tabular-nums shadow-sm hover:border-[#38BDF8]/50 ${badgeColor} bg-[#121418] hover:bg-[#181B22] cursor-pointer shrink-0`}
        aria-label="Model runtime status details"
        title="Click to view local model status and memory allocations"
      >
        <span className="relative flex h-2 w-2 shrink-0">
          <span className={`absolute inline-flex h-full w-full rounded-full opacity-75 ${dotColor}`} />
          <span className={`relative inline-flex rounded-full h-2 w-2 ${dotColor.replace(' animate-pulse', '').replace(' animate-ping', '')}`} />
        </span>

        <div className="flex items-center gap-1.5 truncate max-w-[85px] xs:max-w-[110px] sm:max-w-[130px] md:max-w-[135px] lg:max-w-[180px] xl:max-w-[240px]">
          <span className="font-semibold text-white truncate">
            {activeModel ? activeModel.displayName.split(' (')[0] : 'No Model'}
          </span>
          <span className="text-[#8E929F] hidden xl:inline">·</span>
          <span className="hidden xl:inline text-[11px]">{statusLabel}</span>
        </div>

        <ChevronDown className={`w-3.5 h-3.5 transition-transform duration-200 text-[#8E929F] shrink-0 ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {/* Popover Dropdown Details - Responsive alignment */}
      {isOpen && (
        <div className="absolute left-0 mt-2 w-[320px] sm:w-[380px] max-w-[calc(100vw-1.5rem)] rounded-2xl bg-[#0B0C0E] border border-[#2E333D] shadow-2xl p-4 space-y-4 z-50 text-xs text-[#F4F4F6] animate-in fade-in slide-in-from-top-2 duration-150">
          <div className="flex items-center justify-between border-b border-[#1E2028] pb-3">
            <div className="flex items-center gap-2">
              <Cpu className="w-4 h-4 text-[#38BDF8]" />
              <span className="font-semibold text-sm text-white">Local Inference Engine</span>
            </div>
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              className="p-1 text-[#8E929F] hover:text-white rounded"
              aria-label="Close status popover"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {activeModel ? (
            <div className="space-y-3">
              <div>
                <div className="text-sm font-semibold text-white">{activeModel.displayName}</div>
                <div className="text-[11px] text-[#8E929F] font-mono mt-0.5">
                  ID: {activeModel.modelId} · Format: .{activeModel.format}
                </div>
              </div>

              {/* Hardware Telemetry Grid */}
              <div className="grid grid-cols-2 gap-2 pt-1 font-mono text-[11px] tabular-nums">
                <div className="p-2.5 rounded-xl bg-[#121418] border border-[#1E2028]">
                  <div className="text-[#8E929F]">Allocated RAM</div>
                  <div className="text-white font-semibold mt-0.5">
                    {(activeModel.estimatedRamBytes / 1e9).toFixed(2)} GB
                  </div>
                  <div className="text-[#34D399] text-[10px] mt-0.5">Single-Model Guard</div>
                </div>

                <div className="p-2.5 rounded-xl bg-[#121418] border border-[#1E2028]">
                  <div className="text-[#8E929F]">Quantization</div>
                  <div className="text-white font-semibold mt-0.5">{activeModel.quantization}</div>
                  <div className="text-[#38BDF8] text-[10px] mt-0.5">Weight compression</div>
                </div>

                <div className="p-2.5 rounded-xl bg-[#121418] border border-[#1E2028]">
                  <div className="text-[#8E929F]">Context Window</div>
                  <div className="text-white font-semibold mt-0.5">
                    {activeModel.contextWindow.toLocaleString()} tokens
                  </div>
                  <div className="text-[#8E929F] text-[10px] mt-0.5">Attention budget</div>
                </div>

                <div className="p-2.5 rounded-xl bg-[#121418] border border-[#1E2028]">
                  <div className="text-[#8E929F]">Delegates</div>
                  <div className="text-white font-semibold mt-0.5">
                    {activeModel.hardwareAcceleration.join(' · ')}
                  </div>
                  <div className="text-[#34D399] text-[10px] mt-0.5">GPU / CPU / NPU</div>
                </div>
              </div>

              {/* Security & Integrity Check */}
              <div className="p-2.5 rounded-xl bg-[#121418] border border-[#1E2028] space-y-1">
                <div className="flex items-center justify-between text-[11px]">
                  <span className="flex items-center gap-1.5 text-[#34D399]">
                    <ShieldCheck className="w-3.5 h-3.5" />
                    <span>Safe FlatBuffer Verified</span>
                  </span>
                  <span className="text-[#8E929F] font-mono">SHA-256</span>
                </div>
                <div className="text-[10px] font-mono text-[#8E929F] truncate">
                  {activeModel.sha256Checksum}
                </div>
              </div>

              {/* Quick Actions */}
              <div className="flex items-center gap-2 pt-2 border-t border-[#1E2028]">
                <button
                  type="button"
                  onClick={() => {
                    onSelectLoadedModel(null);
                    setIsOpen(false);
                  }}
                  className="flex-1 py-2 text-xs font-medium text-[#F87171] hover:bg-[#F87171]/10 border border-[#1E2028] rounded-xl transition-colors flex items-center justify-center gap-1.5"
                >
                  <Power className="w-3.5 h-3.5" />
                  <span>Unload & Free RAM</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setIsOpen(false);
                    onNavigateToModels();
                  }}
                  className="flex-1 py-2 text-xs font-semibold bg-[#38BDF8] text-black hover:bg-[#38BDF8]/90 rounded-xl transition-colors flex items-center justify-center gap-1.5"
                >
                  <Sliders className="w-3.5 h-3.5" />
                  <span>Model Library</span>
                </button>
              </div>
            </div>
          ) : (
            <div className="space-y-3 py-2 text-center">
              <div className="p-3 rounded-full bg-[#121418] w-12 h-12 mx-auto flex items-center justify-center text-[#8E929F]">
                <HardDrive className="w-6 h-6" />
              </div>
              <div className="space-y-1">
                <div className="font-semibold text-white">No Model Loaded in Memory</div>
                <p className="text-xs text-[#8E929F]">
                  Device RAM is completely free (0 MB allocated). Select a model package from the library to enable on-device inference.
                </p>
              </div>
              <button
                type="button"
                onClick={() => {
                  setIsOpen(false);
                  onNavigateToModels();
                }}
                className="w-full py-2.5 text-xs font-semibold bg-[#38BDF8] text-black rounded-xl hover:bg-[#38BDF8]/90 transition-colors"
              >
                Open Model Library & Load Model
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
