# ADR 0009: Refuse unsafe text at the input boundary, escape at the output

**Status:** Accepted
**Author:** Danish Husain

## Context

Claims, e-NAC decisions and remarks carry free text typed or pasted by many users. Text is shown to other officials and will later be printed on the claim forms. Two kinds of problem need handling:

- **Markup and injection** (HTML, script, SQL). These are only dangerous when text is interpreted, which happens at output.
- **Invisible or deceptive characters** (control characters, right to left overrides that make `bill_fdp.exe` display as `bill_exe.pdf`, broken Unicode). These are dangerous or confusing wherever the text is shown, and no output encoder removes them.

Escaping HTML on input was rejected: it corrupts stored data (an `&` in a hospital name becomes `&amp;` on the printed form) and gives a false sense of safety for other output formats.

## Decision

1. A global JSON deserializer (`SafeText`) handles every incoming string. It normalises text to Unicode NFC and **refuses** (400 `UNSAFE_TEXT`) any value with control characters other than tab and line breaks, bidirectional overrides or isolates, unpaired surrogates or noncharacters. Refusing, rather than silently deleting, keeps stored text identical to what the user saw.
2. Uploaded file names, which cannot be refused without losing the upload, have those characters replaced.
3. Formats and lengths are checked with Bean Validation on every request type.
4. Output is encoded where it is produced: React escapes rendered text, SQL is parameter bound, downloads are served as attachments with a sandbox policy.

## Consequences

- No endpoint can forget to sanitise, because the filter sits in the JSON reader.
- A user who pastes text with hidden characters gets a clear message and must retype it; this is rare and preferable to storing text that differs from what they saw.
- Query parameters and form fields outside JSON bodies are covered by validation annotations, not by this filter.
