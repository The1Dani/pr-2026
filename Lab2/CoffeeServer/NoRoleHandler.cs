using System.Text;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace CoffeeServer;

public class RequestModel
{
    public required string RequestType { get; set; } = "menu";
    public int Coffee { get; set; } = 0;
    public int Croissant { get; set; } = 0;
    public int Tea { get; set; } = 0;
}

public class NoRoleHandler :  CommandDispatcher
{
    protected override void OnOther(Connection con, string action, JObject payload)
    {
        con.WriteError("Since this connection doesnt have a role you cannot execute actions!");
    }
}