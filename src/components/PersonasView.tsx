import React, { useState } from 'react';
import {
  Archive,
  Check,
  Copy,
  Download,
  Edit3,
  Play,
  Plus,
  Trash2,
  Upload,
} from 'lucide-react';
import {
  ContextWindowStrategy,
  KnowledgeCollection,
  LocalModel,
  Persona,
} from '../types/localmind';

interface PersonasViewProps {
  personas: Persona[];
  activePersonaId: string;
  models: LocalModel[];
  collections: KnowledgeCollection[];
  onSelectPersonaForChat: (personaId: string) => void;
  onSavePersona: (persona: Persona) => void;
  onDeletePersona: (personaId: string) => void;
}

export const PersonasView: React.FC<PersonasViewProps> = ({
  personas,
  activePersonaId,
  models,
  collections,
  onSelectPersonaForChat,
  onSavePersona,
  onDeletePersona,
}) => {
  const [editingPersona, setEditingPersona] = useState<Persona | null>(null);
  const [showArchived, setShowArchived] = useState(false);

  const installedModels = models.filter((m) => m.downloadStatus === 'INSTALLED');
  const visiblePersonas = personas.filter((p) => (showArchived ? p.isArchived : !p.isArchived));

  const handleStartCreateSpecialist = () => {
    const defaultModelId = installedModels[0]?.modelId ?? models[0].modelId;
    setEditingPersona({
      id: `persona-custom-${Date.now()}`,
      name: 'Biomedical Research Specialist',
      description: 'Analyzes locally imported clinical and biochemical documents with strict citation hygiene.',
      category: 'specialist',
      accentColor: '#38BDF8',
      selectedModelId: defaultModelId,
      systemInstruction:
        'You are a user-created LocalMind Specialist. Base every technical claim on enabled local Knowledge Collections and clearly separate base model inference from retrieved passages.',
      temperature: 0.3,
      maxOutputTokens: 1024,
      contextWindowStrategy: 'ROLLING_SUMMARY_WITH_RAG',
      enabledCollectionIds: collections.slice(0, 2).map((c) => c.id),
      enabledTools: ['DETERMINISTIC_CALCULATOR'],
      memoryEnabled: true,
      isBuiltIn: false,
      isArchived: false,
      createdAt: new Date().toISOString(),
    });
  };

  const handleDuplicate = (persona: Persona) => {
    const copy: Persona = {
      ...persona,
      id: `persona-copy-${Date.now()}`,
      name: `${persona.name} (Custom Copy)`,
      isBuiltIn: false,
      isArchived: false,
      createdAt: new Date().toISOString(),
    };
    onSavePersona(copy);
  };

  const handleExportPersonas = () => {
    const blob = new Blob([JSON.stringify(personas, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'localmind-personas.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  const toggleCollectionInDraft = (collectionId: string) => {
    if (!editingPersona) return;
    const exists = editingPersona.enabledCollectionIds.includes(collectionId);
    setEditingPersona({
      ...editingPersona,
      enabledCollectionIds: exists
        ? editingPersona.enabledCollectionIds.filter((id) => id !== collectionId)
        : [...editingPersona.enabledCollectionIds, collectionId],
    });
  };

  return (
    <div className="max-w-7xl mx-auto px-4 md:px-8 py-8 space-y-8 bg-black">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-[#1E2028]">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-white">
            Persona Library & Specialist Studio
          </h1>
          <p className="text-sm text-[#8E929F] mt-1">
            Each persona references an installed local model ID and selected RAG collections without duplicating model weight files on disk.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={() => setShowArchived((v) => !v)}
            className="min-h-[40px] px-3.5 py-2 text-xs font-medium bg-[#0B0C0E] hover:bg-[#121418] text-[#8E929F] hover:text-white border border-[#1E2028] rounded-lg transition-colors whitespace-nowrap"
          >
            {showArchived ? 'Showing Archived' : 'Show Active Personas'}
          </button>

          <button
            type="button"
            onClick={handleExportPersonas}
            className="min-h-[40px] px-3.5 py-2 text-xs font-medium bg-[#0B0C0E] hover:bg-[#121418] text-white border border-[#1E2028] rounded-lg flex items-center gap-1.5 transition-colors whitespace-nowrap"
          >
            <Download className="w-3.5 h-3.5 text-[#38BDF8]" />
            <span>Export JSON</span>
          </button>

          <button
            type="button"
            onClick={handleStartCreateSpecialist}
            className="min-h-[40px] px-4 py-2 text-xs font-semibold bg-[#38BDF8] text-black rounded-lg hover:bg-[#38BDF8]/90 flex items-center gap-1.5 transition-colors whitespace-nowrap"
          >
            <Plus className="w-4 h-4" />
            <span>Create Specialist Persona</span>
          </button>
        </div>
      </div>

      {/* Persona Editor Drawer / Panel */}
      {editingPersona && (
        <div className="p-6 rounded-2xl bg-[#0B0C0E] border border-[#38BDF8] space-y-5">
          <div className="flex items-center justify-between border-b border-[#1E2028] pb-4">
            <div>
              <h2 className="text-lg font-semibold text-white">
                {editingPersona.isBuiltIn ? `Configure ${editingPersona.name}` : 'Edit Persona Definition'}
              </h2>
              <p className="text-xs text-[#8E929F]">
                Changes apply immediately to prompt assembly and context-window budgeting.
              </p>
            </div>
            <button
              type="button"
              onClick={() => setEditingPersona(null)}
              className="text-xs text-[#8E929F] hover:text-white"
            >
              Cancel
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs text-[#8E929F] mb-1">Persona Name</label>
              <input
                type="text"
                value={editingPersona.name}
                onChange={(e) => setEditingPersona({ ...editingPersona, name: e.target.value })}
                className="w-full bg-[#121418] text-white text-sm px-3 py-2 rounded-lg border border-[#1E2028]"
              />
            </div>

            <div>
              <label className="block text-xs text-[#8E929F] mb-1">
                Referenced Local Model (Zero File Duplication)
              </label>
              <select
                value={editingPersona.selectedModelId}
                onChange={(e) =>
                  setEditingPersona({ ...editingPersona, selectedModelId: e.target.value })
                }
                className="w-full bg-[#121418] text-white text-sm px-3 py-2 rounded-lg border border-[#1E2028] font-mono"
              >
                {models.map((m) => (
                  <option key={m.modelId} value={m.modelId}>
                    {m.displayName} ({m.downloadStatus})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs text-[#8E929F] mb-1">Context-Window Strategy</label>
              <select
                value={editingPersona.contextWindowStrategy}
                onChange={(e) =>
                  setEditingPersona({
                    ...editingPersona,
                    contextWindowStrategy: e.target.value as ContextWindowStrategy,
                  })
                }
                className="w-full bg-[#121418] text-white text-sm px-3 py-2 rounded-lg border border-[#1E2028] font-mono"
              >
                <option value="ROLLING_SUMMARY_WITH_RAG">ROLLING_SUMMARY_WITH_RAG</option>
                <option value="SLIDING_WINDOW">SLIDING_WINDOW</option>
                <option value="STRICT_LOCAL_ONLY">STRICT_LOCAL_ONLY</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs text-[#8E929F] mb-1">Short Description</label>
            <input
              type="text"
              value={editingPersona.description}
              onChange={(e) =>
                setEditingPersona({ ...editingPersona, description: e.target.value })
              }
              className="w-full bg-[#121418] text-white text-sm px-3 py-2 rounded-lg border border-[#1E2028]"
            />
          </div>

          <div>
            <label className="block text-xs text-[#8E929F] mb-1">
              System Instruction (Protected from Untrusted RAG Documents)
            </label>
            <textarea
              rows={3}
              value={editingPersona.systemInstruction}
              onChange={(e) =>
                setEditingPersona({ ...editingPersona, systemInstruction: e.target.value })
              }
              className="w-full bg-[#121418] text-white text-sm p-3 rounded-lg border border-[#1E2028] font-mono"
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs text-[#8E929F] mb-1 font-mono tabular-nums">
                Temperature ({editingPersona.temperature.toFixed(2)})
              </label>
              <input
                type="range"
                min="0"
                max="1"
                step="0.05"
                value={editingPersona.temperature}
                onChange={(e) =>
                  setEditingPersona({
                    ...editingPersona,
                    temperature: parseFloat(e.target.value),
                  })
                }
                className="w-full accent-[#38BDF8]"
              />
            </div>

            <div>
              <label className="block text-xs text-[#8E929F] mb-1 font-mono tabular-nums">
                Max Output Tokens ({editingPersona.maxOutputTokens})
              </label>
              <input
                type="number"
                min={256}
                max={4096}
                step={128}
                value={editingPersona.maxOutputTokens}
                onChange={(e) =>
                  setEditingPersona({
                    ...editingPersona,
                    maxOutputTokens: Number(e.target.value) || 1024,
                  })
                }
                className="w-full bg-[#121418] text-white text-sm px-3 py-1.5 rounded-lg border border-[#1E2028] font-mono tabular-nums"
              />
            </div>

            <div className="flex items-center pt-5">
              <label className="flex items-center gap-2.5 text-xs text-white cursor-pointer">
                <input
                  type="checkbox"
                  checked={editingPersona.memoryEnabled}
                  onChange={(e) =>
                    setEditingPersona({ ...editingPersona, memoryEnabled: e.target.checked })
                  }
                  className="h-4 w-4 rounded bg-black border-[#2E333D]"
                />
                <span>Include Approved User Memories in Prompt</span>
              </label>
            </div>
          </div>

          {/* Enabled Knowledge Collections */}
          <div className="space-y-2">
            <div className="text-xs text-[#8E929F]">
              Enabled Local Knowledge Collections (RAG Grounding)
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {collections.map((col) => {
                const checked = editingPersona.enabledCollectionIds.includes(col.id);
                return (
                  <button
                    key={col.id}
                    type="button"
                    onClick={() => toggleCollectionInDraft(col.id)}
                    className={`p-3 rounded-xl border text-left flex items-center justify-between transition-colors ${
                      checked
                        ? 'bg-[#121418] border-[#38BDF8] text-white'
                        : 'bg-black border-[#1E2028] text-[#8E929F]'
                    }`}
                  >
                    <div>
                      <div className="text-xs font-semibold text-white">{col.name}</div>
                      <div className="text-[11px] text-[#8E929F]">{col.license}</div>
                    </div>
                    {checked && <Check className="w-4 h-4 text-[#38BDF8]" />}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#1E2028]">
            <button
              type="button"
              onClick={() => setEditingPersona(null)}
              className="px-4 py-2 text-xs text-[#8E929F] hover:text-white"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => {
                onSavePersona(editingPersona);
                setEditingPersona(null);
              }}
              className="px-5 py-2 text-xs font-semibold bg-[#38BDF8] text-black rounded-lg hover:bg-[#38BDF8]/90"
            >
              Save Persona
            </button>
          </div>
        </div>
      )}

      {/* Persona Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {visiblePersonas.map((persona) => {
          const isActive = persona.id === activePersonaId;
          const linkedModel = models.find((m) => m.modelId === persona.selectedModelId);

          return (
            <div
              key={persona.id}
              className={`rounded-2xl bg-[#0B0C0E] border p-5 flex flex-col justify-between gap-4 transition-colors ${
                isActive ? 'border-[#38BDF8]' : 'border-[#1E2028]'
              }`}
            >
              <div className="space-y-3">
                {/* Clean unboxed kicker metadata */}
                <div className="flex items-center justify-between text-xs text-[#8E929F] font-mono tabular-nums">
                  <span>{persona.isBuiltIn ? 'Built-in Persona' : 'User Specialist'}</span>
                  <span>Temp {persona.temperature.toFixed(1)} · {persona.maxOutputTokens} tok</span>
                </div>

                <div>
                  <h3 className="text-lg font-semibold text-white">{persona.name}</h3>
                  <p className="text-xs text-[#8E929F] mt-1 leading-relaxed">
                    {persona.description}
                  </p>
                </div>

                <div className="pt-2 border-t border-[#1E2028] space-y-1.5 text-xs text-[#8E929F]">
                  <div className="flex items-center justify-between">
                    <span>Referenced Model:</span>
                    <span className="text-white font-mono truncate max-w-[175px]">
                      {linkedModel?.displayName.split(' (')[0] ?? persona.selectedModelId}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span>Enabled Knowledge RAG:</span>
                    <span className="text-white font-mono tabular-nums">
                      {persona.enabledCollectionIds.length} collection(s)
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span>Context Strategy:</span>
                    <span className="text-white font-mono">{persona.contextWindowStrategy}</span>
                  </div>
                </div>
              </div>

              <div className="pt-3 border-t border-[#1E2028] flex flex-wrap items-center justify-between gap-2">
                <button
                  type="button"
                  onClick={() => onSelectPersonaForChat(persona.id)}
                  className="min-h-[38px] px-3.5 py-1.5 text-xs font-semibold bg-[#38BDF8] text-black rounded-lg hover:bg-[#38BDF8]/90 flex items-center gap-1.5 whitespace-nowrap"
                >
                  <Play className="w-3.5 h-3.5 fill-current" />
                  <span>Chat with Persona</span>
                </button>

                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => setEditingPersona(persona)}
                    className="min-h-[38px] min-w-[38px] flex items-center justify-center text-[#8E929F] hover:text-white rounded-lg border border-[#1E2028]"
                    aria-label={`Edit ${persona.name}`}
                    title="Edit Persona"
                  >
                    <Edit3 className="w-3.5 h-3.5" />
                  </button>

                  <button
                    type="button"
                    onClick={() => handleDuplicate(persona)}
                    className="min-h-[38px] min-w-[38px] flex items-center justify-center text-[#8E929F] hover:text-white rounded-lg border border-[#1E2028]"
                    aria-label={`Duplicate ${persona.name}`}
                    title="Duplicate Persona"
                  >
                    <Copy className="w-3.5 h-3.5" />
                  </button>

                  <button
                    type="button"
                    onClick={() =>
                      onSavePersona({ ...persona, isArchived: !persona.isArchived })
                    }
                    className="min-h-[38px] min-w-[38px] flex items-center justify-center text-[#8E929F] hover:text-white rounded-lg border border-[#1E2028]"
                    aria-label={`Archive ${persona.name}`}
                    title={persona.isArchived ? 'Restore Persona' : 'Archive Persona'}
                  >
                    <Archive className="w-3.5 h-3.5" />
                  </button>

                  {!persona.isBuiltIn && (
                    <button
                      type="button"
                      onClick={() => onDeletePersona(persona.id)}
                      className="min-h-[38px] min-w-[38px] flex items-center justify-center text-[#8E929F] hover:text-[#F87171] rounded-lg border border-[#1E2028]"
                      aria-label={`Delete ${persona.name}`}
                      title="Delete Custom Persona"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
