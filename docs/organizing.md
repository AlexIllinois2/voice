# Organizing Audiobooks

Adding a book in Voice adds exactly one book per operation: pick either a zip archive or a folder, and it is added as a single book. A zip's
audio entries and a folder's audio files become the chapters of that book.

Previously added folders can still use these structures; they keep working on rescans:

## 1. **Audiobooks in Separate Folders**

Each folder within a previously selected directory is treated as a separate audiobook. Any audio files directly inside that directory (not in
a subfolder) will also be recognized as individual audiobooks.

**Example:**

```
/Audiobooks
├─ TheHobbit/
│   ├─ chapter1.mp3
│   └─ chapter2.mp3
├─ MobyDick/
│   ├─ chapter1.mp3
│   └─ chapter2.mp3
└─ LittlePrince.mp3
```

Voice recognizes three audiobooks: `TheHobbit`, `MobyDick`, and the single-file book `LittlePrince`.

## 2. **Single Audiobook Folder**

The selected folder itself is one audiobook, with files inside treated as chapters.

**Example:**

```
/PrideAndPrejudice
├─ Chapter1.mp3
├─ Chapter2.mp3
└─ Chapter3.mp3
```

Voice recognizes one audiobook: `PrideAndPrejudice`.

## 3. **Audiobooks Organized by Author**

First-level folders represent authors, and subfolders represent audiobooks.

**Example:**

```
/Authors
├─ Woolf/
│   ├─ MrsDalloway/
│   │   ├─ chapter1.mp3
│   │   └─ chapter2.mp3
│   └─ ToTheLighthouse/
│       ├─ chapter1.mp3
│       └─ chapter2.mp3
└─ Tolkien/
    └─ TheHobbit/
        ├─ chapter1.mp3
        └─ chapter2.mp3
```

Voice recognizes authors (`Woolf`, `Tolkien`) and their audiobooks (`MrsDalloway`, `ToTheLighthouse`, `TheHobbit`).
