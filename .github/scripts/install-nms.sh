#!/usr/bin/env bash
# Install Spigot NMS jars into the local Maven repository for SWM builds.
#
# BuildTools Java requirements:
#   - Minecraft 1.8.x – 1.16.x  →  Java 8
#   - Minecraft 1.17.x          →  Java 16+
#
# Env:
#   JAVA8_HOME   – JDK 8 home (required for legacy revs)
#   JAVA17_HOME  – JDK 17 home (required for 1.17 + preferred for maven install-file)
#   NMS_GROUP    – legacy | modern | all  (default: all)
#   NMS_WORK_DIR – BuildTools work dir (default: <repo>/.nms-build)
#
# Safe to re-run: skips versions already present in ~/.m2.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${NMS_WORK_DIR:-${ROOT_DIR}/.nms-build}"
BT_JAR="${WORK_DIR}/BuildTools.jar"
M2_REPO="${HOME}/.m2/repository"
NMS_GROUP="${NMS_GROUP:-all}"

mkdir -p "${WORK_DIR}"
cd "${WORK_DIR}"

# Resolve JDK homes (CI exports JAVA8_HOME / JAVA17_HOME; local may use JAVA_HOME only)
if [[ -z "${JAVA8_HOME:-}" ]]; then
  if command -v /usr/lib/jvm/temurin-8-jdk-amd64/bin/java >/dev/null 2>&1; then
    JAVA8_HOME="/usr/lib/jvm/temurin-8-jdk-amd64"
  elif [[ -n "${JAVA_HOME:-}" ]] && "${JAVA_HOME}/bin/java" -version 2>&1 | grep -Eq 'version "1\.8|version "8'; then
    JAVA8_HOME="${JAVA_HOME}"
  fi
fi

if [[ -z "${JAVA17_HOME:-}" ]]; then
  if command -v /usr/lib/jvm/temurin-17-jdk-amd64/bin/java >/dev/null 2>&1; then
    JAVA17_HOME="/usr/lib/jvm/temurin-17-jdk-amd64"
  elif [[ -n "${JAVA_HOME:-}" ]] && "${JAVA_HOME}/bin/java" -version 2>&1 | grep -Eq 'version "17'; then
    JAVA17_HOME="${JAVA_HOME}"
  fi
fi

use_java() {
  local home="$1"
  local label="$2"
  if [[ -z "${home}" || ! -x "${home}/bin/java" ]]; then
    echo "ERROR: ${label} is not set or invalid (got: '${home:-}')." >&2
    echo "Set JAVA8_HOME and/or JAVA17_HOME before running this script." >&2
    exit 1
  fi
  export JAVA_HOME="${home}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
  echo "Using ${label}: $(java -version 2>&1 | head -n1)"
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

has_artifact() {
  local group_path="$1"
  local artifact="$2"
  local version="$3"
  local jar="${M2_REPO}/${group_path}/${artifact}/${version}/${artifact}-${version}.jar"
  [[ -f "${jar}" ]]
}

install_file() {
  local file="$1"
  local groupId="$2"
  local artifactId="$3"
  local version="$4"
  # Prefer Java 17 for Maven tooling when available
  if [[ -n "${JAVA17_HOME:-}" && -x "${JAVA17_HOME}/bin/java" ]]; then
    export JAVA_HOME="${JAVA17_HOME}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
  fi
  mvn -q install:install-file \
    -Dfile="${file}" \
    -DgroupId="${groupId}" \
    -DartifactId="${artifactId}" \
    -Dversion="${version}" \
    -Dpackaging=jar
}

ensure_buildtools() {
  if [[ ! -f "${BT_JAR}" ]]; then
    echo "Downloading BuildTools..."
    download "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar" "${BT_JAR}"
  fi
}

# build_spigot <rev> <mavenVersion> <javaHomeLabel>
# javaHomeLabel: java8 | java17
build_spigot() {
  local rev="$1"
  local version="$2"
  local java_label="$3"
  local group_path="org/spigotmc"
  local artifact="spigot"

  if has_artifact "${group_path}" "${artifact}" "${version}"; then
    echo "Skipping Spigot ${rev} (already installed as ${version})"
    return 0
  fi

  if [[ "${java_label}" == "java8" ]]; then
    use_java "${JAVA8_HOME:-}" "JAVA8_HOME"
  else
    use_java "${JAVA17_HOME:-}" "JAVA17_HOME"
  fi

  ensure_buildtools
  echo "Building Spigot ${rev} via BuildTools with ${java_label} (this can take a while)..."
  local dir="${WORK_DIR}/spigot-${rev}"
  mkdir -p "${dir}"
  (
    cd "${dir}"
    cp "${BT_JAR}" BuildTools.jar
    java -jar BuildTools.jar --rev "${rev}" --compile spigot
  )

  if ! has_artifact "${group_path}" "${artifact}" "${version}"; then
    local built
    built="$(find "${dir}" -maxdepth 1 -name "spigot-${rev}*.jar" ! -name "spigot-${rev}-*.jar.bak" | head -n1 || true)"
    if [[ -z "${built}" ]]; then
      built="$(find "${dir}" -maxdepth 1 -name "spigot-*.jar" | head -n1 || true)"
    fi
    if [[ -n "${built}" && -f "${built}" ]]; then
      install_file "${built}" "org.spigotmc" "spigot" "${version}"
    else
      echo "ERROR: Could not locate built Spigot jar for ${rev}" >&2
      return 1
    fi
  fi
  echo "Installed Spigot ${version}"
}

# Legacy modules that still declare paper coordinates in their pom:
# install Spigot under the paper Maven coordinate so compile works.
install_as_paper_coord() {
  local mc_version="$1"
  local maven_version="$2"
  local group_path="com/destroystokyo/paper"
  local artifact="paper"

  if has_artifact "${group_path}" "${artifact}" "${maven_version}"; then
    echo "Skipping paper-coord ${maven_version} (already installed)"
    return 0
  fi

  local spigot_maven_version="${mc_version}-R0.1-SNAPSHOT"
  if ! has_artifact "org/spigotmc" "spigot" "${spigot_maven_version}"; then
    # paper-coord targets are all pre-1.17 → Java 8
    build_spigot "${mc_version}" "${spigot_maven_version}" "java8"
  fi

  local spigot_jar="${M2_REPO}/org/spigotmc/spigot/${spigot_maven_version}/spigot-${spigot_maven_version}.jar"
  if [[ -f "${spigot_jar}" ]]; then
    install_file "${spigot_jar}" "com.destroystokyo.paper" "paper" "${maven_version}"
    echo "Installed paper coordinate ${maven_version} from Spigot ${spigot_maven_version}"
    return 0
  fi

  echo "ERROR: Could not install paper-coordinate jar for ${mc_version}" >&2
  return 1
}

install_legacy() {
  echo "=== NMS group: legacy (Java 8 BuildTools) ==="
  build_spigot "1.8.8" "1.8.8-R0.1-SNAPSHOT" "java8"
  build_spigot "1.9" "1.9-R0.1-SNAPSHOT" "java8"
  build_spigot "1.9.4" "1.9.4-R0.1-SNAPSHOT" "java8"
  build_spigot "1.13" "1.13-R0.1-SNAPSHOT" "java8"
  build_spigot "1.14.4" "1.14.4-R0.1-SNAPSHOT" "java8"
  build_spigot "1.15.1" "1.15.1-R0.1-SNAPSHOT" "java8"
  build_spigot "1.16.1" "1.16.1-R0.1-SNAPSHOT" "java8"
  build_spigot "1.16.3" "1.16.3-R0.1-SNAPSHOT" "java8"
  build_spigot "1.16.5" "1.16.5-R0.1-SNAPSHOT" "java8"

  install_as_paper_coord "1.10.2" "1.10.2-R0.1-SNAPSHOT"
  install_as_paper_coord "1.11.2" "1.11.2-R0.1-SNAPSHOT"
  install_as_paper_coord "1.12.2" "1.12.2-R0.1-SNAPSHOT"
  install_as_paper_coord "1.13.2" "1.13.2-R0.1-SNAPSHOT"
}

install_modern() {
  echo "=== NMS group: modern (Java 17 BuildTools) ==="
  build_spigot "1.17.1" "1.17.1-R0.1-SNAPSHOT" "java17"
}

echo "NMS work directory: ${WORK_DIR}"
echo "NMS_GROUP=${NMS_GROUP}"
echo "JAVA8_HOME=${JAVA8_HOME:-<unset>}"
echo "JAVA17_HOME=${JAVA17_HOME:-<unset>}"

case "${NMS_GROUP}" in
  legacy)
    install_legacy
    ;;
  modern)
    install_modern
    ;;
  all)
    install_legacy
    install_modern
    ;;
  *)
    echo "ERROR: Unknown NMS_GROUP='${NMS_GROUP}' (expected legacy|modern|all)" >&2
    exit 1
    ;;
esac

echo "NMS dependencies for group '${NMS_GROUP}' are installed."
