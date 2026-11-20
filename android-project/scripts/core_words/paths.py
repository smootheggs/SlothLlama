"""Where the build finds things. CORE_SRC is a directory holding the unpacked sources:
  wordfreq/   (wordfreq 3.1.1 wheel: data/large_fr.msgpack.gz, data/large_zh.msgpack.gz)
  hkcancor/   (from the pycantonese 5.0.0 wheel: pycantonese/data/hkcancor/*.cha)
  cedict.txt.gz (CC-CEDICT, as shipped in the pycccedict 1.2.0 wheel)
  complete.json (complete-hsk-vocabulary)
They are read as data only; nothing from them is imported or run."""
import os
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.environ.get("CORE_SRC", os.path.join(HERE, "sources"))
WF_FR = os.path.join(SRC, "wordfreq", "large_fr.msgpack.gz")
WF_ZH = os.path.join(SRC, "wordfreq", "large_zh.msgpack.gz")
HKCANCOR = os.path.join(SRC, "hkcancor")
CEDICT = os.path.join(SRC, "cedict.txt.gz")
HSK = os.path.join(SRC, "complete.json")
