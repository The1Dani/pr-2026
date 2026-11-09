using Newtonsoft.Json.Linq;

namespace CoffeeServer;

public class NoRoleDispatcher : CommandDispatcher
{
    protected override void OnOther(Connection con, string action, JObject payload)
    {
        con.WriteError("Since this connection doesnt have a role you cannot execute actions!");
    }
}
