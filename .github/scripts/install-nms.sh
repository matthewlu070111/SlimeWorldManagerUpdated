#!/usr/bin/env bash
# Install Spigot NMS jars into ~/.m2 from CodeMC (no BuildTools).
#
# IMPORTANT: Do NOT install CodeMC's original POMs. Those POMs often declare
# parents/transitive deps (e.g. minecraft-server) and repositories that point at
# https://papermc.io (HTTP 403). For compile we only need the NMS classes in the
# jar, so we install with a minimal local POM and no dependencies.
#
# https://repo.codemc.io/repository/nms/
set -euo pipefail

CODEMC_NMS="${CODEMC_NMS:-https://repo.codemc.io/repository/nms}"
M2_REPO="${HOME}/.m2/repository"
WORK_DIR="${NMS_WORK_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.nms-build/downloads}"
NMS_GROUP="${NMS_GROUP:-all}"
# Force reinstall even if jar exists (cleans bad POMs left by earlier CI runs)
NMS_FORCE="${NMS_FORCE:-1}"

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

artifact_dir() {
  local group_path="$1"
  local artifact="$2"
  local version="$3"
  echo "${M2_REPO}/${group_path}/${artifact}/${version}"
}

has_clean_install() {
  local group_path="$1"
  local artifact="$2"
  local version="$3"
  local dir
  dir="$(artifact_dir "${group_path}" "${artifact}" "${version}")"
  [[ -d "${dir}" ]] || return 1

  # Require a jar
  find "${dir}" -maxdepth 1 -type f \( -name "${artifact}-${version}.jar" -o -name "${artifact}-*.jar" \) \
    ! -name "*-sources.jar" ! -name "*-javadoc.jar" ! -name "*-tests.jar" | grep -q . || return 1

  # Reject installs whose POM still has parent/dependencies (old/bad installs)
  local pom
  pom="$(find "${dir}" -maxdepth 1 -type f -name "${artifact}-${version}.pom" | head -n1 || true)"
  if [[ -z "${pom}" || ! -f "${pom}" ]]; then
    return 1
  fi
  if grep -Eq '<parent>|<dependencies>' "${pom}"; then
    return 1
  fi
  return 0
}

write_minimal_pom() {
  local out="$1"
  local groupId="$2"
  local artifactId="$3"
  local version="$4"
  cat > "${out}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>${groupId}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>${version}</version>
  <packaging>jar</packaging>
  <description>SWM CI/local stub POM — jar only, no transitive deps</description>
</project>
EOF
}

install_from_codemc() {
  local groupId="$1"
  local artifactId="$2"
  local version="$3"
  local group_path
  group_path="$(echo "${groupId}" | tr '.' '/')"
  local dir
  dir="$(artifact_dir "${group_path}" "${artifactId}" "${version}")"

  if [[ "${NMS_FORCE}" != "1" ]] && has_clean_install "${group_path}" "${artifactId}" "${version}"; then
    echo "OK (cached clean) ${groupId}:${artifactId}:${version}"
    return 0
  fi

  # Remove previous install so Maven cannot reuse a bad POM with papermc parent/repos
  if [[ -d "${dir}" ]]; then
    echo "Removing previous install ${dir}"
    rm -rf "${dir}"
  fi

  local base="${CODEMC_NMS}/${group_path}/${artifactId}/${version}"
  local meta_file="${WORK_DIR}/${artifactId}-${version}-maven-metadata.xml"
  echo "Fetching metadata ${groupId}:${artifactId}:${version}..."
  download "${base}/maven-metadata.xml" "${meta_file}"

  local snap_value
  snap_value="$(python3 - "${meta_file}" <<'PY' || true
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for sv in root.findall(".//snapshotVersion"):
    ext = sv.findtext("extension")
    classifier = sv.findtext("classifier")
    if ext == "jar" and (classifier is None or classifier == ""):
        print(sv.findtext("value") or "")
        break
else:
    ver = root.findtext("./version") or root.findtext("./versioning/release") or ""
    print(ver)
PY
)"

  if [[ -z "${snap_value}" ]]; then
    snap_value="$(grep -A4 '<extension>jar</extension>' "${meta_file}" | grep -m1 '<value>' | sed -E 's/.*<value>([^<]+)<.*/\1/' || true)"
  fi
  if [[ -z "${snap_value}" ]]; then
    snap_value="${version}"
  fi

  local jar_name="${artifactId}-${snap_value}.jar"
  local jar_file="${WORK_DIR}/${jar_name}"
  local pom_file="${WORK_DIR}/${artifactId}-${version}-minimal.pom"

  echo "Downloading ${jar_name}..."
  download "${base}/${jar_name}" "${jar_file}"

  write_minimal_pom "${pom_file}" "${groupId}" "${artifactId}" "${version}"

  echo "Installing ${groupId}:${artifactId}:${version} with dependency-free POM..."
  mvn -q install:install-file \
    -Dfile="${jar_file}" \
    -DpomFile="${pom_file}" \
    -DgroupId="${groupId}" \
    -DartifactId="${artifactId}" \
    -Dversion="${version}" \
    -Dpackaging=jar

  if ! has_clean_install "${group_path}" "${artifactId}" "${version}"; then
    echo "ERROR: clean install failed for ${groupId}:${artifactId}:${version}" >&2
    echo "Installed POM contents:" >&2
    find "${dir}" -name "*.pom" -exec cat {} \; >&2 || true
    return 1
  fi
  echo "OK ${groupId}:${artifactId}:${version}"
}

install_legacy() {
  echo "=== legacy NMS (1.8.8 – 1.16.5) via CodeMC jar + minimal POM ==="
  local versions=(
    "1.8.8-R0.1-SNAPSHOT"
    "1.9-R0.1-SNAPSHOT"
    "1.9.4-R0.1-SNAPSHOT"
    "1.10.2-R0.1-SNAPSHOT"
    "1.11.2-R0.1-SNAPSHOT"
    "1.12.2-R0.1-SNAPSHOT"
    "1.13-R0.1-SNAPSHOT"
    "1.13.2-R0.1-SNAPSHOT"
    "1.14.4-R0.1-SNAPSHOT"
    "1.15.1-R0.1-SNAPSHOT"
    "1.16.1-R0.1-SNAPSHOT"
    "1.16.3-R0.1-SNAPSHOT"
    "1.16.5-R0.1-SNAPSHOT"
  )
  local v
  for v in "${versions[@]}"; do
    install_from_codemc org.spigotmc spigot "${v}"
  done
}

install_modern() {
  echo "=== modern NMS (1.17.1) via CodeMC jar + minimal POM ==="
  install_from_codemc org.spigotmc spigot "1.17.1-R0.1-SNAPSHOT"
}

echo "CodeMC NMS base: ${CODEMC_NMS}"
echo "NMS_GROUP=${NMS_GROUP}"
echo "NMS_FORCE=${NMS_FORCE}"

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

echo "All requested NMS artifacts are installed (jars only, no papermc transitive deps)."
