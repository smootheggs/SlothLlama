# Core words: sources

`build.py` turns these into `app/src/main/assets/core/{fr,zh,yue}.json`. The JSON is committed,
so building the app never needs the sources; rebuild only to change the lists.

| Source | Version | Used for | Licence |
| --- | --- | --- | --- |
| [wordfreq](https://github.com/rspeer/wordfreq) | 3.1.1 wheel, sha256 `4b1c6ecffc6198be3396d5cf871c4423ca71c907c231348d352dd54d62b97473` | French ranking; each word's share of text (French, Mandarin) | data CC BY-SA 4.0 |
| [complete-hsk-vocabulary](https://github.com/drkameleon/complete-hsk-vocabulary) | commit `7ac65bf1a6387d35f1ade478906172a19311c7f9` | Mandarin words, ranks, parts of speech, HSK 3.0 levels, measure words | MIT (meanings from CC-CEDICT) |
| [CC-CEDICT](https://cc-cedict.org) | as shipped in the pycccedict 1.2.0 wheel, sha256 `a810ca3255516a090f5f9059d6cdb156f86842f579f658a7ae7a84273bcd6326` | Cantonese glosses where none is written here | CC BY-SA 4.0 |
| Hong Kong Cantonese Corpus (HKCanCor) | as shipped in the pycantonese 5.0.0 wheel, sha256 `4a010973683fb8d14313ef44cdf7a4ded1adbe1a1bc64c93d0f75e65f141dd1a` | Cantonese words, counts, parts of speech, Jyutping | CC BY 4.0 |

Written here (no outside source): the French word list, glosses, genders and plurals
(`fr_*.txt`), the French conjugation engine (`fr_conj.py`, ported to Kotlin as
`FrenchConjugator.kt`), the Mandarin and Cantonese reading picks and glosses (`zh_over.txt`,
`yue_over.txt`) and every grammar note (`*_notes.txt`).

The wheels are only unpacked as zip files and read as data: nothing in them is installed,
imported or run. Lay the files out like this (or point `CORE_SRC` elsewhere):

    scripts/core_words/sources/
      wordfreq/large_fr.msgpack.gz      (wordfreq/data/ in the wordfreq wheel)
      wordfreq/large_zh.msgpack.gz
      hkcancor/*.cha                    (pycantonese/data/hkcancor/ in the pycantonese wheel)
      cedict.txt.gz                     (pycccedict/data/cedict_1_0_ts_utf-8_mdbg.txt.gz)
      complete.json                     (from complete-hsk-vocabulary)

then, from `android-project/`:

    python3 -I scripts/core_words/build.py app/src/main/assets/core app/src/test/resources/core/fr_conj_golden.json

The second file is every listed verb conjugated by `fr_conj.py`, which the unit tests compare
the Kotlin port against. `sources/` is not committed.
