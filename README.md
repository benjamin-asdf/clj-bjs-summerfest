# Summer Fest

Clojure + Datastar event site (RSVP, gallery, chat, party games).
Magic-link auth, PostgreSQL, http-kit, hiccup.

## Local dev

Postgres needs to be running locally. First time:

```sh
./scripts/setup-db.sh   # creates user/db, applies migrations
```

Then:

```sh
./scripts/dev.sh        # http on :3030, nREPL on :7889
```

Connect your editor to nREPL on `localhost:7889`, or eval one-offs:

```sh
clj-nrepl-eval -p 7889 "(require '[summerfest.db :as db]) (db/list-users)"
```

## Remote layout

The deploy target is `linode` (resolved via `~/.ssh/config`).

| Path                          | Purpose                                  |
| ----------------------------- | ---------------------------------------- |
| `/opt/summerfest/code`        | rsynced source                           |
| `/opt/summerfest/uploads`     | persistent gallery uploads (not synced)  |
| `/opt/summerfest/.env`        | server-side secrets (SESSION_SECRET)     |
| `/opt/summerfest/run/app.pid` | running pid                              |
| `/opt/summerfest/log/app.log` | stdout/stderr                            |
| `127.0.0.1:8096`              | http (proxied by nginx at `/summerfest`) |
| `127.0.0.1:7888`              | embedded nREPL (loopback only)           |
| `127.0.0.1:5432`              | PostgreSQL 15                            |

Override anything in `scripts/deploy.local.env` (gitignored).

## Deploy

Plain rsync + restart:

```sh
./scripts/deploy.sh                # rsync + restart
./scripts/deploy.sh --no-restart   # rsync only (no app restart)
./scripts/start.sh                 # start without rsync
./scripts/stop.sh                  # stop the running app
./scripts/logs.sh                  # tail app.log
```

Cold start takes ~30–40 s on the small VPS (deps + nREPL load).

### Database migrations

```sh
./scripts/migrate.sh             # local DB
./scripts/migrate.sh --remote    # live prod via nREPL — no restart needed
```

### First-time remote setup

Once per host, on the VPS:

```sh
sh /opt/summerfest/code/scripts/remote/install-pg.sh
# nginx vhost: /opt/summerfest/code/scripts/remote/summerfest-location.conf
```

`sshd_config` must allow TCP forwarding (`AllowTcpForwarding yes`) so the
nREPL tunnel works.

## Live updates via remote nREPL

The prod app starts an nREPL server on `127.0.0.1:7888`. It's loopback-only,
so you reach it through an SSH tunnel:

```sh
./scripts/repl-tunnel.sh
# leave running; connects localhost:7888 -> remote 127.0.0.1:7888
```

Then from another shell, eval anything against the live app:

```sh
clj-nrepl-eval -p 7888 "(require 'summerfest.views :reload)"
```

For one-shot evals, the tunnel script can do it inline (opens, evals, closes):

```sh
./scripts/repl-tunnel.sh --eval "(require 'summerfest.views :reload)"
```

### Example: list users on prod

```sh
./scripts/repl-tunnel.sh --eval "(require '[summerfest.db :as db]) \
  (->> (db/list-users) \
       (map (juxt :id :name :group-size :token)) \
       clojure.pprint/pprint)"
```

Sample output:

```
([1 "Alice & Bob" 2 #uuid "8c4f...c0a1"]
 [2 "Solo Sam" 1 #uuid "1e7b...9d3f"])
```

### Other useful prod evals

```sh
# Apply a new migration without a restart
./scripts/repl-tunnel.sh --eval \
  "(require '[summerfest.db :as db]) (db/migrate!)"

# Create a new user + magic link
./scripts/repl-tunnel.sh --eval \
  "(require '[summerfest.db :as db]) (db/create-user! \"Charlie\" :group-size 1)"

# Reload a namespace after pushing a code-only change
./scripts/repl-tunnel.sh --eval \
  "(require 'summerfest.views :reload)"

# Sanity-check chat counts
./scripts/repl-tunnel.sh --eval \
  "(require '[summerfest.db :as db]) (db/q \"SELECT COUNT(*) FROM chat_messages\")"
```

## Notes

- Alpine 3.17 ships an old `clojure` CLI (1.9.0.315) — it doesn't understand
  `-M` / `-A` / `-X`. Remote scripts use `-R:alias -m ns.name` instead.
- nginx keeps the `/summerfest` prefix on the wire; `wrap-base-path`
  middleware in the app strips it.
- `SESSION_SECRET` lives in `$REMOTE_ROOT/.env` on the server, never in the
  repo. Bootstrap once per environment:

  ```sh
  ssh linode 'umask 077 && \
    echo "SESSION_SECRET=$(openssl rand -base64 32)" >> /opt/summerfest/.env'
  ```

  `scripts/remote/start.sh` sources this file on each start. The app fails
  fast at boot if the secret is missing or shorter than 16 chars. Rotating
  the secret (regenerating the file) invalidates every existing session
  cookie — only do it intentionally.
