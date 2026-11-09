// Init


using System.Net;
using System.Net.Sockets;
using CoffeeServer;
using Serilog;


int PORT = 4000;
int BACKLOG = 256;

Log.Logger = new LoggerConfiguration()
    .MinimumLevel.Debug()
    .WriteTo
    .Console(outputTemplate: "[{Level:u3}] {Message:l}{NewLine}{Exception}")
    .CreateLogger();

Socket listener = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
listener.Bind(new IPEndPoint(IPAddress.Loopback, PORT));

// Listen for connections and append to connection pool
var pool = new ConnectionPool(listener);

try
{
    await pool.Listen(BACKLOG);
}
finally
{
    Log.CloseAndFlush();
}
