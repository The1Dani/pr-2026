using System.Diagnostics;
using System.Text;
using Newtonsoft.Json.Linq;

namespace CoffeeServer;

public class RequestReader(Connection con, StringBuilder sb)
{
    private const int BufferSize = 1024;
    private readonly Decoder _decoder = Encoding.UTF8.GetDecoder();
    private readonly byte[] _buffer = new byte[BufferSize];
    private readonly char[] _chars = new char[Encoding.UTF8.GetMaxCharCount(BufferSize)];
    private bool _crSeen = false;

    public async Task<bool> Read()
    {
        while (true)
        {
            try
            {
                var count = await con.Sock.ReceiveAsync(_buffer);
                _decoder.GetChars(_buffer, 0, count, _chars, 0);
                if (count <= 0)
                {
                    return false;
                }
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
                throw; // TODO: replace
            }

            foreach (var ch in _chars)
            {
                sb.Append(ch.ToString());
                if (ch == '\r')
                {
                    _crSeen = true;
                    continue;
                }

                if (_crSeen && ch == '\n')
                {
                    // Return true to tell the connection is still going
                    return true;
                }

                _crSeen = false;
            }

            if (sb.Length > 1024 * 1024) //1MiB
            {
                //TODO: create an error handler that will write an error message to the socket
                return false;
            }
        }
    }

    public string GetString()
    {
        var req = sb.ToString().Trim();
        sb.Clear();
        return req;
    }
}

public class ConnectionHandler
{
    private readonly ConnectionPool _pool;

    public ConnectionHandler(ConnectionPool pool)
    {
        _pool = pool;
    }

    public async Task Handle(Connection con)
    {
        Debug.Assert(_pool.Contains(con), $"Connection {con} is not in the pool");

        //Read and parse the incoming request
        StringBuilder sb = new();
        var reqReader = new RequestReader(con, sb);

        try
        {
            if (await reqReader.Read())
            {
                var line = reqReader.GetString();
                con.SetRoleFromJson(line);

                if (con.Role == ERole.Provider)
                    StoreService.Instance.AddProvider(con);

            }
            else
            {
                _pool.CloseConnection(con);
                return;
            }

            while (await reqReader.Read())
            {
                var req = reqReader.GetString();
                try
                {
                    await HandleCommand(con, req);
                }
                catch (Exception e)
                {
                    Console.WriteLine(e);
                    throw;
                }
            }
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
        finally
        {
            _pool.CloseConnection(con);
        }
    }

    private async Task HandleCommand(Connection con, string req)
    {
        var obj = JObject.Parse(req);
        var action = (string?) obj["action"]
            ?? throw new InvalidOperationException($"Unknown action: {req}");;
        obj.Remove("action");
        await DispatchRegistry.Get(con.Role).Dispatch(con, action, obj);
    }
}