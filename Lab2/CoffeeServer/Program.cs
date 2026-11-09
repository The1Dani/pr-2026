// Init


using System.Net;
using System.Net.Sockets;
using CoffeeServer;


int PORT = 4000;
int BACKLOG = 4;
Socket listener = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
listener.Bind(new IPEndPoint(IPAddress.Loopback, PORT));

// Listen for connections and append to connection pool
var pool = new ConnectionPool(listener);

await pool.Listen(BACKLOG);
