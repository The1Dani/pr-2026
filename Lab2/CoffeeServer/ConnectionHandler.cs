using System.Diagnostics;
using System.Text;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Serilog;

namespace CoffeeServer;

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
                try
                {
                    con.SetRoleFromJson(line);
                }
                catch (Exception e) when (e is JsonException or InvalidOperationException)
                {
                    await con.WriteErrorAndFinish(
                        "Expected a handshake like {\"Role\":\"Client\"} (roles: Client, Provider, NoRole)");
                    return; // finally → CloseConnection
                }

                if (con.Role == ERole.Provider)
                    StoreService.Instance.AddProvider(con);
            }
            else
            {
                return;
            }

            while (await reqReader.Read())
            {
                var req = reqReader.GetString();
                try
                {
                    await HandleCommand(con, req);
                }
                catch (Exception e) when (e is InvalidOperationException)
                {
                    con.WriteError(e.Message);
                }
                catch (Exception e) when (e is JsonReaderException or JsonSerializationException)
                {
                    con.WriteError("Json bad formatted.");
                }
                catch (Exception e)
                {
                    Log.Debug(e, "Request from {ConEndPoint} was wrong!", con.EndPoint);
                }
            }
        }
        catch (Exception e)
        {
            Log.Warning(e, "Request from {ConEndPoint} died!", con.EndPoint);
        }
        finally
        {
            _pool.CloseConnection(con);
        }
    }

    private static async Task HandleCommand(Connection con, string req)
    {
        var obj = JObject.Parse(req);
        string action;
        try
        {
            action = (string?)obj["action"]
                     ?? throw new InvalidOperationException($"Unknown action: {req}");
        }
        catch (Exception e)
        {
            throw new InvalidOperationException(e.Message);
        }

        obj.Remove("action");
        await DispatchRegistry.Get(con.Role).Dispatch(con, action.ToLower(), obj);
    }
}