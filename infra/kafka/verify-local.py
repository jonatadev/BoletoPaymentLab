"""Exercise Kafka hardening in a new Compose project with synthetic data."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[2]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("migration", "fresh"), default="migration")
    parser.add_argument("--port", type=int, default=19092)
    parser.add_argument("--openssl", default="openssl")
    args = parser.parse_args()
    project = "bpl-kafka-it-" + uuid.uuid4().hex[:10]
    environment = dict(os.environ, KAFKA_PORT=str(args.port), KAFKA_SECRET_INSTANCE=project)
    # Compose interpolates the whole file even when only Kafka services start.
    environment["KC_BOOTSTRAP_ADMIN_USERNAME"] = "fixture-admin"
    environment["KC_BOOTSTRAP_ADMIN_PASSWORD"] = "synthetic-local-only"

    def run(*command, capture=False):
        return subprocess.run(command, check=True, cwd=ROOT, env=environment, text=True,
                              stdout=subprocess.PIPE if capture else None).stdout

    def compose(*command, capture=False):
        return run("docker", "compose", "--env-file", ".env.example", "-p", project,
                   "-f", "compose.yaml", "-f", "infra/kafka/security-test.yaml", *command, capture=capture)

    def legacy(*command):
        run("docker", "compose", "-p", project, "-f", "infra/kafka/legacy-test.yaml", *command)

    def provision(*command, capture=False):
        return compose("run", "--rm", "--no-deps", "kafka-provision", *command, capture=capture)

    def snapshot(mode):
        output = provision("KafkaSecurityChecks", mode, capture=True)
        snapshots = [line for line in output.splitlines() if line.startswith("KAF-11 SNAPSHOT ")]
        if len(snapshots) != 1:
            raise RuntimeError("Expected one migration snapshot.")
        return snapshots[0]

    run(sys.executable, "infra/kafka/generate-local.py", "--instance", project, "--openssl", args.openssl)
    compose("build", "kafka-provision")
    print(f"Isolated Compose project {project}", flush=True)

    before = None
    if args.mode == "migration":
        legacy("up", "-d", "--wait", "--wait-timeout", "240", "kafka")
        before = snapshot("legacy")
        legacy("stop", "kafka")

    compose("up", "-d", "--force-recreate", "--wait", "--wait-timeout", "240", "kafka")
    provision()
    if before is not None and before != snapshot("snapshot"):
        raise RuntimeError("The upgraded Kafka snapshot differs from the legacy snapshot.")

    compose("restart", "kafka")
    compose("up", "-d", "--wait", "--wait-timeout", "240", "kafka")
    provision()
    if before is not None and before != snapshot("snapshot"):
        raise RuntimeError("The Kafka snapshot changed after restart.")

    print("KAF-11 restart and reprovisioning passed"
          + (" with the original cluster ID, topic ID, message and offset." if before else "."), flush=True)
    provision("KafkaSecurityChecks")
    print(f"Project {project} remains available at 127.0.0.1:{args.port} for additional tests.")

if __name__ == "__main__":
    main()
