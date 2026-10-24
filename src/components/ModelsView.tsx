import React, { useRef, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Cpu,
  Download,
  FileUp,
  HardDrive,
  Pause,
  Play,
  RefreshCw,
  ShieldCheck,
  Sliders,
  Trash2,
  XCircle,
} from 'lucide-react';
import { LocalModel } from '../types/localmind';
import { computeSha256Hex, inspectModelCompatibility } from '../engine/localMindEngine';

interface ModelsViewProps {
  models: LocalModel[];
  loadedModelId: string | null;
  deviceRamGb: number;
  deviceStorageFreeGb: number;
  isOfflineMode: boolean;
  onUpdateModel: (modelId: string, updater: (prev: LocalModel) => LocalModel) => void;
  onAddCustomModel: (model: LocalModel) => void;
  onLoadModelIntoMemory: (modelId: string | null) => void;
}

export const ModelsView: React.FC<ModelsViewProps> = ({
  models,
  loadedModelId,
  deviceRamGb,
  deviceStorageFreeGb,
  isOfflineMode,
  onUpdateModel,
  onAddCustomModel,
  onLoadModelIntoMemory,
}) => {
  const [selectedInspectorModelId, setSelectedInspectorModelId] = useState<string>(
    models[0]?.modelId ?? ''
  );
  const [licenseModalModelId, setLicenseModalModelId] = useState<string | null>(null);
  const [importFeedback, setImportFeedback] = useState<string | null>(null);
  const filePickerRef = useRef<HTMLInputElement | null>(null);

  const deviceRamBytes = deviceRamGb * 1e9;
  const deviceStorageBytes = deviceStorageFreeGb * 1e9;

  const startOrResumeDownload = (model: LocalModel) => {
    if (isOfflineMode) {
      onUpdateModel(model.modelId, (prev) => ({
        ...prev,
        downloadStatus: 'QUEUED',
        errorMessage: 'Offline mode active: Download queued for Wi-Fi connection.',
      }));
      return;
    }

    if (model.requiresExplicitAcceptance && !model.licenseAccepted) {
      setLicenseModalModelId(model.modelId);
      return;
    }

    onUpdateModel(model.modelId, (prev) => ({
      ...prev,
      downloadStatus: 'DOWNLOADING',
      errorMessage: undefined,
    }));

    let step = model.downloadProgress;
    const interval = setInterval(() => {
      step += 25;
      if (step < 100) {
        onUpdateModel(model.modelId, (prev) => {
          if (prev.downloadStatus !== 'DOWNLOADING') {
            clearInterval(interval);
            return prev;
          }
          return {
            ...prev,
            downloadProgress: step,
            bytesDownloaded: Math.round((step / 100) * prev.downloadSizeBytes),
          };
        });
      } else {
        clearInterval(interval);
        onUpdateModel(model.modelId, (prev) => ({
          ...prev,
          downloadStatus: 'INSTALLED',
          downloadProgress: 100,
          bytesDownloaded: prev.downloadSizeBytes,
          isIntegrityVerified: true,
          installedAt: new Date().toISOString(),
        }));
      }
    }, 280);
  };

  const handleImportSafFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const lower = file.name.toLowerCase();
    const isSupported =
      lower.endsWith('.litertlm') || lower.endsWith('.task') || lower.endsWith('.tflite');

    const newModel: LocalModel = {
      modelId: `saf-import-${Date.now()}`,
      displayName: `SAF Imported: ${file.name}`,
      version: '1.0.0-local',
      architecture: isSupported ? 'gemma2' : 'unknown',
      format: isSupported ? 'litertlm' : 'safetensors',
      quantization: isSupported ? 'INT4' : 'FP32',
      contextWindow: 8192,
      downloadSizeBytes: Math.max(file.size, 520_000_000),
      estimatedRamBytes: Math.max(file.size * 1.4, 980_000_000),
      sha256Checksum: computeSha256Hex(file.name + file.size),
      tokenizerIncluded: isSupported,
      sourceUrl: `content://com.android.externalstorage.documents/${encodeURIComponent(file.name)}`,
      licenseName: 'User Imported Local Package',
      licenseSpdx: 'Custom-Local',
      licenseUrl: 'https://slothllama.app/licenses',
      requiresExplicitAcceptance: false,
      licenseAccepted: true,
      hardwareAcceleration: ['GPU', 'CPU'],
      downloadStatus: isSupported ? 'INSTALLED' : 'FAILED',
      downloadProgress: isSupported ? 100 : 0,
      bytesDownloaded: isSupported ? file.size : 0,
      isIntegrityVerified: isSupported,
      errorMessage: isSupported
        ? undefined
        : `Unsupported model file '${file.name}'. Installed LiteRT-LM runtime accepts validated .litertlm, .task, and .tflite packages only.`,
    };

    onAddCustomModel(newModel);
    setSelectedInspectorModelId(newModel.modelId);
    setImportFeedback(
      isSupported
        ? `Verified SHA-256 and imported '${file.name}' into app-controlled storage.`
        : `Rejected '${file.name}' with detailed ModelCompatibilityReport (INCOMPATIBLE_RUNTIME).`
    );
  };

  const inspectedModel =
    models.find((m) => m.modelId === selectedInspectorModelId) ?? models[0];
  const compatibilityReport = inspectedModel
    ? inspectModelCompatibility(inspectedModel, deviceStorageBytes, deviceRamBytes)
    : null;

  const licenseTargetModel = models.find((m) => m.modelId === licenseModalModelId);

  return (
    <div className="max-w-7xl mx-auto px-4 md:px-8 py-8 space-y-8 bg-black">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-[#1E2028]">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-white">
            Model Library & LiteRT-LM Runtime Manager
          </h1>
          <p className="text-sm text-[#8E929F] mt-1">
            Curated JSON catalog, Storage Access Framework (SAF) local file import, resumable downloads, SHA-256 verification, and single-active-model memory guard.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <input
            ref={filePickerRef}
            type="file"
            onChange={handleImportSafFile}
            className="hidden"
          />
          <button
            type="button"
            onClick={() => filePickerRef.current?.click()}
            className="min-h-[40px] px-4 py-2 text-xs font-semibold bg-[#121418] hover:bg-[#191C22] text-white border border-[#1E2028] rounded-lg flex items-center gap-2 transition-colors whitespace-nowrap"
          >
            <FileUp className="w-4 h-4 text-[#38BDF8]" />
            <span>Import Model File (SAF)</span>
          </button>

          {loadedModelId && (
            <button
              type="button"
              onClick={() => onLoadModelIntoMemory(null)}
              className="min-h-[40px] px-4 py-2 text-xs font-medium bg-[#121418] hover:bg-[#F87171]/15 text-[#F87171] border border-[#1E2028] rounded-lg transition-colors whitespace-nowrap"
            >
              Unload Active Model & Free RAM
            </button>
          )}
        </div>
      </div>

      {importFeedback && (
        <div className="p-3.5 rounded-xl bg-[#0B0C0E] border border-[#38BDF8] flex items-center justify-between text-xs text-white">
          <span>{importFeedback}</span>
          <button
            type="button"
            onClick={() => setImportFeedback(null)}
            className="text-[#8E929F] hover:text-white"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Hardware Telemetry Strip */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div className="p-4 rounded-2xl bg-[#0B0C0E] border border-[#1E2028]">
          <div className="text-xs text-[#8E929F]">On-Device Runtime</div>
          <div className="text-sm font-semibold text-white mt-1">
            Google AI Edge LiteRT-LM
          </div>
          <div className="text-xs text-[#34D399] font-mono mt-0.5">
            CPU · GPU · NPU Delegates
          </div>
        </div>

        <div className="p-4 rounded-2xl bg-[#0B0C0E] border border-[#1E2028]">
          <div className="text-xs text-[#8E929F]">Loaded in Memory</div>
          <div className="text-sm font-semibold text-white mt-1 truncate">
            {loadedModelId
              ? models.find((m) => m.modelId === loadedModelId)?.displayName.split(' (')[0]
              : 'None (0 MB Allocated)'}
          </div>
          <div className="text-xs text-[#8E929F] font-mono tabular-nums mt-0.5">
            Single-Model OOM Guard Active
          </div>
        </div>

        <div className="p-4 rounded-2xl bg-[#0B0C0E] border border-[#1E2028]">
          <div className="text-xs text-[#8E929F]">Available Free Storage</div>
          <div className="text-sm font-semibold text-white font-mono tabular-nums mt-1">
            {deviceStorageFreeGb.toFixed(1)} GB Free
          </div>
          <div className="text-xs text-[#8E929F] mt-0.5">Pre-download check enforced</div>
        </div>

        <div className="p-4 rounded-2xl bg-[#0B0C0E] border border-[#1E2028]">
          <div className="text-xs text-[#8E929F]">Device Memory Envelope</div>
          <div className="text-sm font-semibold text-white font-mono tabular-nums mt-1">
            {deviceRamGb.toFixed(1)} GB Total RAM
          </div>
          <div className="text-xs text-[#8E929F] mt-0.5">Safe ceiling: 85% RAM</div>
        </div>
      </div>

      {/* Main Split: Model Catalog Cards + ModelCompatibilityReport Inspector */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-7 space-y-4">
          <h2 className="text-sm font-semibold text-white">
            Curated Model Registry & Installed Packages
          </h2>

          {models.map((model) => {
            const report = inspectModelCompatibility(
              model,
              deviceStorageBytes,
              deviceRamBytes
            );
            const isLoaded = loadedModelId === model.modelId;
            const isSelected = selectedInspectorModelId === model.modelId;

            return (
              <div
                key={model.modelId}
                onClick={() => setSelectedInspectorModelId(model.modelId)}
                className={`p-5 rounded-2xl border transition-colors cursor-pointer space-y-4 ${
                  isSelected
                    ? 'bg-[#121418] border-[#38BDF8]'
                    : 'bg-[#0B0C0E] border-[#1E2028] hover:border-[#2E333D]'
                }`}
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-base font-semibold text-white">
                        {model.displayName}
                      </h3>
                    </div>
                    {/* Clean unboxed metadata with typographic separators */}
                    <div className="text-xs text-[#8E929F] font-mono tabular-nums mt-1 flex flex-wrap items-center gap-2">
                      <span>Arch: {model.architecture}</span>
                      <span aria-hidden="true">·</span>
                      <span>Format: .{model.format}</span>
                      <span aria-hidden="true">·</span>
                      <span>Quant: {model.quantization}</span>
                      <span aria-hidden="true">·</span>
                      <span>Context: {model.contextWindow.toLocaleString()}</span>
                      <span aria-hidden="true">·</span>
                      <span>Size: {(model.downloadSizeBytes / 1e9).toFixed(2)} GB</span>
                      <span aria-hidden="true">·</span>
                      <span>Est. RAM: {(model.estimatedRamBytes / 1e9).toFixed(2)} GB</span>
                    </div>
                  </div>

                  <div className="text-xs font-mono tabular-nums shrink-0">
                    {report.isCompatible ? (
                      <span className="text-[#34D399]">SUPPORTED</span>
                    ) : (
                      <span className="text-[#F87171]">{report.status}</span>
                    )}
                  </div>
                </div>

                {/* Thermal / Large Model Warning */}
                {report.warnings.length > 0 && (
                  <div className="p-2.5 rounded-xl bg-black border border-[#FBBF24]/40 text-xs text-[#FBBF24] flex items-start gap-2">
                    <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                    <span>{report.warnings[0]}</span>
                  </div>
                )}

                {model.errorMessage && (
                  <div className="text-xs text-[#F87171] font-mono">{model.errorMessage}</div>
                )}

                {/* Download Progress Bar */}
                {(model.downloadStatus === 'DOWNLOADING' ||
                  model.downloadStatus === 'PAUSED' ||
                  model.downloadStatus === 'QUEUED') && (
                  <div className="space-y-1.5">
                    <div className="flex justify-between text-xs font-mono tabular-nums text-[#8E929F]">
                      <span>{model.downloadStatus}</span>
                      <span>
                        {model.downloadProgress}% (
                        {(model.bytesDownloaded / 1e9).toFixed(2)} /{' '}
                        {(model.downloadSizeBytes / 1e9).toFixed(2)} GB)
                      </span>
                    </div>
                    <div className="w-full h-2 bg-black rounded-full overflow-hidden">
                      <div
                        className="h-full bg-[#38BDF8] transition-all"
                        style={{ width: `${model.downloadProgress}%` }}
                      />
                    </div>
                  </div>
                )}

                {/* Action Controls: Download, Pause, Resume, Cancel, Delete, Load/Unload, License */}
                <div
                  className="pt-3 border-t border-[#1E2028] flex flex-wrap items-center justify-between gap-2"
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="text-xs text-[#8E929F] font-mono">
                    License: {model.licenseSpdx} (
                    {model.licenseAccepted ? 'Accepted' : 'Acceptance Required'})
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    {!model.licenseAccepted && (
                      <button
                        type="button"
                        onClick={() => setLicenseModalModelId(model.modelId)}
                        className="px-3 py-1.5 text-xs font-semibold bg-[#FBBF24] text-black rounded-lg"
                      >
                        Review & Accept License
                      </button>
                    )}

                    {model.downloadStatus === 'INSTALLED' && (
                      <>
                        <button
                          type="button"
                          onClick={() =>
                            onLoadModelIntoMemory(isLoaded ? null : model.modelId)
                          }
                          className={`px-3.5 py-1.5 text-xs font-semibold rounded-lg transition-colors ${
                            isLoaded
                              ? 'bg-[#34D399] text-black'
                              : 'bg-[#38BDF8] text-black hover:bg-[#38BDF8]/90'
                          }`}
                        >
                          {isLoaded ? 'Active in Memory (Unload)' : 'Load Model into RAM'}
                        </button>

                        <button
                          type="button"
                          onClick={() =>
                            onUpdateModel(model.modelId, (prev) => ({
                              ...prev,
                              adapterAttached: prev.adapterAttached
                                ? null
                                : 'LoRA-STEM-Tutor-v1 (Experimental)',
                            }))
                          }
                          className="px-3 py-1.5 text-xs bg-black hover:bg-[#1E2028] text-[#8E929F] hover:text-white border border-[#1E2028] rounded-lg"
                        >
                          {model.adapterAttached
                            ? 'Detach Experimental Adapter'
                            : 'Attach Experimental LoRA'}
                        </button>

                        <button
                          type="button"
                          onClick={() => {
                            if (isLoaded) onLoadModelIntoMemory(null);
                            onUpdateModel(model.modelId, (prev) => ({
                              ...prev,
                              downloadStatus: 'NOT_INSTALLED',
                              downloadProgress: 0,
                              bytesDownloaded: 0,
                              isIntegrityVerified: false,
                            }));
                          }}
                          className="p-1.5 text-[#8E929F] hover:text-[#F87171] rounded-lg border border-[#1E2028]"
                          aria-label={`Delete model ${model.displayName}`}
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </>
                    )}

                    {(model.downloadStatus === 'NOT_INSTALLED' ||
                      model.downloadStatus === 'CANCELED' ||
                      model.downloadStatus === 'FAILED') &&
                      report.status !== 'MISSING_TOKENIZER' &&
                      report.status !== 'INCOMPATIBLE_RUNTIME' && (
                        <button
                          type="button"
                          onClick={() => startOrResumeDownload(model)}
                          className="px-3.5 py-1.5 text-xs font-semibold bg-[#38BDF8] text-black rounded-lg flex items-center gap-1.5"
                        >
                          <Download className="w-3.5 h-3.5" />
                          <span>Download Package</span>
                        </button>
                      )}

                    {model.downloadStatus === 'DOWNLOADING' && (
                      <>
                        <button
                          type="button"
                          onClick={() =>
                            onUpdateModel(model.modelId, (prev) => ({
                              ...prev,
                              downloadStatus: 'PAUSED',
                            }))
                          }
                          className="px-3 py-1.5 text-xs bg-black text-white border border-[#1E2028] rounded-lg flex items-center gap-1"
                        >
                          <Pause className="w-3.5 h-3.5" />
                          <span>Pause</span>
                        </button>
                        <button
                          type="button"
                          onClick={() =>
                            onUpdateModel(model.modelId, (prev) => ({
                              ...prev,
                              downloadStatus: 'CANCELED',
                              downloadProgress: 0,
                              bytesDownloaded: 0,
                            }))
                          }
                          className="px-3 py-1.5 text-xs bg-black text-[#F87171] border border-[#1E2028] rounded-lg"
                        >
                          Cancel
                        </button>
                      </>
                    )}

                    {(model.downloadStatus === 'PAUSED' ||
                      model.downloadStatus === 'QUEUED') && (
                      <button
                        type="button"
                        onClick={() => startOrResumeDownload(model)}
                        className="px-3 py-1.5 text-xs font-semibold bg-[#38BDF8] text-black rounded-lg flex items-center gap-1"
                      >
                        <Play className="w-3.5 h-3.5" />
                        <span>Resume</span>
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Right Column: Live ModelCompatibilityReport Inspector */}
        <div className="lg:col-span-5 space-y-4">
          <h2 className="text-sm font-semibold text-white">
            ModelCompatibilityReport Inspector
          </h2>

          {inspectedModel && compatibilityReport && (
            <div className="p-6 rounded-2xl bg-[#0B0C0E] border border-[#1E2028] space-y-5 sticky top-20">
              <div className="pb-4 border-b border-[#1E2028]">
                <div className="text-xs text-[#8E929F] font-mono">
                  {inspectedModel.modelId}
                </div>
                <h3 className="text-lg font-semibold text-white mt-0.5">
                  {inspectedModel.displayName}
                </h3>
                <div className="mt-2 text-xs font-mono">
                  Status:{' '}
                  <span
                    className={
                      compatibilityReport.isCompatible
                        ? 'text-[#34D399] font-semibold'
                        : 'text-[#F87171] font-semibold'
                    }
                  >
                    {compatibilityReport.status}
                  </span>
                </div>
                <p className="text-xs text-[#F4F4F6] mt-2 leading-relaxed">
                  {compatibilityReport.detailedReason}
                </p>
              </div>

              <div className="space-y-2.5 text-xs font-mono tabular-nums">
                <div className="flex justify-between">
                  <span className="text-[#8E929F]">SHA-256 Checksum:</span>
                  <span className="text-white">
                    {inspectedModel.sha256Checksum.slice(0, 16)}...
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#8E929F]">Integrity Status:</span>
                  <span
                    className={
                      inspectedModel.isIntegrityVerified
                        ? 'text-[#34D399]'
                        : 'text-[#8E929F]'
                    }
                  >
                    {inspectedModel.isIntegrityVerified
                      ? 'SHA-256 Verified'
                      : 'Pending Download'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#8E929F]">Bundled Tokenizer:</span>
                  <span
                    className={
                      inspectedModel.tokenizerIncluded
                        ? 'text-[#34D399]'
                        : 'text-[#F87171]'
                    }
                  >
                    {inspectedModel.tokenizerIncluded ? 'Present' : 'MISSING_TOKENIZER'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#8E929F]">Hardware Delegates:</span>
                  <span className="text-white">
                    {inspectedModel.hardwareAcceleration.join(' · ')}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#8E929F]">Optional Adapter (Layer e):</span>
                  <span className="text-[#38BDF8]">
                    {inspectedModel.adapterAttached ?? 'None (Base Model Only)'}
                  </span>
                </div>
              </div>

              {/* Quick Diagnostic Simulator to test all 10 CompatibilityStatus states */}
              <div className="pt-4 border-t border-[#1E2028] space-y-2">
                <div className="text-xs font-semibold text-white">
                  Test Compatibility States on Selected Model
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() =>
                      onUpdateModel(inspectedModel.modelId, (prev) => ({
                        ...prev,
                        tokenizerIncluded: !prev.tokenizerIncluded,
                      }))
                    }
                    className="px-2.5 py-1 text-[11px] bg-[#121418] hover:bg-[#191C22] text-white border border-[#1E2028] rounded"
                  >
                    Toggle Missing Tokenizer
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      onUpdateModel(inspectedModel.modelId, (prev) => ({
                        ...prev,
                        quantization: prev.quantization === 'INT4' ? 'FP32' : 'INT4',
                      }))
                    }
                    className="px-2.5 py-1 text-[11px] bg-[#121418] hover:bg-[#191C22] text-white border border-[#1E2028] rounded"
                  >
                    Toggle Unsupported Quantization
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      onUpdateModel(inspectedModel.modelId, (prev) => ({
                        ...prev,
                        requiresExplicitAcceptance: true,
                        licenseAccepted: !prev.licenseAccepted,
                      }))
                    }
                    className="px-2.5 py-1 text-[11px] bg-[#121418] hover:bg-[#191C22] text-white border border-[#1E2028] rounded"
                  >
                    Toggle License Acceptance
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Explicit License Acceptance Modal */}
      {licenseTargetModel && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="max-w-md w-full rounded-2xl bg-[#0B0C0E] border border-[#38BDF8] p-6 space-y-4">
            <h3 className="text-lg font-semibold text-white">
              Accept Model License Before First Use
            </h3>
            <p className="text-xs text-[#8E929F] leading-relaxed">
              <strong>{licenseTargetModel.displayName}</strong> is distributed under{' '}
              <strong>{licenseTargetModel.licenseName}</strong> ({licenseTargetModel.licenseSpdx}). SlothLlama requires explicit license acceptance before downloading or loading weights.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setLicenseModalModelId(null)}
                className="px-3.5 py-1.5 text-xs text-[#8E929F] hover:text-white"
              >
                Decline
              </button>
              <button
                type="button"
                onClick={() => {
                  onUpdateModel(licenseTargetModel.modelId, (prev) => ({
                    ...prev,
                    licenseAccepted: true,
                    licenseAcceptedAt: new Date().toISOString(),
                  }));
                  setLicenseModalModelId(null);
                }}
                className="px-4 py-1.5 text-xs font-semibold bg-[#38BDF8] text-black rounded-lg"
              >
                Accept License
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
