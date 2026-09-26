# Git setup notes

## Repository layout

`git init` at the project root is the normal setup. If the root `.git` is
unavailable — for example it is a read-only mount in a container or a sandbox —
the repository metadata is kept in `.git-sealcore/` at the project root instead,
and every command needs the two environment variables:

```bash
export GIT_DIR="$PWD/.git-sealcore"
export GIT_WORK_TREE="$PWD"
git status
```

Gradle inherits those variables, so `./gradlew :sealcore:build` still names the
artifact with the right branch and commit. Without them the build falls back to
`nogit`, which is visible in the jar name rather than hidden.

To move the metadata back to a normal layout:

```bash
cp -a .git-sealcore/. .git/
unset GIT_DIR GIT_WORK_TREE
```

## Commit identity

`user.name` and `user.email` are set repository-locally. Change them before the
first push if they should not be the defaults:

```bash
git config user.name "Your Name"
git config user.email "you@example.com"
```

## Remote

No remote is configured. Add one and push:

```bash
git remote add origin <url>
git push -u origin main
```
