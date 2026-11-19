# Re-sign all commits with your GPG key

How to put your own GPG signature on every commit of a branch, so GitHub shows them as **Verified**. Authors, dates,
messages and files stay exactly as they are; only the signatures are added. Tested on 2026-11-19 on a copy of this
branch (git 2.43, GnuPG 2.4.4): 18 of 18 commits got a good signature, and files, authors, author dates, committer
dates and messages were byte-identical afterwards.

> **This rewrites history.** Every commit ID changes (a signature is part of the commit). Do it on your own
> computer, on a branch only you use, and push with `--force-with-lease` (step 6). Anyone else with a copy of the
> branch must fetch it fresh afterwards.

## 0. What you need

| | |
|---|---|
| Git | 2.30 or newer (`git --version`) |
| GnuPG | `gpg --version` (Windows: Gpg4win; macOS: `brew install gnupg pinentry-mac`) |
| A GPG key | with a user ID for **69535896+Rafee-M@users.noreply.github.com** (step 1) |
| GitHub | the public key added to your account (step 2) |

The private key stays on your computer. Never put it into a cloud session, a repository or a chat.

## 1. Your key and its e-mail address

List your keys:

```bash
gpg --list-secret-keys --keyid-format=long
```

The line `sec   ed25519/1D6E66C0D0E756E2 …` holds the **key ID** (here `1D6E66C0D0E756E2`). The `uid` lines must include
`69535896+Rafee-M@users.noreply.github.com`: GitHub only shows "Verified" when the commit's e-mail is one of the key's
user IDs **and** a verified e-mail of your account (your noreply address always is).

- **No key yet:**
  ```bash
  gpg --quick-gen-key "Rafee-M <69535896+Rafee-M@users.noreply.github.com>" ed25519 sign 2y
  ```
  It asks for a passphrase: choose a strong one and keep it in your password manager.
- **Key exists, but without the noreply address:** add it as a user ID (replace KEYID):
  ```bash
  gpg --quick-add-uid KEYID "Rafee-M <69535896+Rafee-M@users.noreply.github.com>"
  ```

## 2. Give GitHub your public key

```bash
gpg --armor --export KEYID
```

Copy everything from `-----BEGIN PGP PUBLIC KEY BLOCK-----` to `-----END …-----` into GitHub → Settings → **SSH and
GPG keys** → **New GPG key**. (If you added a user ID in step 1 to a key GitHub already has, delete the old entry and
add the new export: GitHub only knows the user IDs it was given.)

## 3. Tell git to sign with that key (in the repository)

```bash
cd nextdnsmanager-plus
git config user.name "Rafee-M"
git config user.email "69535896+Rafee-M@users.noreply.github.com"
git config gpg.format openpgp
git config user.signingkey KEYID
git config commit.gpgsign true
```

`gpg.format openpgp` matters: if your global git settings say `ssh` (some tools set that), git would try to sign with
an SSH key instead and the rehearsal showed no GPG signature at all. `commit.gpgsign true` also signs every **future**
commit you make.

If gpg can't ask for your passphrase in a terminal: `export GPG_TTY=$(tty)` (Linux/macOS; put it in `~/.bashrc` or
`~/.zshrc`). On macOS add `pinentry-program /opt/homebrew/bin/pinentry-mac` to `~/.gnupg/gpg-agent.conf`, then
`gpgconf --kill gpg-agent`.

Quick test (makes no commit):

```bash
echo test | gpg --clearsign --local-user KEYID > /dev/null && echo "gpg works"
```

## 4. Re-sign every commit of the branch

```bash
git fetch origin
git checkout claude/nextdns-manager-plus-review-el0q2u
git status                                   # must say "nothing to commit, working tree clean"
git pull --ff-only                           # be exactly at the remote's newest commit
git branch backup/before-signing             # a safety copy of the unsigned history
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f --commit-filter 'git commit-tree -S "$@"' -- HEAD
```

- `--commit-filter 'git commit-tree -S "$@"'` rebuilds each commit exactly as it was (same files, author, dates,
  message) and signs it with `user.signingkey`. gpg may ask for your passphrase once (the agent remembers it).
- `-- HEAD` = every commit from the very first one (this branch has no older history).
- Why not `git rebase --root --exec 'git commit --amend -S'`: it was rehearsed too and **changed the committer dates**
  to "now". The filter keeps them.

## 5. Check before you push

```bash
git log --format='%h %G? %an <%ae> %s' | head -20      # every line must show G (= good signature)
git log --format=%G? | sort | uniq -c                   # one line: "<number of commits> G"
git verify-commit HEAD                                  # "Good signature from Rafee-M <69535896+…>"
git diff backup/before-signing HEAD --stat              # empty = no file changed
```

`%G?` letters: **G** good, **U** good but your key isn't marked trusted locally (fine for GitHub), **N** no signature,
**B** bad. Anything other than G/U: stop, fix step 3, then `git reset --hard backup/before-signing` and repeat step 4.

## 6. Push

```bash
git push --force-with-lease origin claude/nextdns-manager-plus-review-el0q2u
```

`--force-with-lease` refuses if GitHub has commits you haven't fetched, so nothing of anybody else is overwritten.
Then open the branch's commits on GitHub: each should show **Verified**. When you are happy, delete the safety copy:
`git branch -D backup/before-signing`.

## 7. Afterwards

- **Commit IDs changed again.** The table in `handoff.md` §1 (old → new IDs) points at the unsigned IDs; old IDs
  quoted in older docs stay findable through it, but the "new" column now refers to the history before signing.
- **New commits:** with `commit.gpgsign true` every commit you make is signed. Commits made in a cloud session can't be
  signed with your key (it never leaves your computer); sign them later with step 4 (on the commits since the last
  signed one: `-- <last-signed-commit>..HEAD`), or merge through a pull request on github.com, whose merge commit
  GitHub signs itself.
- **Other branches** (e.g. the old `claude/gallant-bardeen-myrq5y`) are not touched by this; repeat step 4 on them if
  wanted.

## Undo

Before pushing: `git reset --hard backup/before-signing`. After pushing: push the backup back with
`git push --force-with-lease origin backup/before-signing:claude/nextdns-manager-plus-review-el0q2u`.
