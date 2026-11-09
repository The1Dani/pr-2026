# CoffeeServer

A TCP server for a small coffee shop. **Clients** read the menu and place
orders. **Providers** deliver stock and are asked to restock when it runs low.
All messages are JSON.

```bash
cd Lab2/CoffeeServer
dotnet run            # listens on 127.0.0.1:4000
```

---

## 1. Wire format

| | |
|---|---|
| Transport | TCP, `127.0.0.1:4000` |
| Encoding | UTF-8 |
| Framing | One JSON object per message, terminated by `\r\n` (CRLF). Applies in both directions. |
| Max message size | 1 MiB. A larger message gets an error, then the connection is closed. |

- A lone `\n` is **not** a terminator. The server keeps buffering until it sees `\r\n`.
- A message may arrive split across several TCP reads, and several messages may arrive in one read. Both sides must buffer and split on `\r\n` (see [§6](#6-implementing-a-client)).
- Whitespace around a message is ignored.
- Messages carry **no request IDs**. Replies to your requests come back in the order you sent them, but the server can also send messages you didn't ask for (see [§4](#4-messages-from-the-server)).

---

## 2. Session lifecycle

```
connect ──► handshake ──► commands … ──► close
            {"Role":…}    {"action":…}
```

### 2.1 Handshake (first message)

```json
{"Role": "Client"}
```

| Role | Can do |
|---|---|
| `Client` | `menu`, `order` |
| `Provider` | `delivery`, and receives restock requests |
| `NoRole` | nothing: every action returns an error |

- The key and value are case-insensitive (`{"role":"client"}` works). Extra fields are ignored.
- The role must be a string. Numbers (`{"Role":1}`) and unknown names are rejected.
- **A successful handshake gets no reply.** Send your first command right after it.
- **A failed handshake** gets the error below, and the server then closes the connection:
  ```json
  {"Error": "Expected a handshake like {\"Role\":\"Client\"} (roles: Client, Provider, NoRole)"}
  ```
- The role can't be changed later. Reconnect to use a different role.

### 2.2 Commands

Every message after the handshake is a command:

```json
{"action": "<name>", ...arguments}
```

- `action` is case-insensitive (`"MENU"` works).
- The arguments are the other top-level fields of the same object.
- A failed command gets an `{"Error": ...}` reply and **the connection stays open**.

### 2.3 Closing

Either side may close at any time. If a client disconnects while orders are
still being prepared, the **orders are cancelled and their stock is returned**.

---

## 3. Commands

Products and preparation times:

| Product | Prep time per item |
|---|---|
| `Coffee` | 4 s |
| `Tea` | 3 s |
| `Croissant` | 2 s |

Stock starts at a random 10–50 per product.

### 3.1 `menu` (Client)

```json
→ {"action": "menu"}
← {"Coffee": 31, "Tea": 24, "Croissant": 13}
```
Returns the current stock of every product.

### 3.2 `order` (Client)

```json
→ {"action": "order", "Coffee": 1, "Croissant": 2}
← {"TimeToReady": 8}
   … 8 seconds later …
← {"Message": "Your order is ready!"}
```

- Arguments map product names to quantities. Use the exact names `Coffee`, `Tea` and `Croissant`, with positive integers.
- The stock is reserved immediately. `TimeToReady` is the sum of quantity × prep time, in seconds.
- The ready message arrives later, without being requested. Other replies may arrive before it (see [§4](#4-messages-from-the-server)).
- You may place more orders, or ask for the menu, while an order is being prepared.
- If you disconnect before the ready message arrives, the order is cancelled and its stock returned.

| Problem | Reply |
|---|---|
| Any quantity ≤ 0 | `{"Error": "You cannot order negative or zero items!"}` |
| More than in stock | `{"Error": "Not enough products!"}` (nothing is reserved) |
| Unknown product or non-numeric quantity | `{"Error": "Json bad formatted."}` |

> Don't send an empty order (`{"action":"order"}`). The server answers
> `{"TimeToReady": 0}`. Validate on the client side.

### 3.3 `delivery` (Provider)

```json
→ {"action": "delivery", "Tea": 20, "Coffee": 5}
← {"Message": "Delivery accepted."}
```

- Adds the quantities to stock. Each product's stock is **capped at 1000**, and anything over the cap is dropped without an error.
- Non-positive quantities are ignored, and the reply is still `Delivery accepted.`. Send only positive integers.
- Keep each quantity ≤ 1000. Very large values (close to 2³¹) overflow the stock counter.

### 3.4 Restock requests (sent to the Provider, unrequested)

When an order leaves any product with **fewer than 5** in stock, every
connected provider receives a request to top up **every product under 30**
back to 30:

```json
← {"Croissant": 28}
← {"Tea": 26, "Croissant": 28}
```
The reply to this is an ordinary `delivery`.

---

## 4. Messages from the server

Every server message is one JSON object. Classify it by its keys:

| Shape | Meaning | Sent to | Requested? |
|---|---|---|---|
| `{"Error": "…"}` | the request failed | anyone | reply |
| `{"Coffee":…, "Tea":…, "Croissant":…}` (all 3 products) | menu | Client | reply to `menu` |
| `{"TimeToReady": n}` | order accepted | Client | reply to `order` |
| `{"Message": "Your order is ready!"}` | an earlier order is done | Client | **unrequested** |
| `{"Message": "Delivery accepted."}` | delivery applied | Provider | reply to `delivery` |
| `{"<Product>": n, …}` (the products to restock, possibly all 3) | restock request | Provider | **unrequested** |

A restock request naming all three products looks exactly like a menu, so
tell them apart by role: a Provider never receives a menu, and a Client never
receives a restock request. Each request gets exactly one reply: `Error`, the
menu, `TimeToReady`, or `Delivery accepted.`. The unrequested messages are the order-ready
notification and the restock request, and they can arrive between any two
replies.

### Error reference

| Error text | Cause | Connection |
|---|---|---|
| `Expected a handshake like …` | invalid handshake | **closed** |
| `The message is larger than 1Mib bigger than allowed!` | message > 1 MiB | **closed** |
| `Json bad formatted.` | invalid JSON, non-object, unknown product, bad quantity type | open |
| `Unknown action: …` | no `action` field | open |
| `Action: <x> is not implemented.` | action unknown or not allowed for this role | open |
| `Since this connection doesnt have a role you cannot execute actions!` | `NoRole` sent any action | open |
| `You cannot order negative or zero items!` | quantity ≤ 0 in an order | open |
| `Not enough products!` | order exceeds stock | open |
| `Internal Server Error` | unexpected server failure | open |

---

## 5. Limits and server behaviour

| Limit | Behaviour |
|---|---|
| Message size | 1 MiB, then error and close |
| Unread replies | if more than 1024 replies pile up because you are not reading, you are disconnected |
| Stock per product | capped at 1000 |
| Connection backlog | 256 pending connections |
| Order timing | `TimeToReady` is the target. Under heavy load (dozens of orders at once) the ready message can arrive a few seconds later. |
