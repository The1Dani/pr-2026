#!/usr/bin/env python3
"""
End-to-end test for the CoffeeServer (C#) driven through the Java client.

The script starts the C# server, compiles the Java client, launches N client
processes and M provider processes, and talks to the server only by writing
commands to the Java programs' stdin (exactly what a person would type) and
reading what they print on stdout.

Usage (from anywhere):
    python3 Lab2/Scripts/test_clients.py                       # 3 clients, 1 provider
    python3 Lab2/Scripts/test_clients.py --clients 5 --providers 2
    python3 Lab2/Scripts/test_clients.py --no-server --port 4000   # use a server you started yourself
    python3 Lab2/Scripts/test_clients.py -v                    # also print every line the processes print

Needs: python3, a JDK (javac/java) and, unless --no-server, the .NET SDK (dotnet).
Exit code is 0 when every check passed, 1 otherwise.
"""
from __future__ import annotations

import argparse
import json
import re
import socket
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path
from typing import Callable

LAB2 = Path(__file__).resolve().parent.parent
JAVA_SRC = LAB2 / "src" / "main" / "java"
SERVER_DIR = LAB2 / "CoffeeServer"

PRODUCTS = ["Coffee", "Tea", "Croissant"]
PREP_TIME = {"Coffee": 4, "Tea": 3, "Croissant": 2}
RESTOCK_BELOW = 5      # server asks providers to restock when a product falls below this
RESTOCK_TO = 30        # ...topping every product under 30 back to 30
STOCK_CAP = 1000

ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")

VERBOSE = False
_print_lock = threading.Lock()


def log(text: str) -> None:
    with _print_lock:
        print(text, flush=True)


# --------------------------------------------------------------------------- #
# One Java process (client or provider) driven through stdin/stdout
# --------------------------------------------------------------------------- #
class JavaPeer:
    """Runs `java org.example.Main host port role` and lets us type into it."""

    def __init__(self, name: str, role: str, classpath: str, host: str, port: int):
        self.name = name
        self.role = role
        self.proc = subprocess.Popen(
            ["java", "-cp", classpath, "org.example.Main", host, str(port), role],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            bufsize=1,
        )
        self._lines: list[str] = []          # cleaned lines not yet consumed by expect()
        self._cond = threading.Condition()
        self._eof = False
        threading.Thread(target=self._pump, name=f"{name}-stdout", daemon=True).start()

    # ---- reading ---------------------------------------------------------- #
    @staticmethod
    def _clean(raw: str) -> str:
        """Removes the ANSI clear-line codes, the '\\r' redraws and the '> ' prompts."""
        text = ANSI.sub("", raw.rstrip("\n"))
        parts = [p for p in text.split("\r") if p.strip()]
        text = parts[-1] if parts else ""
        while text.startswith("> "):
            text = text[2:]
        return text.strip()

    def _pump(self) -> None:
        assert self.proc.stdout is not None
        for raw in self.proc.stdout:
            line = self._clean(raw)
            if not line or line == ">":
                continue
            if VERBOSE:
                log(f"    [{self.name}] {line}")
            with self._cond:
                self._lines.append(line)
                self._cond.notify_all()
        with self._cond:
            self._eof = True
            self._cond.notify_all()

    def clear(self) -> None:
        """Forgets everything printed so far (so old messages don't satisfy new checks)."""
        with self._cond:
            self._lines.clear()

    def expect(self, predicate: Callable[[str], bool], timeout: float, what: str) -> str:
        """Waits for a printed line matching `predicate`, removes it and returns it."""
        deadline = time.monotonic() + timeout
        with self._cond:
            while True:
                for i, line in enumerate(self._lines):
                    if predicate(line):
                        del self._lines[i]
                        return line
                remaining = deadline - time.monotonic()
                if remaining <= 0 or self._eof:
                    seen = "; ".join(self._lines[-5:]) or "nothing"
                    state = "process ended" if self._eof else f"timed out after {timeout:.0f}s"
                    raise AssertionError(f"{self.name}: expected {what}, {state} (last lines: {seen})")
                self._cond.wait(remaining)

    def expect_server(self, predicate: Callable[[dict], bool], timeout: float, what: str) -> dict:
        """Waits for a '[server] {json}' line whose JSON matches `predicate`."""
        def match(line: str) -> bool:
            msg = server_json(line)
            return msg is not None and predicate(msg)
        return server_json(self.expect(match, timeout, what))  # type: ignore[return-value]

    def expect_text(self, fragment: str, timeout: float = 5) -> str:
        return self.expect(lambda l: fragment in l, timeout, repr(fragment))

    # ---- writing ---------------------------------------------------------- #
    def type(self, command: str) -> None:
        """Writes one line to the Java program's stdin, like a user pressing Enter."""
        if VERBOSE:
            log(f"    [{self.name}] <<< {command}")
        assert self.proc.stdin is not None
        self.proc.stdin.write(command + "\n")
        self.proc.stdin.flush()

    # ---- higher level helpers -------------------------------------------- #
    def wait_connected(self, timeout: float = 15) -> None:
        self.expect_text("Connected to", timeout)
        # The help text is printed right after the role was sent.
        self.expect_text("quit", timeout)

    def menu(self, timeout: float = 5) -> dict[str, int]:
        self.type("menu")
        return self.expect_server(is_menu, timeout, "a menu")

    def quit(self, timeout: float = 10) -> int:
        if self.proc.poll() is None:
            try:
                self.type("quit")
            except (BrokenPipeError, OSError):
                pass
        try:
            return self.proc.wait(timeout)
        except subprocess.TimeoutExpired:
            self.proc.kill()
            return self.proc.wait()

    def alive(self) -> bool:
        return self.proc.poll() is None


def server_json(line: str) -> dict | None:
    if not line.startswith("[server] "):
        return None
    try:
        msg = json.loads(line[len("[server] "):])
    except json.JSONDecodeError:
        return None
    return msg if isinstance(msg, dict) else None


def is_menu(msg: dict) -> bool:
    return set(msg) == set(PRODUCTS)


def is_error(text: str | None = None) -> Callable[[dict], bool]:
    return lambda m: "Error" in m and (text is None or text in m["Error"])


def is_message(text: str) -> Callable[[dict], bool]:
    return lambda m: m.get("Message") == text


def order_time(items: dict[str, int]) -> int:
    return sum(PREP_TIME[p] * q for p, q in items.items())


def order_cmd(items: dict[str, int]) -> str:
    return "order " + " ".join(f"{p.lower()}={q}" for p, q in items.items())


# --------------------------------------------------------------------------- #
# Test runner
# --------------------------------------------------------------------------- #
class Results:
    def __init__(self) -> None:
        self.passed: list[str] = []
        self.failed: list[tuple[str, str]] = []
        self.skipped: list[tuple[str, str]] = []

    def run(self, name: str, fn: Callable[[], None]) -> None:
        log(f"\n=== {name}")
        start = time.monotonic()
        try:
            fn()
        except AssertionError as e:
            self.failed.append((name, str(e)))
            log(f"FAIL  {name}: {e}")
            return
        except Exception as e:  # unexpected crash inside the test itself
            self.failed.append((name, f"{type(e).__name__}: {e}"))
            log(f"FAIL  {name}: {type(e).__name__}: {e}")
            return
        self.passed.append(name)
        log(f"PASS  {name} ({time.monotonic() - start:.1f}s)")

    def skip(self, name: str, reason: str) -> None:
        self.skipped.append((name, reason))
        log(f"\n=== {name}\nSKIP  {reason}")

    def summary(self) -> bool:
        log("\n" + "=" * 60)
        log(f"passed: {len(self.passed)}  failed: {len(self.failed)}  skipped: {len(self.skipped)}")
        for name, err in self.failed:
            log(f"  FAIL {name}: {err}")
        for name, why in self.skipped:
            log(f"  SKIP {name}: {why}")
        return not self.failed


def check(cond: bool, message: str) -> None:
    if not cond:
        raise AssertionError(message)


def wait_for_menu(client: JavaPeer, expected: dict[str, int], timeout: float, what: str) -> dict[str, int]:
    """Asks for the menu until it equals `expected` (deliveries/cancellations are asynchronous)."""
    deadline = time.monotonic() + timeout
    menu: dict[str, int] = {}
    while time.monotonic() < deadline:
        menu = client.menu()
        if menu == expected:
            return menu
        time.sleep(0.3)
    raise AssertionError(f"{what}: menu is {menu}, expected {expected}")


def scenario(clients: list[JavaPeer], providers: list[JavaPeer], r: Results, slack: float) -> None:
    c0 = clients[0] if clients else None

    # 1. Every client can read the menu, and all of them see the same stock.
    def menus() -> None:
        seen = []
        for c in clients:
            m = c.menu()
            check(all(isinstance(m[p], int) and 0 <= m[p] <= STOCK_CAP for p in PRODUCTS),
                  f"{c.name}: bad menu {m}")
            seen.append(m)
            log(f"  {c.name} menu: {m}")
        check(all(m == seen[0] for m in seen), f"clients see different stock: {seen}")
    r.run("every client reads the menu", menus) if clients else r.skip("menu", "no clients")

    # 2. Validation done locally by the Java client (nothing is sent).
    def local_validation() -> None:
        assert c0 is not None
        c0.type("deliver coffee=1")
        c0.expect_text("is a provider command")
        c0.type("order")
        c0.expect_text("needs at least one item")
        c0.type("order coffee")
        c0.expect_text("product=quantity")
        c0.type("dance")
        c0.expect_text("Unknown command")
        if providers:
            providers[0].type("menu")
            providers[0].expect_text("is a client command")
    r.run("Java client rejects bad commands locally", local_validation) if c0 else r.skip(
        "local validation", "no clients")

    # 3. Errors answered by the server; the connection must stay open after each one.
    def server_errors() -> None:
        assert c0 is not None
        c0.clear()
        c0.type("order coffee=0")
        c0.expect_server(is_error("negative or zero"), 5, "zero-quantity error")
        c0.type("order coffee=-3")
        c0.expect_server(is_error("negative or zero"), 5, "negative-quantity error")
        c0.type(f"order coffee={STOCK_CAP + 1}")
        c0.expect_server(is_error("Not enough products"), 5, "not-enough-products error")
        c0.type("order pizza=1")
        c0.expect_server(is_error("Json bad formatted"), 5, "unknown-product error")
        c0.type("order coffee=abc")
        c0.expect_server(is_error("Json bad formatted"), 5, "non-numeric quantity error")
        c0.type('{"action":"dance"}')
        c0.expect_server(is_error("not implemented"), 5, "unknown-action error")
        c0.type('{"noaction":1}')
        c0.expect_server(is_error("Unknown action"), 5, "missing-action error")
        c0.type("{not json")
        c0.expect_server(is_error("Json bad formatted"), 5, "invalid-JSON error")
        c0.menu()                                    # still connected
        check(c0.alive(), "client process died after server errors")
        if providers:
            p = providers[0]
            p.clear()
            p.type('{"action":"menu"}')              # raw JSON bypasses the local check
            p.expect_server(is_error("not implemented"), 5, "menu-from-provider error")
    r.run("server errors keep the connection open", server_errors) if c0 else r.skip(
        "server errors", "no clients")

    # 4. All clients order at the same time; each gets TimeToReady and later "ready".
    def concurrent_orders() -> None:
        orders: dict[str, dict[str, int]] = {}
        for i, c in enumerate(clients):
            product = PRODUCTS[i % len(PRODUCTS)]
            orders[c.name] = {product: 1}
        for c in clients:
            c.clear()
        for c in clients:                                  # fire them all quickly
            c.type(order_cmd(orders[c.name]))
        accepted: dict[str, int] = {}
        for c in clients:
            msg = c.expect_server(lambda m: "TimeToReady" in m or is_error()(m), 5, "order reply")
            if "Error" in msg:
                # Possible only when stock really ran out; providers restock it shortly.
                check("Not enough" in msg["Error"], f"{c.name}: unexpected error {msg}")
                log(f"  {c.name}: out of stock for {orders[c.name]}")
                continue
            want = order_time(orders[c.name])
            check(msg["TimeToReady"] == want,
                  f"{c.name}: TimeToReady {msg['TimeToReady']} for {orders[c.name]}, expected {want}")
            accepted[c.name] = want
            log(f"  {c.name}: ordered {orders[c.name]}, ready in {want}s")
        check(accepted, "no order was accepted")
        start = time.monotonic()
        for c in clients:
            if c.name in accepted:
                c.expect_server(is_message("Your order is ready!"), accepted[c.name] + slack, "order ready")
                took = time.monotonic() - start
                check(took >= accepted[c.name] - 1.5, f"{c.name}: ready after {took:.1f}s, "
                                                      f"too early (TimeToReady {accepted[c.name]})")
                log(f"  {c.name}: ready after {took:.1f}s")
    r.run(f"{len(clients)} clients order concurrently", concurrent_orders) if clients else r.skip(
        "concurrent orders", "no clients")

    # 5. A client can order again while an earlier order is being prepared.
    def overlapping_orders() -> None:
        assert c0 is not None
        c0.clear()
        c0.type("order croissant=1")
        c0.expect_server(lambda m: m.get("TimeToReady") == 2, 5, "TimeToReady 2")
        c0.type("order croissant=2")
        c0.expect_server(lambda m: m.get("TimeToReady") == 4, 5, "TimeToReady 4")
        c0.menu()                                         # menu while both are cooking
        c0.expect_server(is_message("Your order is ready!"), 2 + slack, "first ready")
        c0.expect_server(is_message("Your order is ready!"), 4 + slack, "second ready")
    r.run("one client with overlapping orders", overlapping_orders) if c0 else r.skip(
        "overlapping orders", "no clients")

    # 6. Every provider can deliver by hand; the stock grows accordingly.
    def manual_delivery() -> None:
        assert c0 is not None
        time.sleep(1)                                     # let automatic restocks settle
        before = c0.menu()
        for p in providers:
            p.clear()
            p.type("deliver tea=1")
            p.expect_server(is_message("Delivery accepted."), 5, "Delivery accepted.")
        expected = dict(before)
        expected["Tea"] = min(STOCK_CAP, before["Tea"] + len(providers))
        wait_for_menu(c0, expected, 5, "after manual deliveries")
        log(f"  tea {before['Tea']} -> {expected['Tea']}")
    if providers and c0:
        r.run(f"{len(providers)} provider(s) deliver by hand", manual_delivery)
    else:
        r.skip("manual delivery", "needs at least one client and one provider")

    # 7. Draining a product makes the server ask a provider to restock; the Java
    #    provider answers on its own and stock goes back to 30.
    def automatic_restock() -> None:
        assert c0 is not None
        time.sleep(1)
        menu = c0.menu()
        product = "Croissant"
        if menu[product] < RESTOCK_BELOW + 1:
            # Make room so the drain below is a real order.
            providers[0].type(f"deliver {product.lower()}=10")
            providers[0].expect_server(is_message("Delivery accepted."), 5, "Delivery accepted.")
            menu = c0.menu()
        drain = menu[product] - (RESTOCK_BELOW - 2)      # leaves 3 behind
        for p in providers:
            p.clear()
        c0.clear()
        c0.type(f"order {product.lower()}={drain}")
        c0.expect_server(lambda m: "TimeToReady" in m, 5, "TimeToReady for the drain order")
        after_drain = dict(menu)
        after_drain[product] -= drain
        want_request = {p: RESTOCK_TO - q for p, q in after_drain.items() if q < RESTOCK_TO}

        # Exactly one (random) provider is asked; wait on all of them.
        deadline = time.monotonic() + 10
        asked = None
        while asked is None and time.monotonic() < deadline:
            for p in providers:
                try:
                    p.expect_text("[provider] restock requested", 0.2)
                    asked = p
                    break
                except AssertionError:
                    pass
        check(asked is not None, "no provider received a restock request")
        assert asked is not None
        request = asked.expect_server(lambda m: set(m) <= set(PRODUCTS) and "Error" not in m, 2,
                                      "the restock request")
        check(request == want_request, f"restock request {request}, expected {want_request}")
        asked.expect_server(is_message("Delivery accepted."), 5, "Delivery accepted. for the restock")
        log(f"  {asked.name} was asked for {request} and delivered it")

        expected = {p: max(q, RESTOCK_TO) for p, q in after_drain.items()}
        wait_for_menu(c0, expected, 5, "after the automatic restock")
        log(f"  stock after restock: {expected}")
    if providers and c0:
        r.run("low stock triggers an automatic restock", automatic_restock)
    else:
        r.skip("automatic restock", "needs at least one client and one provider")

    # 8. A client that disconnects mid-order gets its order cancelled and stock returned.
    def cancel_on_disconnect() -> None:
        leaver, watcher = clients[-1], clients[0]
        time.sleep(1)
        before = watcher.menu()
        leaver.clear()
        leaver.type("order coffee=1 tea=1")
        leaver.expect_server(lambda m: m.get("TimeToReady") == 7, 5, "TimeToReady 7")
        during = watcher.menu()
        check(during["Coffee"] == before["Coffee"] - 1 and during["Tea"] == before["Tea"] - 1,
              f"stock not reserved: before {before}, during {during}")
        code = leaver.quit()
        check(code == 0, f"{leaver.name} exited with {code}")
        wait_for_menu(watcher, before, 5, "after the client left mid-order")
        clients.remove(leaver)
    if len(clients) >= 2:
        r.run("disconnect mid-order cancels it", cancel_on_disconnect)
    else:
        r.skip("disconnect mid-order", "needs at least two clients")


# --------------------------------------------------------------------------- #
# Setup: server and Java build
# --------------------------------------------------------------------------- #
def compile_java(out_dir: Path) -> None:
    sources = sorted(str(p) for p in JAVA_SRC.rglob("*.java"))
    log(f"Compiling {len(sources)} Java files...")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(out_dir), *sources], check=True)


def wait_port(host: str, port: int, timeout: float, proc: subprocess.Popen | None) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if proc is not None and proc.poll() is not None:
            raise RuntimeError(f"the server exited with code {proc.returncode}")
        try:
            with socket.create_connection((host, port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.3)
    raise RuntimeError(f"nothing is listening on {host}:{port} after {timeout:.0f}s")


def start_server(host: str, port: int, log_path: Path) -> subprocess.Popen:
    log("Building the C# server...")
    subprocess.run(["dotnet", "build", "-nologo", "-v", "q", str(SERVER_DIR)], check=True,
                   stdout=subprocess.DEVNULL)
    log(f"Starting the server on {host}:{port} (log: {log_path})")
    log_file = open(log_path, "w", encoding="utf-8")
    return subprocess.Popen(
        ["dotnet", "run", "--no-build", "--project", str(SERVER_DIR), "--",
         "--host", host, "--port", str(port)],
        stdout=log_file, stderr=subprocess.STDOUT,
    )


def free_port(host: str) -> int:
    with socket.socket() as s:
        s.bind((host, 0))
        return s.getsockname()[1]


def main() -> int:
    global VERBOSE
    ap = argparse.ArgumentParser(description="Drive the Java client against the C# CoffeeServer.")
    ap.add_argument("-c", "--clients", type=int, default=3, help="number of client processes (default 3)")
    ap.add_argument("-p", "--providers", type=int, default=1, help="number of provider processes (default 1)")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=0,
                    help="server port (default: a free port when starting the server, 4000 with --no-server)")
    ap.add_argument("--no-server", action="store_true", help="don't start the server, use one already running")
    ap.add_argument("--slack", type=float, default=3.0,
                    help="extra seconds allowed past TimeToReady for 'ready' (default 3)")
    ap.add_argument("-v", "--verbose", action="store_true", help="print everything the processes print")
    args = ap.parse_args()
    if args.clients < 0 or args.providers < 0 or args.clients + args.providers == 0:
        ap.error("need at least one client or provider, and no negative counts")
    VERBOSE = args.verbose
    port = args.port or (4000 if args.no_server else free_port(args.host))

    work = Path(tempfile.mkdtemp(prefix="coffee-test-"))
    classes = work / "classes"
    classes.mkdir()
    server: subprocess.Popen | None = None
    peers: list[JavaPeer] = []
    results = Results()
    try:
        compile_java(classes)
        if not args.no_server:
            server = start_server(args.host, port, work / "server.log")
        wait_port(args.host, port, 60, server)

        # Providers first, so restock requests always have somewhere to go.
        providers = [JavaPeer(f"provider{i + 1}", "provider", str(classes), args.host, port)
                     for i in range(args.providers)]
        clients = [JavaPeer(f"client{i + 1}", "client", str(classes), args.host, port)
                   for i in range(args.clients)]
        peers = providers + clients
        log(f"Started {len(clients)} client(s) and {len(providers)} provider(s)")
        for peer in peers:
            peer.wait_connected()

        scenario(clients, providers, results, args.slack)

        def clean_exit() -> None:
            for peer in list(clients) + providers:
                code = peer.quit()
                check(code == 0, f"{peer.name} exited with code {code}")
                peer.expect_text("Bye.", 2)
        results.run("every process quits cleanly", clean_exit)
    except (RuntimeError, subprocess.CalledProcessError, AssertionError) as e:
        results.failed.append(("setup", str(e)))
        log(f"FAIL  setup: {e}")
    finally:
        for peer in peers:
            if peer.alive():
                peer.proc.kill()
        if server is not None:
            server.terminate()
            try:
                server.wait(10)
            except subprocess.TimeoutExpired:
                server.kill()
            log(f"Server log: {work / 'server.log'}")

    return 0 if results.summary() else 1


if __name__ == "__main__":
    sys.exit(main())
