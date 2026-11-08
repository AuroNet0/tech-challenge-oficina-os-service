import argparse
import importlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET


REPOSITORY = "AuroNet0/tech-challenge-oficina-os-service"
INFRA_REPOSITORY = "AuroNet0/tech-challenge-oficina-k8s-infra"
INFRA_REVISION = "02f08249276f9148fbbb1305a321d7056f3d9c0b"
POSTGRES = "postgres:17-alpine@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b"
RABBITMQ = "rabbitmq:4-management-alpine@sha256:0452ee3fd3b9a74160b251e693338616b464f22df4d4ba43304c101bebdca292"


class GateFailure(RuntimeError):
    pass


def require(condition, code):
    if not condition:
        raise GateFailure(code)


def command(arguments, cwd=None, timeout=1200):
    result = subprocess.run(arguments, cwd=cwd, capture_output=True, timeout=timeout)
    require(result.returncode == 0, "COMMAND_FAILED")
    return result.stdout


def validate_checkout(path, repository, revision):
    require(re.fullmatch(r"[0-9a-f]{40}", revision) is not None, "FULL_SHA_REQUIRED")
    prefix = ["git", "-C", str(path)]
    origin = command(prefix + ["remote", "get-url", "origin"]).decode().strip()
    require(origin in {f"https://github.com/{repository}", f"https://github.com/{repository}.git"}, "ORIGIN_REJECTED")
    require(command(prefix + ["rev-parse", "HEAD"]).decode().strip() == revision, "REVISION_MISMATCH")
    require(not command(prefix + ["status", "--porcelain", "--untracked-files=all"]), "DIRTY_CHECKOUT")


def load_guard(infra):
    validate_checkout(infra, INFRA_REPOSITORY, INFRA_REVISION)
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(infra / "scripts/platform-secrets"))
    return importlib.import_module("check_secret_files")


def image_contract(inspected):
    config = inspected["Config"]
    require(config.get("User") == "10001:10001", "RUNTIME_UID_REJECTED")
    require(config.get("Entrypoint") == ["java", "-jar", "/app/app.jar"], "ENTRYPOINT_REJECTED")
    require(config.get("StopSignal") == "SIGTERM", "STOP_SIGNAL_REJECTED")
    health = config.get("Healthcheck", {}).get("Test", [])
    require(health and health[0] == "CMD-SHELL" and
            "http://127.0.0.1:8080/actuator/health/readiness" in " ".join(health), "HEALTHCHECK_REJECTED")


def literal_secret(name, content):
    if re.search(rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b", content):
        return True
    if Path(name).suffix.lower() not in {".json", ".yaml", ".yml", ".properties", ".conf", ".ini", ".xml"}:
        return False
    assignments = re.finditer(
        rb'''(?im)(?:^|(?<!\$)[,{])[ \t]*["']?(?:[A-Za-z0-9_.-]*[_.-])?(?:password|passwd|token|secret|api_key|access_key|secret_key|aws_secret_access_key|aws_session_token)["']?[ \t]*[:=][ \t]*(?:"([^"\r\n]*)"|'([^'\r\n]*)'|([^\s,#}\r\n]+))''',
        content)
    for match in assignments:
        value = next(value for value in match.groups() if value is not None)
        if value not in {b"", b"null", b"~"} and not value.startswith(b"${"):
            return True
    return re.search(rb"(?is)<(?:password|token|secret)>\s*(?!\$\{)[^\s<][^<]*</(?:password|token|secret)>", content) is not None


def basic_secret_scan(repository, guard):
    paths = command(["git", "-C", str(repository), "ls-files", "--cached", "--others", "--exclude-standard", "-z"]).decode().split("\0")
    paths = sorted(set(paths) - {""})
    require(guard.scan(repository, paths=paths) == 0, "SOURCE_SCAN_REJECTED")
    checked = 0
    for name in paths:
        path = repository / name
        if path.is_file():
            require(not literal_secret(name, path.read_bytes()), "LITERAL_SECRET_REJECTED")
            checked += 1
    return {"status": "passed", "files": checked}


def maven_evidence(repository):
    totals = {field: 0 for field in ("tests", "failures", "errors", "skipped")}
    for folder in ("surefire-reports", "failsafe-reports"):
        for path in (repository / "target" / folder).glob("TEST-*.xml"):
            suite = ET.parse(path).getroot()
            require(suite.tag == "testsuite", "TEST_REPORT_REJECTED")
            for field in totals:
                totals[field] += int(suite.get(field, "0"))
    require(totals["tests"] > 0, "TEST_REPORT_MISSING")
    report = ET.parse(repository / "target/site/jacoco/jacoco.xml").getroot()
    counters = [counter for counter in report.findall("counter") if counter.get("type") == "LINE"]
    require(len(counters) == 1, "LINE_COVERAGE_MISSING")
    covered, missed = (int(counters[0].get(field)) for field in ("covered", "missed"))
    require(covered >= 0 and missed >= 0 and covered + missed > 0, "LINE_COVERAGE_INVALID")
    return {**totals, "line_covered": covered, "line_missed": missed,
            "line_percent": 100 * covered / (covered + missed)}


def quality_gate(quality):
    require(all(quality[field] == 0 for field in ("failures", "errors", "skipped")), "TESTS_NOT_APPROVED")
    require(quality["line_percent"] >= 80, "COVERAGE_BELOW_80")


def runtime(tag):
    prefix = "os-ci-" + uuid.uuid4().hex[:12]
    database, broker, application = [prefix + suffix for suffix in ("-db", "-mq", "-app")]
    password = uuid.uuid4().hex
    containers = []
    network_created = False
    cleanup_failed = False
    try:
        command(["docker", "network", "create", "--internal", prefix])
        network_created = True
        for name, image, variables in [
            (database, POSTGRES, {"POSTGRES_DB": "oficina_os", "POSTGRES_USER": "oficina_os", "POSTGRES_PASSWORD": password}),
            (broker, RABBITMQ, {"RABBITMQ_DEFAULT_USER": "ci", "RABBITMQ_DEFAULT_PASS": password,
                               "RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS": "+S 2:2"}),
        ]:
            arguments = ["docker", "run", "-d", "--name", name, "--hostname", name, "--network", prefix]
            for key, value in variables.items():
                arguments.extend(["-e", key + "=" + value])
            command(arguments + [image])
            containers.append(name)
        deadline = time.monotonic() + 120
        while True:
            database_ready = subprocess.run(["docker", "exec", database, "pg_isready", "-U", "oficina_os"], capture_output=True, timeout=15)
            broker_ready = subprocess.run(["docker", "exec", "--user", "rabbitmq", broker, "rabbitmq-diagnostics", "-q", "ping"], capture_output=True, timeout=30)
            if database_ready.returncode == broker_ready.returncode == 0:
                break
            require(time.monotonic() < deadline, "RUNTIME_DEPENDENCIES_TIMEOUT")
            time.sleep(2)
        variables = {
            "DB_HOST": database, "DB_NAME": "oficina_os", "DB_USERNAME": "oficina_os", "DB_PASSWORD": password,
            "RABBITMQ_HOST": broker, "RABBITMQ_USERNAME": "ci", "RABBITMQ_PASSWORD": password,
            "AUTH_JWT_ISSUER": "http://auth.invalid", "AUTH_JWT_AUDIENCE": "oficina",
            "AUTH_JWKS_URI": "http://auth.invalid/jwks", "AWS_EC2_METADATA_DISABLED": "true",
        }
        arguments = ["docker", "run", "-d", "--name", application, "--network", prefix]
        for key, value in variables.items():
            arguments.extend(["-e", key + "=" + value])
        command(arguments + [tag])
        containers.append(application)
        deadline = time.monotonic() + 180
        while True:
            state = json.loads(command(["docker", "inspect", application]))[0]["State"]
            require(state["Running"], "APPLICATION_EXITED")
            if state.get("Health", {}).get("Status") == "healthy":
                break
            require(time.monotonic() < deadline, "HEALTHCHECK_TIMEOUT")
            time.sleep(3)
        require(command(["docker", "exec", application, "id", "-u"]).strip() == b"10001", "RUNTIME_UID_REJECTED")
        java = subprocess.run(["docker", "exec", application, "java", "-version"], capture_output=True, timeout=30)
        require(java.returncode == 0 and re.search(rb'version "21[.\"]', java.stdout + java.stderr), "RUNTIME_JAVA_21_REQUIRED")
        for endpoint in ("health", "health/liveness", "health/readiness"):
            response = command(["docker", "exec", application, "wget", "-q", "-O", "-", "http://127.0.0.1:8080/actuator/" + endpoint])
            require(json.loads(response).get("status") == "UP", "HEALTH_ENDPOINT_REJECTED")
        command(["docker", "stop", "--time", "30", application], timeout=45)
        state = json.loads(command(["docker", "inspect", application]))[0]["State"]
        require(not state["Running"] and not state.get("OOMKilled") and state["ExitCode"] in {0, 143}, "SIGTERM_REJECTED")
        return {"health": "UP", "uid": 10001, "sigterm_exit_code": state["ExitCode"]}
    finally:
        for name in reversed(containers):
            try:
                command(["docker", "rm", "-f", "-v", name], timeout=60)
            except Exception:
                cleanup_failed = True
        if network_created:
            try:
                command(["docker", "network", "rm", prefix], timeout=60)
            except Exception:
                cleanup_failed = True
        require(not cleanup_failed, "RUNTIME_CLEANUP_FAILED")


def verify(repository, infra, revision, report):
    report["stage"] = "checkout"
    require(command(["git", "-C", str(repository), "rev-parse", "HEAD"]).decode().strip() == revision, "REVISION_MISMATCH")
    report["checks"]["checkout"] = "passed"
    report["stage"] = "basic_secrets"
    guard = load_guard(infra)
    report["basic_secrets"] = basic_secret_scan(repository, guard)
    report["checks"]["basic_secrets"] = "passed"
    report["stage"] = "java"
    version = subprocess.run(["java", "-version"], capture_output=True, timeout=30)
    require(version.returncode == 0 and re.search(rb'version "21[.\"]', version.stdout + version.stderr), "JAVA_21_REQUIRED")
    report["checks"]["java"] = "passed"
    report["stage"] = "maven"
    wrapper = repository / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    maven = [str(wrapper)] if os.name == "nt" else ["sh", str(wrapper)]
    command(maven + ["-B", "-ntp", "clean", "verify"], cwd=repository)
    report["checks"]["maven"] = "passed"
    report["stage"] = "tests_and_coverage"
    quality = maven_evidence(repository)
    report["maven"] = quality
    quality_gate(quality)
    report["checks"]["tests_and_coverage"] = "passed"
    report["stage"] = "docker_build"
    tag = "oficina-os-ci:" + revision
    command(["docker", "build", "--platform=linux/amd64",
             "--label", "org.opencontainers.image.source=https://github.com/" + REPOSITORY,
             "--label", "org.opencontainers.image.revision=" + revision, "-t", tag, "."], cwd=repository)
    inspected = json.loads(command(["docker", "image", "inspect", tag]))[0]
    require(inspected["Os"] == "linux" and inspected["Architecture"] == "amd64", "PLATFORM_REJECTED")
    image_contract(inspected)
    report["image"] = {"tag": tag, "platform": "linux/amd64", "uid": 10001}
    report["checks"]["docker_build"] = "passed"
    report["stage"] = "runtime"
    report["runtime"] = runtime(tag)
    report["checks"]["runtime"] = "passed"
    report["stage"] = "complete"


def main():
    parser = argparse.ArgumentParser()
    for name in ("repository", "infra", "revision", "output"):
        parser.add_argument("--" + name, required=True)
    options = parser.parse_args()
    output = Path(options.output).resolve()
    require(not output.exists(), "OUTPUT_ALREADY_EXISTS")
    reports = output / "reports"
    reports.mkdir(parents=True)
    report = {"repository": REPOSITORY, "revision": options.revision, "status": "failed", "stage": "initialization", "failures": [],
              "checks": {name: "not_run" for name in ("checkout", "basic_secrets", "java", "maven", "tests_and_coverage", "docker_build", "runtime")},
              "third_party_binary_scan": {"required": False, "status": "not_run"}}
    try:
        verify(Path(options.repository).resolve(), Path(options.infra).resolve(), options.revision, report)
        report["status"] = "passed"
    except Exception as error:
        known = isinstance(error, GateFailure)
        code = str(error) if known and re.fullmatch(r"[A-Z][A-Z0-9_]{1,80}", str(error)) else "VERIFICATION_FAILED"
        if report["stage"] in report["checks"]:
            report["checks"][report["stage"]] = "failed"
        report["failures"].append(code)
    finally:
        (reports / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Productive verification: " + report["status"] + "; stage=" + report["stage"])
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
