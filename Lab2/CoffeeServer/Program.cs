// Init

using System.Net;
using System.Net.Sockets;
using CoffeeServer;
using CommandLine;
using Serilog;

var threadCount = Environment.ProcessorCount;

var res = Parser.Default.ParseArguments<Options>(args);

if (res.Errors.Any())
    return 1;
var options = res.Value;


ThreadPool.SetMinThreads(threadCount, threadCount);

Log.Logger = new LoggerConfiguration()
    .MinimumLevel.Debug()
    .WriteTo
    .Console(outputTemplate: "[{Level:u3}] {Message:l}{NewLine}{Exception}")
    .CreateLogger();

var listener = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
listener.Bind(new IPEndPoint(IPAddress.Parse(options.Host), options.Port));

// Listen for connections and append to connection pool
var pool = new ConnectionPool(listener);

try
{
    await pool.Listen(options.Backlog);
}
finally
{
    Log.CloseAndFlush();
}
return 0;
