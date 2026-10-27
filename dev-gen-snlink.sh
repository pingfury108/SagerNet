#!/bin/bash
# Generate a sn:// profile import link for SagerNet (gost profile).
# Usage: bash dev-gen-snlink.sh <name> <host> <port> <user> <pass> <mode>
#   mode: 0=relay+ssh 1=socks5+ssh 2=ssh 3=sshd
# Example (your upstream):
#   bash dev-gen-snlink.sh sg-sshd 149.129.41.181 8081 gost 'yourpass' 3
set -e
cd "$(dirname "$0")"

NAME="$1"; HOST="$2"; PORT="$3"; USER="$4"; PASS="$5"; MODE="${6:-3}"
[ $# -ge 5 ] || { echo "usage: $0 <name> <host> <port> <user> <pass> [mode=3]" >&2; exit 1; }

WORK=/tmp/snlink-gen
mkdir -p "$WORK"
cat > "$WORK/SnGen.java" <<'JAVA'
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import java.io.ByteArrayOutputStream;

public class SnGen {
    // args: name host port user pass mode outfile
    public static void main(String[] args) throws Exception {
        String name = args[0], host = args[1], user = args[3], pass = args[4];
        int port = Integer.parseInt(args[2]);
        int mode = Integer.parseInt(args[5]);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteBufferOutput out = new ByteBufferOutput(baos);

        out.writeInt(1);                     // bean format version 1 (+mode)
        out.writeString(host);               // serverAddress
        out.writeInt(port);                  // serverPort
        out.writeString(user);               // username
        out.writeInt(1);                     // authType = PASSWORD
        out.writeString(pass);               // password
        out.writeInt(mode);                  // gost mode

        out.writeInt(1);                     // extra version
        out.writeString(name);               // profile name
        out.writeInt(0);                     // extraType = NONE

        out.flush();
        java.io.FileOutputStream f = new java.io.FileOutputStream(args[6]);
        f.write(baos.toByteArray());
        f.close();
    }
}
JAVA

# locate kryo jar (gradle cache) and java (mise/system)
KYO=$(find ~/.gradle/caches/modules-2/files-2.1/com.esotericsoftware/kryo -name "kryo-5.3.0.jar" 2>/dev/null | head -1)
[ -n "$KYO" ] || { echo "kryo jar not found in ~/.gradle/caches" >&2; exit 1; }
export PATH="$HOME/.local/share/mise/installs/java/temurin-17/bin:$PATH"
command -v javac >/dev/null || { echo "javac not found" >&2; exit 1; }

javac -cp "$KYO" -d "$WORK" "$WORK/SnGen.java"
java -cp "$KYO:$WORK" SnGen "$NAME" "$HOST" "$PORT" "$USER" "$PASS" "$MODE" "$WORK/bean.bin"

python3 - "$WORK/bean.bin" <<'PY'
import zlib, base64, sys
data = open(sys.argv[1], "rb").read()
link = "sn://gost?" + base64.urlsafe_b64encode(zlib.compress(data, 9)).decode().rstrip("=")
print(link)
PY
