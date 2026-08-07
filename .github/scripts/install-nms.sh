#!/usr/bin/env bash
# Install Spigot/Paper NMS jars into ~/.m2 from CodeMC (no BuildTools).
# https://repo.codemc.io/repository/nms/
set -euo pipefail

CODEMC_NMS="${CODEMC_NMS:-https://repo.codemc.io/repository/nms}"
M2_REPO="${HOME}/.m2/repository"
WORK_DIR="${NMS_WORK_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.nms-build/downloads}"
NMS_GROUP="${NMS_GROUP:-all}"

mkdir -p "${WORK_DIR}"

download() {
  local url="$1"
  local out="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL --retry 3 --retry-delay 2 -o "${out}" "${url}"
  else
    wget -q -O "${out}" "${url}"
  fi
}

has_main_jar() {
  local group_path="$1"
  local artifact="$2"
  local version="$3"
  local dir="${M2_REPO}/${group_path}/${artifact}/${version}"
  [[ -d "${dir}" ]] || return 1
  # Accept either fixed SNAPSHOT name or timestamped SNAPSHOT jars
  find "${dir}" -maxdepth 1 -type f \( -name "${artifact}-${version}.jar" -o -name "${artifact}-*.jar" \) \
    ! -name "*-sources.jar" ! -name "*-javadoc.jar" ! -name "*-tests.jar" | grep -q .
}

# Parse maven-metadata.xml and download the main jar + pom, then install-file
install_from_codemc() {
  local groupId="$1"
  local artifactId="$2"
  local version="$3"
  local group_path
  group_path="$(echo "${groupId}" | tr '.' '/')"

  if has_main_jar "${group_path}" "${artifactId}" "${version}"; then
    echo "OK (cached) ${groupId}:${artifactId}:${version}"
    return 0
  fi

  local base="${CODEMC_NMS}/${group_path}/${artifactId}/${version}"
  local meta_file="${WORK_DIR}/${artifactId}-${version}-maven-metadata.xml"
  echo "Fetching metadata ${groupId}:${artifactId}:${version}..."
  download "${base}/maven-metadata.xml" "${meta_file}"

  # Prefer snapshotVersion entry for jar without classifier
  local snap_value
  snap_value="$(python3 - "${meta_file}" <<'PY' || true
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
# handle default namespace-less metadata
for sv in root.findall(".//snapshotVersion"):
    ext = sv.findtext("extension")
    classifier = sv.findtext("classifier")
    if ext == "jar" and (classifier is None or classifier == ""):
        print(sv.findtext("value") or "")
        break
else:
    # non-snapshot or missing snapshotVersions: use version tag
    ver = root.findtext("./version") or root.findtext("./versioning/release") or ""
    print(ver)
PY
)"

  if [[ -z "${snap_value}" ]]; then
    # Fallback: grep-based parse for environments without usable python xml
    snap_value="$(grep -A4 '<extension>jar</extension>' "${meta_file}" | grep -m1 '<value>' | sed -E 's/.*<value>([^<]+)<.*/\1/' || true)"
  fi
  if [[ -z "${snap_value}" ]]; then
    snap_value="${version}"
  fi

  local jar_name="${artifactId}-${snap_value}.jar"
  local pom_name="${artifactId}-${snap_value}.pom"
  local jar_file="${WORK_DIR}/${jar_name}"
  local pom_file="${WORK_DIR}/${pom_name}"

  echo "Downloading ${jar_name}..."
  download "${base}/${jar_name}" "${jar_file}"

  # pom is optional for compile, but install-file is happier with it
  if ! download "${base}/${pom_name}" "${pom_file}" 2>/dev/null; then
    # try non-timestamped pom
    if ! download "${base}/${artifactId}-${version}.pom" "${pom_file}" 2>/dev/null; then
      pom_file=""
    fi
  fi

  echo "Installing into local Maven repo as ${groupId}:${artifactId}:${version}..."
  if [[ -n "${pom_file}" && -f "${pom_file}" ]]; then
    mvn -q install:install-file \
      -Dfile="${jar_file}" \
      -DpomFile="${pom_file}" \
      -DgroupId="${groupId}" \
      -DartifactId="${artifactId}" \
      -Dversion="${version}" \
      -Dpackaging=jar
  else
    mvn -q install:install-file \
      -Dfile="${jar_file}" \
      -DgroupId="${groupId}" \
      -DartifactId="${artifactId}" \
      -Dversion="${version}" \
      -Dpackaging=jar
  fi

  if ! has_main_jar "${group_path}" "${artifactId}" "${version}"; then
    echo "ERROR: install-file did not place jar for ${groupId}:${artifactId}:${version}" >&2
    return 1
  fi
  echo "OK ${groupId}:${artifactId}:${version}"
}

install_legacy() {
  echo "=== legacy NMS (1.8.8 – 1.16.5) via CodeMC ==="
  install_from_codemc org.spigotmc spigot "1.8.8-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.9-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.9.4-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.13-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.14.4-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.15.1-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.16.1-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.16.3-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.16.5-R0.1-SNAPSHOT"

  # paper-coordinate modules
  install_from_codemc org.spigotmc spigot "1.10.2-R0.1-SNAPSHOT"
  install_from_codemc com.destroystokyo.paper paper "1.10.2-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.11.2-R0.1-SNAPSHOT"
  install_from_codemc com.destroystokyo.paper paper "1.11.2-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.12.2-R0.1-SNAPSHOT"
  install_from_codemc com.destroystokyo.paper paper "1.12.2-R0.1-SNAPSHOT"
  install_from_codemc org.spigotmc spigot "1.13.2-R0.1-SNAPSHOT"
  install_from_codemc com.destroystokyo.paper paper "1.13.2-R0.1-SNAPSHOT"
}

install_modern() {
  echo "=== modern NMS (1.17.1) via CodeMC ==="
  install_from_codemc org.spigotmc spigot "1.17.1-R0.1-SNAPSHOT"
}

echo "CodeMC NMS base: ${CODEMC_NMS}"
echo "NMS_GROUP=${NMS_GROUP}"

case "${NMS_GROUP}" in
  legacy) install_legacy ;;
  modern) install_modern ;;
  all)
    install_legacy
    install_modern
    ;;
  *)
    echo "ERROR: Unknown NMS_GROUP='${NMS_GROUP}' (legacy|modern|all)" >&2
    exit 1
    ;;
esac

echo "All requested NMS artifacts are installed."
