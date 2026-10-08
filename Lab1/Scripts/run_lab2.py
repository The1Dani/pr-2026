#!/usr/bin/env python3
import subprocess
import os

def run(*command: str) -> str:
    """Run a command, streaming its output live and returning it as a string."""
    lines: list[str] = []
    with subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
    ) as p:
        assert p.stdout is not None
        for line in p.stdout:
            print(line, end="")
            lines.append(line)

    if p.returncode != 0:
        raise RuntimeError(f"{' '.join(command)} failed (exit {p.returncode})")

    return "".join(lines)

# Note that before running you need to copy the numbers.*.txt to root of the drive
# When run the drive should be unmounted
def main():
    user = os.environ.get("SUDO_USER")
    as_user = ["sudo", "-u", user] if user else []

    MOUNT_LOCATION = "/mnt/usb-drive"
    MOUNT_DEVICE = "/dev/sda"
    for i in [1, 2, 4]:
        run("mount", "-m", MOUNT_DEVICE, MOUNT_LOCATION)
        run(*as_user, "./run.sh", MOUNT_LOCATION, str(i))
        run("umount", MOUNT_LOCATION)


if __name__ == "__main__":
    main()
