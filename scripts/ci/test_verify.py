import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("verify", Path(__file__).with_name("verify.py"))
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)


def quality(percent=90):
    return {"tests": 5, "failures": 0, "errors": 0, "skipped": 0, "line_percent": percent}


def image():
    return {"Os": "linux", "Architecture": "amd64", "Config": {
        "User": "10001:10001", "Entrypoint": ["java", "-jar", "/app/app.jar"], "StopSignal": "SIGTERM",
        "Healthcheck": {"Test": ["CMD-SHELL", "wget http://127.0.0.1:8080/actuator/health/readiness"]}}}


class VerificationTests(unittest.TestCase):
    def test_command_failure_never_exposes_output(self):
        result = subprocess.CompletedProcess([], 7, b"sensitive stdout", b"sensitive stderr")
        with patch.object(verify.subprocess, "run", return_value=result):
            with self.assertRaisesRegex(verify.GateFailure, "^COMMAND_FAILED$"):
                verify.command(["tool"])

    def test_guard_is_not_imported_before_checkout_validation(self):
        with patch.object(verify, "validate_checkout", side_effect=verify.GateFailure("REVISION_MISMATCH")), patch.object(verify.importlib, "import_module") as imported:
            with self.assertRaises(verify.GateFailure):
                verify.load_guard(Path("infra"))
            imported.assert_not_called()

    def test_pinned_guard_origin_and_revision_are_checked(self):
        revision = "a" * 40
        cases = [
            ([b"https://github.com/foreign/repository.git\n"], "ORIGIN_REJECTED"),
            ([b"https://github.com/" + verify.INFRA_REPOSITORY.encode() + b".git\n", b"b" * 40], "REVISION_MISMATCH"),
        ]
        for responses, code in cases:
            with self.subTest(code=code), patch.object(verify, "command", side_effect=responses):
                with self.assertRaisesRegex(verify.GateFailure, "^" + code + "$"):
                    verify.validate_checkout(Path("infra"), verify.INFRA_REPOSITORY, revision)

    def test_literal_credentials_in_configs_are_rejected(self):
        examples = [
            ("application.yml", b"password: accidental_value"),
            ("compose.yaml", b"  DB_PASSWORD: accidental_value"),
            ("config.json", b'{"token":"accidental_value"}'),
            ("config.properties", b"aws_secret_access_key=accidental_value"),
            ("settings.xml", b"<password>accidental_value</password>"),
            ("source.java", b"ghp_" + b"Z" * 36),
        ]
        for name, content in examples:
            with self.subTest(name=name):
                self.assertTrue(verify.literal_secret(name, content))

    def test_environment_references_and_empty_config_values_are_accepted(self):
        for content in (b"password: ${DB_PASSWORD}", b"DB_PASSWORD: ${DB_PASSWORD:-local_default}",
                        b'{"token":""}', b"password: null", b"password:\nother: value",
                        b"<password>${DB_PASSWORD}</password>",
                        b"RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-guest}",
                        b'{"value":"${TOKEN:-guest}"}'):
            with self.subTest(content=content):
                self.assertFalse(verify.literal_secret("config.yml", content))

    def test_source_guard_rejection_is_mandatory(self):
        class Guard:
            @staticmethod
            def scan(repository, paths):
                return 1
        with tempfile.TemporaryDirectory() as folder, patch.object(verify, "command", return_value=b"file.txt\0"):
            with self.assertRaisesRegex(verify.GateFailure, "SOURCE_SCAN_REJECTED"):
                verify.basic_secret_scan(Path(folder), Guard())

    def test_literal_secret_rejection_never_prints_the_value(self):
        class Guard:
            @staticmethod
            def scan(repository, paths):
                return 0
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "config.yml").write_text("password: accidental_value")
            with patch.object(verify, "command", return_value=b"config.yml\0"):
                with self.assertRaisesRegex(verify.GateFailure, "^LITERAL_SECRET_REJECTED$"):
                    verify.basic_secret_scan(root, Guard())

    def test_maven_counters_and_line_coverage_are_read(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            reports = root / "target/surefire-reports"
            coverage = root / "target/site/jacoco"
            reports.mkdir(parents=True)
            coverage.mkdir(parents=True)
            (reports / "TEST-fixture.xml").write_text('<testsuite tests="5" failures="0" errors="0" skipped="0"/>')
            (coverage / "jacoco.xml").write_text('<report><counter type="LINE" covered="80" missed="20"/></report>')
            evidence = verify.maven_evidence(root)
            self.assertEqual(evidence["tests"], 5)
            self.assertEqual(evidence["line_percent"], 80)
            verify.quality_gate(evidence)

    def test_tests_and_coverage_fail_closed(self):
        for field in ("failures", "errors", "skipped"):
            with self.subTest(field=field), self.assertRaisesRegex(verify.GateFailure, "TESTS_NOT_APPROVED"):
                verify.quality_gate({**quality(), field: 1})
        with self.assertRaisesRegex(verify.GateFailure, "COVERAGE_BELOW_80"):
            verify.quality_gate(quality(79.999))

    def test_missing_test_reports_fail(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(verify.GateFailure, "TEST_REPORT_MISSING"):
                verify.maven_evidence(Path(folder))

    def run_pipeline(self, output, responses=None, evidence=None, runtime_error=None):
        revision = "a" * 40
        responses = responses or [revision.encode(), b"", b"", json.dumps([image()]).encode()]
        arguments = ["verify.py", "--repository", "os", "--infra", "infra",
                     "--revision", revision, "--output", str(output)]
        java = subprocess.CompletedProcess([], 0, b"", b'openjdk version "21.0.13"')
        with patch.object(sys, "argv", arguments), patch.object(verify, "load_guard"), \
                patch.object(verify, "basic_secret_scan", return_value={"status": "passed", "files": 5}), \
                patch.object(verify, "maven_evidence", return_value=evidence or quality()), \
                patch.object(verify.subprocess, "run", return_value=java), \
                patch.object(verify, "runtime", side_effect=runtime_error, return_value={"health": "UP"}), \
                patch.object(verify, "command", side_effect=responses) as commands:
            status = verify.main()
        report = json.loads((output / "reports/verification.json").read_text())
        return status, report, commands.call_args_list

    def test_pipeline_succeeds_without_deep_binary_scan_or_receipts(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder) / "evidence"
            status, report, calls = self.run_pipeline(output)
            self.assertEqual(status, 0)
            self.assertTrue(all(value == "passed" for value in report["checks"].values()))
            self.assertEqual(report["third_party_binary_scan"], {"required": False, "status": "not_run"})
            self.assertEqual(calls[1].args[0][-4:], ["-B", "-ntp", "clean", "verify"])
            if verify.os.name != "nt":
                self.assertEqual(calls[1].args[0][0], "sh")
            self.assertEqual(calls[2].args[0][:3], ["docker", "build", "--platform=linux/amd64"])
            self.assertEqual(sorted(path.name for path in output.iterdir()), ["reports"])

    def test_maven_and_docker_failures_survive_report_collection(self):
        revision = b"a" * 40
        for responses, stage in (([revision, verify.GateFailure("COMMAND_FAILED")], "maven"),
                                 ([revision, b"", verify.GateFailure("COMMAND_FAILED")], "docker_build")):
            with self.subTest(stage=stage), tempfile.TemporaryDirectory() as folder:
                status, report, unused = self.run_pipeline(Path(folder) / "evidence", responses=responses)
                self.assertEqual(status, 1)
                self.assertEqual(report["checks"][stage], "failed")
                self.assertEqual(report["checks"]["runtime"], "not_run")

    def test_coverage_failure_prevents_docker_build(self):
        with tempfile.TemporaryDirectory() as folder:
            status, report, calls = self.run_pipeline(Path(folder) / "evidence", evidence=quality(79))
            self.assertEqual(status, 1)
            self.assertEqual(report["failures"], ["COVERAGE_BELOW_80"])
            self.assertEqual(report["checks"]["docker_build"], "not_run")
            self.assertEqual(len(calls), 2)

    def test_runtime_failure_is_sanitized_and_remains_a_failure(self):
        with tempfile.TemporaryDirectory() as folder:
            status, report, unused = self.run_pipeline(Path(folder) / "evidence", runtime_error=RuntimeError("sensitive details"))
            self.assertEqual(status, 1)
            self.assertEqual(report["checks"]["runtime"], "failed")
            self.assertEqual(report["failures"], ["VERIFICATION_FAILED"])
            self.assertNotIn("sensitive", json.dumps(report))

    def test_runtime_contract_is_preserved(self):
        config = image()["Config"]
        verify.image_contract({"Config": config})
        for changed in ({"User": "root"}, {"Healthcheck": {}}, {"StopSignal": "SIGKILL"}, {"Entrypoint": ["sh"]}):
            with self.subTest(changed=changed), self.assertRaises(verify.GateFailure):
                verify.image_contract({"Config": {**config, **changed}})


if __name__ == "__main__":
    unittest.main()
