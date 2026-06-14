#!/usr/bin/env python3
import base64
import hmac
import json
import os
import shutil
import signal
import subprocess
import tempfile
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


TOKEN = os.environ.get("AGENT_SPACE_TOKEN", "").strip()
HOST = os.environ.get("AGENT_SPACE_HOST", "0.0.0.0")
PORT = int(os.environ.get("AGENT_SPACE_PORT", "8080"))
TMP_ROOT = Path(os.environ.get("AGENT_SPACE_TMP_ROOT", "/workspace/tmp"))
DEFAULT_TIMEOUT_SECONDS = int(os.environ.get("AGENT_SPACE_DEFAULT_TIMEOUT_SECONDS", "30"))
MAX_TIMEOUT_SECONDS = int(os.environ.get("AGENT_SPACE_MAX_TIMEOUT_SECONDS", "120"))
OUTPUT_LIMIT_CHARS = int(os.environ.get("AGENT_SPACE_OUTPUT_LIMIT_CHARS", "20000"))
MAX_REQUEST_BYTES = int(os.environ.get("AGENT_SPACE_MAX_REQUEST_BYTES", str(128 * 1024)))
MAX_ARTIFACT_BYTES = int(os.environ.get("AGENT_SPACE_MAX_ARTIFACT_BYTES", str(25 * 1024 * 1024)))


def clamp_timeout(value):
    try:
        requested = int(value)
    except (TypeError, ValueError):
        requested = DEFAULT_TIMEOUT_SECONDS
    return max(1, min(requested, MAX_TIMEOUT_SECONDS))


def truncate_output(value):
    if len(value) <= OUTPUT_LIMIT_CHARS:
        return value
    return value[:OUTPUT_LIMIT_CHARS] + "\n[output truncated]"


def command_for(mode, code, workspace):
    if mode == "shell":
        return ["/bin/bash", "-lc", code]
    if mode == "python":
        script = workspace / "script.py"
        script.write_text(code, encoding="utf-8")
        return ["python3", str(script)]
    if mode == "node":
        script = workspace / "script.js"
        script.write_text(code, encoding="utf-8")
        return ["node", str(script)]
    raise ValueError("Unsupported mode. Use one of: shell, python, node.")


def sandbox_env(workspace):
    return {
        "HOME": str(workspace),
        "PATH": "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "LANG": os.environ.get("LANG", "en_US.UTF-8"),
        "LC_ALL": os.environ.get("LC_ALL", "en_US.UTF-8"),
        "TMPDIR": str(workspace),
    }


def run_code(payload):
    mode = str(payload.get("mode", "")).strip().lower()
    code = payload.get("code")
    if not isinstance(code, str) or not code.strip():
        raise ValueError("Field 'code' must be a non-empty string.")
    artifact_paths = payload.get("artifactPaths") or []
    if not isinstance(artifact_paths, list) or not all(isinstance(path, str) for path in artifact_paths):
        raise ValueError("Field 'artifactPaths' must be a list of relative file paths.")

    timeout_seconds = clamp_timeout(payload.get("timeoutSeconds"))
    start = time.monotonic()
    workspace = Path(tempfile.mkdtemp(prefix="run-", dir=TMP_ROOT))
    timed_out = False
    exit_code = -1
    stdout = ""
    stderr = ""

    try:
        command = command_for(mode, code, workspace)
        process = subprocess.Popen(
            command,
            cwd=workspace,
            env=sandbox_env(workspace),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            start_new_session=True,
        )
        try:
            stdout, stderr = process.communicate(timeout=timeout_seconds)
            exit_code = process.returncode
        except subprocess.TimeoutExpired:
            timed_out = True
            os.killpg(process.pid, signal.SIGKILL)
            stdout, stderr = process.communicate()

        return {
            "exitCode": exit_code,
            "stdout": truncate_output(stdout),
            "stderr": truncate_output(stderr),
            "timedOut": timed_out,
            "durationMs": int((time.monotonic() - start) * 1000),
            "artifacts": collect_artifacts(workspace, artifact_paths),
        }
    finally:
        shutil.rmtree(workspace, ignore_errors=True)


def collect_artifacts(workspace, artifact_paths):
    artifacts = []
    total_size = 0
    workspace_resolved = workspace.resolve()
    for raw_path in artifact_paths:
        relative_path = raw_path.strip()
        if not relative_path:
            raise ValueError("Artifact path must not be empty.")
        artifact_path = Path(relative_path)
        if artifact_path.is_absolute() or ".." in artifact_path.parts:
            raise ValueError(f"Artifact path must stay inside the workspace: {relative_path}")
        resolved = (workspace / artifact_path).resolve()
        if not resolved.is_relative_to(workspace_resolved):
            raise ValueError(f"Artifact path must stay inside the workspace: {relative_path}")
        if not resolved.exists():
            raise ValueError(f"Artifact file not found: {relative_path}")
        if not resolved.is_file():
            raise ValueError(f"Artifact path is not a file: {relative_path}")
        size = resolved.stat().st_size
        total_size += size
        if total_size > MAX_ARTIFACT_BYTES:
            raise ValueError(f"Artifact files exceed {MAX_ARTIFACT_BYTES} bytes.")
        artifacts.append({
            "path": relative_path,
            "sizeBytes": size,
            "base64": base64.b64encode(resolved.read_bytes()).decode("ascii"),
        })
    return artifacts


def capabilities():
    return {
        "runtimes": {
            "shell": "bash command executed in a fresh ephemeral workspace",
            "python": "python3 script executed in a fresh ephemeral workspace",
            "node": "node script executed in a fresh ephemeral workspace",
        },
        "fileCreationInstructions": [
            "Create files only inside the current working directory of the run.",
            "Use a simple relative file name or relative path, for example assistant-document.docx or output/result.csv.",
            "Do not write files to absolute paths, parent directories, /workspace/output, /tmp, or the host filesystem.",
            "Every file that must be returned to the application must be listed exactly in artifactPaths in the same /run request.",
            "After writing the file, verify it exists and is non-empty before the process exits.",
            "Print a short verification line to stdout with the path and byte size so the caller can show or log the result.",
        ],
        "artifactReturnContract": {
            "requestField": "artifactPaths",
            "pathRules": "artifactPaths must contain non-empty relative file paths inside the run workspace; absolute paths and .. are rejected.",
            "responseField": "artifacts",
            "responseData": "Each returned artifact contains path, sizeBytes, and base64 file content.",
            "failureBehavior": "If a requested artifact path does not exist or is not a file, /run returns an error instead of silently succeeding.",
        },
        "cliCategories": [
            "shell and text processing",
            "Python data science and document processing",
            "Node.js tooling",
            "archive, OCR, media, and SQLite utilities",
        ],
        "filesystemPolicy": "Each run starts in a new temp directory under /workspace/tmp and is deleted after the run. Files survive only when their relative paths are requested through artifactPaths and returned in artifacts as base64. No app, repo, DB, MinIO, Docker socket, or host volumes are mounted.",
        "networkPolicy": "Outbound internet may be available through Docker networking. The sandbox must not be attached to the app/database/storage network.",
        "limits": {
            "defaultTimeoutSeconds": DEFAULT_TIMEOUT_SECONDS,
            "maxTimeoutSeconds": MAX_TIMEOUT_SECONDS,
            "outputLimitCharsPerStream": OUTPUT_LIMIT_CHARS,
            "maxRequestBytes": MAX_REQUEST_BYTES,
            "maxArtifactBytes": MAX_ARTIFACT_BYTES,
        },
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "AgentSpaceRunner/1.0"

    def do_GET(self):
        if self.path == "/health":
            if not self.authorized():
                self.write_json({"status": "unauthorized"}, status=401)
                return
            self.write_json({"status": "ok"})
            return
        if self.path == "/capabilities":
            if not self.authorized():
                self.write_json({"error": "unauthorized"}, status=401)
                return
            self.write_json(capabilities())
            return
        self.write_json({"error": "not found"}, status=404)

    def do_POST(self):
        if self.path != "/run":
            self.write_json({"error": "not found"}, status=404)
            return
        if not self.authorized():
            self.write_json({"error": "unauthorized"}, status=401)
            return
        try:
            self.write_json(run_code(self.read_json()))
        except ValueError as error:
            self.write_json({"error": str(error)}, status=400)
        except Exception as error:
            self.write_json({"error": f"{error.__class__.__name__}: {error}"}, status=500)

    def read_json(self):
        length = int(self.headers.get("Content-Length") or "0")
        if length <= 0:
            raise ValueError("Request body is empty.")
        if length > MAX_REQUEST_BYTES:
            raise ValueError(f"Request body exceeds {MAX_REQUEST_BYTES} bytes.")
        payload = json.loads(self.rfile.read(length).decode("utf-8"))
        if not isinstance(payload, dict):
            raise ValueError("Request body must be a JSON object.")
        return payload

    def authorized(self):
        if not TOKEN:
            return False
        return hmac.compare_digest(self.headers.get("X-Agent-Space-Token", ""), TOKEN)

    def write_json(self, payload, status=200):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, format, *args):
        print(f"{self.address_string()} - {format % args}", flush=True)


def main():
    if not TOKEN:
        raise SystemExit("AGENT_SPACE_TOKEN must be set")
    TMP_ROOT.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"agent_space listening on {HOST}:{PORT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
