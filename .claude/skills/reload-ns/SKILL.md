---
name: reload-ns
description: Reload one or more Clojure namespaces in the running project nREPL. Use when the user says "reload", "reload <ns>", "reload views/i18n/etc", or after editing a .clj file and wanting to pick up changes without restarting the server.
---

# Reload Clojure namespace(s) via nREPL

The project runs an nREPL server in this directory. The port can change between sessions — discover it with `clj-nrepl-eval --discover-ports` and pick the one listed under the current directory. (`dev.sh` / the `:prod` alias defaults to 7889 but the user often starts a fresh REPL on a random port.) Use `clj-nrepl-eval` to send `(require '<ns> :reload)` to that session.

## How to invoke

If the user says "reload views", reload `summerfest.views`. If they say "reload i18n", reload `summerfest.i18n`. Map common shorthand to full namespace names under `summerfest.*`. If unsure which namespace, ask.

## Steps

1. Discover the port: `clj-nrepl-eval --discover-ports` and pick the one listed under this project directory. If multiple are listed, ask the user which one is current.
2. Issue the reload:

   ```bash
   clj-nrepl-eval -p <PORT> "(require 'summerfest.<ns> :reload)"
   ```

3. For multiple namespaces, chain them in one call:

   ```bash
   clj-nrepl-eval -p <PORT> "(require 'summerfest.i18n :reload) (require 'summerfest.views :reload)"
   ```

4. Report the result. If reload fails (compile error), surface the exception so the user can fix the file.

## Notes

- Static assets (`resources/public/*.css`, images) are **not** reloaded by `require :reload` — the browser must hard-refresh to pick them up.
- Data captured at namespace-load time (e.g. the `translations` map literal in `summerfest.i18n`) only refreshes when that namespace is reloaded.
- Avoid `(require ... :reload-all)` unless asked — it cascades through dependencies and is slower.
