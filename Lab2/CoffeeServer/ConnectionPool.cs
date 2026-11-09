using System.Diagnostics.CodeAnalysis;
using System.Net.Sockets;
using Newtonsoft.Json;

namespace CoffeeServer;


public class Connection(Socket client)
{
    private class RoleModel
    {
        [JsonProperty(Required = Required.Always)]
        public ERole Role { get; set; }
    }

    public Socket Sock { get; } = client;
    public ERole Role;

    public void SetRoleFromJson(string firstRequest)
    {
        var req = JsonConvert.DeserializeObject<RoleModel>(firstRequest);
        if (req is not null)
        {
            SetRole(req.Role);
            Console.WriteLine($"Role {req.Role} has been set for {Sock.RemoteEndPoint}");
        }
        else
        {
            throw new Exception("Invalid role");
        }
    }

    public void SetRole(ERole role)
    {
        Role = role;
    }
}

public class ConnectionPool
{
    private readonly Socket _listener;
    private LinkedList<Connection> _connections { get; } = [];

    public ConnectionPool(Socket listener)
    {
        _listener = listener;
    }

    [SuppressMessage("ReSharper", "FunctionNeverReturns")]
    public async Task Listen(int backlog)
    {
        var conHandler = new ConnectionHandler(this);
        _listener.Listen(backlog);

        Console.WriteLine($"Listening on {_listener.LocalEndPoint}");

        while (true)
        {
            var client = await _listener.AcceptAsync();
            Console.WriteLine($"Client {client.RemoteEndPoint} connected");
            var con = new Connection(client);
            _connections.AddLast(con);
            conHandler.Handle(con);
        }
    }

    public void CloseConnection(Connection connection)
    {
        Console.WriteLine($"Connection {connection.Sock.RemoteEndPoint} closed");
        connection.Sock.Close();
        _connections.Remove(connection);
    }

    public bool Contains(Connection connection)
    {
        return _connections.Contains(connection);
    }

    public IEnumerator<Connection> GetEnumerator()
    {
        foreach (var conn in _connections)
        {
            if (conn.Sock is not null) // e.g. only yield open connections
                yield return conn;
        }
    }
}