#!/usr/bin/env bash
# Resolve Spigot/Paper NMS jars into the local Maven repository.
#
# Primary path: download from CodeMC NMS repo (fast, no BuildTools).
# Optional fallback: BuildTools (set NMS_USE_BUILDTOOLS=1).
#
# Env:
#   NMS_GROUP           – legacy | modern | all  (default: all)
#   NMS_USE_BUILDTOOLS  – if 1, fall back to BuildTools when download fails
#   JAVA8_HOME / JAVA17_HOME – only needed for BuildTools fallback
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${NMS_WORK_DIR:-${ROOT_DIR}/.nms-build}"
M2_REPO="${HOME}/.m2/repository"
NMS_GROUP="${NMS_GROUP:-all}"
CODEMC_NMS="https://repo.codemc.io/repository/nms/"
BT_JAR="${WORK_DIR}/BuildTools.jar"
NMS_USE_BUILDTOOLS="${NMS_USE_BUILDTOOLS:-0}"

mkdir -p "${WORK_DIR}"

has_artifact() {
  local group_path="$1"
  local artifact="$2"
  local version="$3"
  local jar="${M2_REPO}/${group_path}/${artifact}/${version}/${artifact}-${version}.jar"
  # SNAPSHOT versions may use timestamped files; accept either name or any jar in dir
  if [[ -f "${jar}" ]]; then
    return 0
  fi
  local dir="${M2_REPO}/${group_path}/${artifact}/${version}"
  [[ -d "${dir}" ]] && find "${dir}" -maxdepth 1 -name "${artifact}-*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" | grep -q .
}

resolve_artifact() {
  local groupId="$1"
  local artifactId="$2"
  local version="$3"
  local group_path
  group_path="$(echo "${groupId}" | tr '.' '/')"

  if has_artifact "${group_path}" "${artifactId}" "${version}"; then
    echo "Skipping ${groupId}:${artifactId}:${version} (already in local repo)"
    return 0
  fi

  echo "Resolving ${groupId}:${artifactId}:${version} from CodeMC NMS..."
  if mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get \
      -DremoteRepositories="codemc-nms::::${CODEMC_NMS}" \
      -Dartifact="${groupId}:${artifactId}:${version}" \
      -Dtransitive=false; then
    if has_artifact "${group_path}" "${artifactId}" "${version}"; then
      echo "Installed ${groupId}:${artifactId}:${version}"
      return 0
    fi
  fi

  echo "WARN: Maven dependency:get failed for ${groupId}:${artifactId}:${version}" >&2
  return 1
}

install_file() {
  local file="$1"
  local groupId="$2"
  local artifactId="$3"
  local version="$4"
  mvn -q install:install-file \
    -Dfile="${file}" \
    -DgroupId="${groupId}" \
    -DartifactId="${artifactId}" \
    -Dversion="${version}" \
    -Dpackaging=jar
}

download() {
  local url="$1"
  local out="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "${out}" "${url}"
  else
    wget -q -O "${out}" "${url}"
  fi
}

use_java() {
  local home="$1"
  local label="$2"
  if [[ -z "${home}" || ! -x "${home}/bin/java" ]]; then
    echo "ERROR: ${label} is not set or invalid (got: '${home:-}')." >&2
    exit 1
  fi
  export JAVA_HOME="${home}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
  echo "Using ${label}: $(java -version 2>&1 | head -n1)"
}

ensure_buildtools() {
  if [[ ! -f "${BT_JAR}" ]]; then
    echo "Downloading BuildTools..."
    download "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar" "${BT_JAR}"
  fi
}

build_spigot_buildtools() {
  local rev="$1"
  local version="$2"
  local java_label="$3"

  if has_artifact "org/spigotmc" "spigot" "${version}"; then
    echo "Skipping BuildTools Spigot ${rev} (already installed)"
    return 0
  fi

  if [[ "${java_label}" == "java8" ]]; then
    use_java "${JAVA8_HOME:-}" "JAVA8_HOME"
  else
    use_java "${JAVA17_HOME:-}" "JAVA17_HOME"
  fi

  ensure_buildtools
  echo "Building Spigot ${rev} via BuildTools with ${java_label}..."
  local dir="${WORK_DIR}/spigot-${rev}"
  mkdir -p "${dir}"
  (
    cd "${dir}"
    git config --global --add safe.directory '*' || true
    cp "${BT_JAR}" BuildTools.jar
    java -jar BuildTools.jar --rev "${rev}" --compile spigot
  )

  if ! has_artifact "org/spigotmc" "spigot" "${version}"; then
    local built
    built="$(find "${dir}" -maxdepth 1 -name "spigot-*.jar" | head -n1 || true)"
    if [[ -n "${built}" && -f "${built}" ]]; then
      install_file "${built}" "org.spigotmc" "spigot" "${version}"
    else
      echo "ERROR: BuildTools did not produce Spigot jar for ${rev}" >&2
      return 1
    fi
  fi
}

install_spigot() {
  local rev="$1"
  local version="$2"
  local java_label="$3"

  if resolve_artifact "org.spigotmc" "spigot" "${version}"; then
    return 0
  fi

  if [[ "${NMS_USE_BUILDTOOLS}" == "1" ]]; then
    echo "Falling back to BuildTools for ${version}..."
    build_spigot_buildtools "${rev}" "${version}" "${java_label}"
    return $?
  fi

  echo "ERROR: Could not resolve org.spigotmc:spigot:${version} from CodeMC." >&2
  echo "Set NMS_USE_BUILDTOOLS=1 to enable BuildTools fallback." >&2
  return 1
}

install_paper() {
  local version="$1"
  # Prefer real paper artifact from CodeMC; otherwise alias from spigot.
  if resolve_artifact "com.destroystokyo.paper" "paper" "${version}"; then
    return 0
  fi

  local spigot_jar_dir="${M2_REPO}/org/spigotmc/spigot/${version}"
  local spigot_jar
  spigot_jar="$(find "${spigot_jar_dir}" -maxdepth 1 -name 'spigot-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null | head -n1 || true)"
  if [[ -n "${spigot_jar}" && -f "${spigot_jar}" ]]; then
    install_file "${spigot_jar}" "com.destroystokyo.paper" "paper" "${version}"
    echo "Installed paper coordinate ${version} from Spigot jar"
    return 0
  fi

  echo "ERROR: Could not install paper:${version}" >&2
  return 1
}

install_legacy() {
  echo "=== NMS group: legacy (1.8.8 – 1.16.5) ==="
  install_spigot "1.8.8" "1.8.8-R0.1-SNAPSHOT" "java8"
  install_spigot "1.9" "1.9-R0.1-SNAPSHOT" "java8"
  install_spigot "1.9.4" "1.9.4-R0.1-SNAPSHOT" "java8"
  install_spigot "1.13" "1.13-R0.1-SNAPSHOT" "java8"
  install_spigot "1.14.4" "1.14.4-R0.1-SNAPSHOT" "java8"
  install_spigot "1.15.1" "1.15.1-R0.1-SNAPSHOT" "java8"
  install_spigot "1.16.1" "1.16.1-R0.1-SNAPSHOT" "java8"
  install_spigot "1.16.3" "1.16.3-R0.1-SNAPSHOT" "java8"
  install_spigot "1.16.5" "1.16.5-R0.1-SNAPSHOT" "java8"

  # Modules that still declare paper coordinates
  install_spigot "1.10.2" "1.10.2-R0.1-SNAPSHOT" "java8"
  install_paper "1.10.2-R0.1-SNAPSHOT"
  install_spigot "1.11.2" "1.11.2-R0.1-SNAPSHOT" "java8"
  install_paper "1.11.2-R0.1-SNAPSHOT"
  install_spigot "1.12.2" "1.12.2-R0.1-SNAPSHOT" "java8"
  install_paper "1.12.2-R0.1-SNAPSHOT"
  install_spigot "1.13.2" "1.13.2-R0.1-SNAPSHOT" "java8"
  install_paper "1.13.2-R0.1-SNAPSHOT"
}

install_modern() {
  echo "=== NMS group: modern (1.17.1) ==="
  install_spigot "1.17.1" "1.17.1-R0.1-SNAPSHOT" "java17"
}

echo "NMS work directory: ${WORK_DIR}"
echo "NMS_GROUP=${NMS_GROUP}"
echo "NMS_USE_BUILDTOOLS=${NMS_USE_BUILDTOOLS}"
echo "CodeMC: ${CODEMC_NMS}"

case "${NMS_GROUP}" in
  legacy) install_legacy ;;
  modern) install_modern ;;
  all)
    install_legacy
    install_modern
    ;;
  *)
    echo "ERROR: Unknown NMS_GROUP='${NMS_GROUP}' (expected legacy|modern|all)" >&2
    exit 1
    ;;
esac

echo "NMS dependencies for group '${NMS_GROUP}' are ready."
