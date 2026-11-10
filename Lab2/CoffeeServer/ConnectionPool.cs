using System.Diagnostics.CodeAnalysis;
using System.Net.Sockets;
using Newtonsoft.Json;
using Serilog;

namespace CoffeeServer;

public class ConnectionPool
{
    private readonly Socket _listener;
    private LinkedList<Connection> Connections { get; } = [];
    private readonly Lock _lock = new();

    public ConnectionPool(Socket listener)
    {
        _listener = listener;
    }

    [SuppressMessage("ReSharper", "FunctionNeverReturns")]
    public async Task Listen(int backlog)
    {
        var conHandler = new ConnectionHandler(this);
        try
        {
            _listener.Listen(backlog);
        }
        catch (Exception e)
        {
            Log.Debug(e,"Something went wrong!");
        }

        Log.Information("Listening on {ListenerLocalEndPoint}", _listener.LocalEndPoint);

        while (true)
        {
            Socket client;
            try
            {
                client = await _listener.AcceptAsync();
            }
            catch (ObjectDisposedException)
            {
                Log.Fatal("Listener closed, stopping accept loop");
                throw;
            }
            catch (SocketException e)
            {
                Log.Warning(e, "Client could not be accepted");
                continue;
            }
            catch (Exception e)
            {
                Log.Fatal(e, "Something went wrong!");
                throw;
            }
            
            Log.Information($"Client {client.RemoteEndPoint} connected");
            
            var con = new Connection(client);

            lock (_lock)
            {
                Connections.AddLast(con);
            }

            conHandler.Handle(con);
        }
    }

    public void CloseConnection(Connection connection)
    {
        Log.Information("Connection {SockRemoteEndPoint} closed", connection.Sock.RemoteEndPoint);
        connection.Dispose();
        lock (_lock)
        {
            Connections.Remove(connection);
        }
    }

    public bool Contains(Connection connection)
    {
        lock (_lock)
        {
            return Connections.Contains(connection);
        }
    }
}