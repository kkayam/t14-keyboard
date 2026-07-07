# T14 Keyboard

A minimal Android input method (IME) for phones with a 5×5 physical keyboard where
most keys carry two letters:

```
[call] [nav]  [home] [nav]  [end]
q w    e r    t y    u i    o p
a s    d f    g h    j k    l
z x    c v    b n    m      ⌫
[⇆]    [sym]  [␣ 0]  [⇧ #]  [⏎]
```

Typing is classic **multi-tap**: press a key once for its first letter, again quickly
for the next one. No dictionary, no prediction, no on-screen keyboard — just a slim,
optional status strip showing the current language and shift/sym state.

## Setup

1. Install the app, open it.
2. **Enable T14 Keyboard** (opens system input-method settings).
3. **Select T14 Keyboard** (opens the input-method picker).
4. Type into the test field.

## Typing

| Key | Behavior |
|---|---|
| letter keys | multi-tap through the key's characters; a different key or the timeout (default 500 ms, configurable) commits |
| space | space · double-space types “. ” (toggleable) |
| shift | ⇧ once → next letter uppercase · twice → caps lock · thrice → off |
| sym | once → next key types its symbol (the one printed on the key) · **long-press** → symbol lock, long-press again to exit · while sym is active the sym key itself types `* +` |
| switch | cycle enabled languages |
| backspace | deletes the pending letter first, then text; hold to repeat |
| enter | commits, then sends the field's action (send/search/done) or a newline |

Extra symbols hide behind multi-tap on the sym layer, e.g. the `.` key continues with
`: ; …`, the `!` key with `( ) [ ] { }`, the `@` key with `& % $ € £`.

In numeric and phone fields the symbol layer is active automatically, so digits come
out on the first tap.

Auto-capitalization of sentence starts is on by default (toggleable).

## Languages

English and Turkish are built in. A language is just a JSON file describing each key's
multi-tap cycle — import your own from the app (**Import language file…**):

```json
{
  "name": "Svenska",
  "code": "SV",
  "locale": "sv",
  "keys": {
    "Q": ["q", "w"], "E": ["e", "r"], "T": ["t", "y"], "U": ["u", "i"],
    "O": ["o", "p", "ö"], "A": ["a", "s", "å", "ä"], "D": ["d", "f"],
    "G": ["g", "h"], "J": ["j", "k"], "L": ["l"], "Z": ["z", "x"],
    "C": ["c", "v"], "B": ["b", "n"], "M": ["m"]
  }
}
```

- Key ids are the **first letter printed on each physical key**:
  `Q E T U O A D G J L Z C B M`, plus `SYM`, `SHIFT`, `SPACE` (used by the symbol layer).
- Each entry is the ordered list of characters the key cycles through. Entries may be
  multi-character strings (e.g. `"ch"`).
- `locale` (optional, BCP-47) controls uppercasing — Turkish `i` → `İ`.
- `code` is what the status strip shows; importing a file with an existing code
  replaces that language.
- A user-imported `sym.json` (code `SYM`) replaces the built-in symbol layer.

Enable/disable languages and reorder the switch cycle from the app's language list.

## Build

```
./gradlew assembleDebug   # APK at app/build/outputs/apk/debug/
./gradlew test            # multi-tap engine unit tests
```
