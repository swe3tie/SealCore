# Git setup notes

## Branches and artifacts

`main` is the development branch. Every artifact is named after the commit it
was built from, so a jar in a `plugins/` folder is always traceable:

```
sealcore/build/libs/SealCore-0.1.0-SNAPSHOT-main-a1b2c3d.jar
```

The same branch and short commit go into the jar manifest and into
`plugin.yml`, so `/sealcore version` reports `0.1.0-SNAPSHOT+main.a1b2c3d`. A
build outside a checkout falls back to `nogit` instead of inventing
provenance.

Rebuild after committing if the commit id in the name matters to you; the label
is resolved at configuration time.

## Commit identity

`user.name` and `user.email` are set repository-locally. Change them if the
defaults are not yours:

```bash
git config user.name "Your Name"
git config user.email "you@example.com"
```

## Remote

The remote is `origin`, pointing at `https://github.com/swe3tie/SealCore.git`.

```bash
git push -u origin main
```

Authentication is whatever the machine is set up for, an SSH agent or a
credential helper. Nothing is stored in this repository.
