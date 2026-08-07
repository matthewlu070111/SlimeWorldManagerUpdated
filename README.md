# Slime World Manager

[![CI](https://github.com/matthewlu070111/SlimeWorldManagerUpdated/actions/workflows/ci.yml/badge.svg)](https://github.com/matthewlu070111/SlimeWorldManagerUpdated/actions/workflows/ci.yml)
[![Release](https://github.com/matthewlu070111/SlimeWorldManagerUpdated/actions/workflows/release.yml/badge.svg)](https://github.com/matthewlu070111/SlimeWorldManagerUpdated/actions/workflows/release.yml)

Slime World Manager is a Minecraft plugin that implements the Slime Region Format, developed by the Hypixel Dev Team.
Its goal is to provide server administrators with an easy-to-use tool to load worlds faster and save space.

#### Supported versions

| Versions | Compile (NMS) | Runtime | Status |
|----------|---------------|---------|--------|
| **1.8.8 – 1.17.1** | Spigot | Spigot / Paper | Supported in this fork |
| **1.18+** | — | — | Use [AdvancedSlimeWorldManager](https://github.com/Paul19988/Advanced-Slime-World-Manager) / AdvancedSlimePaper |

#### Spigot vs Paper

| Layer | Policy |
|-------|--------|
| **Compile** | Prefer **Spigot** NMS jars (BuildTools). Historical modules that still declare Paper Maven coordinates are filled from Spigot in CI. |
| **Runtime** | **Spigot and Paper** of the same Minecraft version. |
| **Paper-only hooks** | Optional ClassModifier patches only; never hard-required. |

#### Releases vs CI

| Workflow | When | What |
|----------|------|------|
| [`.github/workflows/ci.yml`](.github/workflows/ci.yml) | push / PR | Build + upload **Actions artifacts** (no GitHub Release) |
| [`.github/workflows/release.yml`](.github/workflows/release.yml) | tag `v*` | Build + **GitHub Release** with JARs and notes (commits + version range) |

```bash
git tag v2.3.0
git push origin v2.3.0
```

#### Build

Requirements:

* **JDK 17** (module `v1_17_R1` compiles with `--release 16`; older modules stay on 8 bytecode)
* Maven 3.6+
* Spigot/Paper NMS jars (resolved from [CodeMC NMS](https://repo.codemc.io/repository/nms/) by default)

```bash
# Optional warm of local .m2 (CI does this too)
.github/scripts/install-nms.sh

mvn -B package -DskipTests
```

NMS install downloads prebuilt jars from CodeMC (no BuildTools). BuildTools is intentionally not used in CI because old MC versions fail on modern runners (missing snapshot deps like `bungeecord-chat`).

Artifacts:

* `slimeworldmanager-plugin/target/slimeworldmanager-plugin-*.jar`
* `slimeworldmanager-classmodifier/target/slimeworldmanager-classmodifier-*.jar`

## Credits

Thanks to:

* All the contributors who helped by adding features to SWM.
* [cijaaimee](https://github.com/cijaaimee) / Grinderwolf for the original Slime World Manager.
* Community forks (ASWM and others) whose public NMS work informed 1.16/1.17 support.
* [Minikloon](https://twitter.com/Minikloon) and the [Hypixel](https://twitter.com/HypixelNetwork) team for developing the SRF.
