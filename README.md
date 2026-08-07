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

* **JDK 8** for BuildTools on MC 1.8–1.16
* **JDK 17** for BuildTools on MC 1.17 and for the final Maven package (`v1_17_R1` needs release 16+)
* Maven 3.6+
* Spigot NMS jars in the local Maven repository (`.github/scripts/install-nms.sh`)

```bash
# Linux/macOS/Git Bash — install NMS in two Java groups
export JAVA8_HOME=/path/to/jdk8
export JAVA17_HOME=/path/to/jdk17

NMS_GROUP=legacy  .github/scripts/install-nms.sh   # 1.8.8 – 1.16.5 (Java 8)
NMS_GROUP=modern  .github/scripts/install-nms.sh   # 1.17.1 (Java 17)

export JAVA_HOME="$JAVA17_HOME"
mvn -B package -DskipTests
```

Artifacts:

* `slimeworldmanager-plugin/target/slimeworldmanager-plugin-*.jar`
* `slimeworldmanager-classmodifier/target/slimeworldmanager-classmodifier-*.jar`

## Credits

Thanks to:

* All the contributors who helped by adding features to SWM.
* [cijaaimee](https://github.com/cijaaimee) / Grinderwolf for the original Slime World Manager.
* Community forks (ASWM and others) whose public NMS work informed 1.16/1.17 support.
* [Minikloon](https://twitter.com/Minikloon) and the [Hypixel](https://twitter.com/HypixelNetwork) team for developing the SRF.
