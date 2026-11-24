"""Generate development-only Kafka identities and service-specific config mounts."""
import argparse
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import textwrap

ROOT = Path(__file__).resolve().parents[2]

def write(path, value):
    path.write_text(textwrap.dedent(value).lstrip(), encoding="ascii", newline="\n")
    path.chmod(0o600)

def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)

def generate(target):
    if os.name == "nt":
        run("icacls", str(target), "/inheritance:r", "/grant:r",
            os.environ["USERDOMAIN"] + "\\" + os.environ["USERNAME"] + ":(OI)(CI)F",
            "*S-1-5-18:(OI)(CI)F")
    ca, broker, admin, app, ui = (target / name for name in ("ca", "broker", "admin", "app", "ui"))
    for folder in (ca, broker, admin, app, ui):
        folder.mkdir(mode=0o700)

    ca_key, ca_cert = ca / "ca.key", ca / "ca.pem"
    run(OPENSSL, "req", "-x509", "-newkey", "rsa:3072", "-nodes", "-days", "365",
        "-subj", "/CN=Boleto Payment Lab Development CA",
        "-addext", "basicConstraints=critical,CA:TRUE,pathlen:0",
        "-addext", "keyUsage=critical,keyCertSign,cRLSign",
        "-keyout", str(ca_key), "-out", str(ca_cert))

    def issue(directory, name, extensions):
        password = secrets.token_hex(24)
        password_file = directory / "store-password"
        write(password_file, password)
        with tempfile.TemporaryDirectory(dir=ca) as directory_name:
            work = Path(directory_name)
            key, csr, cert, ext = (work / name for name in ("key", "csr", "cert", "ext"))
            write(ext, extensions)
            run(OPENSSL, "req", "-new", "-newkey", "rsa:3072", "-nodes",
                "-subj", "/CN=" + name, "-keyout", str(key), "-out", str(csr))
            run(OPENSSL, "x509", "-req", "-in", str(csr), "-CA", str(ca_cert),
                "-CAkey", str(ca_key), "-set_serial", "0x" + secrets.token_hex(16),
                "-days", "90", "-extfile", str(ext), "-out", str(cert))
            run(OPENSSL, "pkcs12", "-export", "-inkey", str(key), "-in", str(cert),
                "-certfile", str(ca_cert), "-out", str(directory / "identity.p12"),
                "-passout", "file:" + str(password_file))
        password_file.unlink()
        return password

    node_password = issue(broker, "kafka-node", """\
        basicConstraints=critical,CA:FALSE
        keyUsage=critical,digitalSignature,keyEncipherment
        extendedKeyUsage=serverAuth,clientAuth
        subjectAltName=DNS:kafka,DNS:localhost,IP:127.0.0.1
        """)
    admin_password = issue(admin, "kafka-security-admin", """\
        basicConstraints=critical,CA:FALSE
        keyUsage=critical,digitalSignature
        extendedKeyUsage=clientAuth
        """)
    for folder in (broker, admin, app, ui):
        shutil.copyfile(ca_cert, folder / "ca.pem")

    def tls_client(listener, password, timeout):
        return f"""\
            bootstrap.servers=kafka:{listener}
            security.protocol=SSL
            ssl.endpoint.identification.algorithm=https
            ssl.truststore.type=PEM
            ssl.truststore.location=/run/kafka/ca.pem
            ssl.keystore.type=PKCS12
            ssl.keystore.location=/run/kafka/identity.p12
            ssl.keystore.password={password}
            request.timeout.ms={timeout // 2}
            default.api.timeout.ms={timeout}
            """

    write(broker / "broker.properties", f"""\
        ssl.keystore.type=PKCS12
        ssl.keystore.location=/run/kafka/identity.p12
        ssl.keystore.password={node_password}
        ssl.truststore.type=PEM
        ssl.truststore.location=/run/kafka/ca.pem
        """)
    write(admin / "admin.properties", tls_client(29095, admin_password, 45000))

    for name, folder in (("svc-boleto-api", app), ("svc-kafka-ui", ui)):
        password = secrets.token_hex(32)
        write(admin / (name + ".password"), password)
        jaas = ('org.apache.kafka.common.security.scram.ScramLoginModule required '
                f'username="{name}" password="{password}";')
        if folder == app:
            write(app / "spring.yaml", f"""\
                spring:
                  kafka:
                    properties:
                      "[sasl.jaas.config]": '{jaas}'
                """)
        else:
            write(ui / "application.yaml", f"""\
                auth:
                  type: LOGIN_FORM
                spring:
                  security:
                    user:
                      name: kafka-observer
                      password: '{secrets.token_hex(20)}'
                kafka:
                  clusters:
                    - name: BoletoPaymentLab
                      bootstrap-servers: kafka:29092
                      read-only: true
                      properties:
                        security.protocol: SASL_SSL
                        sasl.mechanism: SCRAM-SHA-512
                        sasl.jaas.config: '{jaas}'
                        ssl.endpoint.identification.algorithm: https
                        ssl.truststore.type: PEM
                        ssl.truststore.location: /run/kafka/ca.pem
                """)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instance", default="local", help="Name under .kafka-secrets")
    parser.add_argument("--openssl", default="openssl", help="Path to OpenSSL executable")
    args = parser.parse_args()
    if not args.instance.isascii() or not args.instance.replace("-", "").isalnum():
        parser.error("instance must contain letters, numbers or hyphens")
    OPENSSL = args.openssl
    if os.name != "nt":
        os.umask(0o077)
    store = ROOT / ".kafka-secrets"
    store.mkdir(mode=0o700, exist_ok=True)
    destination = store / args.instance
    if destination.exists():
        parser.exit(message="Kafka material already exists. Existing files were preserved.\n")
    with tempfile.TemporaryDirectory(dir=store) as staging:
        material = Path(staging) / "material"
        material.mkdir(mode=0o700)
        generate(material)
        material.rename(destination)
    print("Development Kafka material generated.")
