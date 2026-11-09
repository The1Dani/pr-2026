using Newtonsoft.Json.Linq;
using Serilog;

namespace CoffeeServer;

public abstract class CommandDispatcher
{
    private readonly Dictionary<string, Action<Connection, JObject>> _commands = new();

    protected void On<TDto>(string action, Action<Connection, TDto> command)
    {
        Action<Connection, JObject> wrapper = (con, payload) =>
        {
            TDto? dto = payload.ToObject<TDto>();

            if (dto is null)
                throw new InvalidOperationException($"Payload for '{action}' could not be read as {typeof(TDto).Name}");

            command(con, dto);
        };

        _commands.Add(action, wrapper);
    }

    protected void On(string action, Action<Connection> command)
    {
        _commands.Add(action, (con, _) => command(con));
    }

    public Task Dispatch(Connection con, string action, JObject payload)
    {
        if (!_commands.TryGetValue(action, out var wrapper))
        {
            return Task.Run(() => OnOther(con, action, payload));
        }

        return Task.Run(() => wrapper(con, payload)); // Running the action handler on a thread from the pool
    }

    protected virtual void OnOther(Connection con, string action, JObject payload)
    {
        Log.Information("Action: {Action} is not implemented. Endpoint {ConEndPoint}", action, con.EndPoint);
        con.WriteError($"Action: {action} is not implemented.");
    }
}