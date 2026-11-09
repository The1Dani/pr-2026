using System.Text;
using Newtonsoft.Json;

namespace CoffeeServer;

public class RequestModel
{
    public required string RequestType { get; set; } = "menu";
    public int Coffee { get; set; } = 0;
    public int Croissant { get; set; } = 0;
    public int Tea { get; set; } = 0;
}

public class RequestHandler :  IRequestHandler
{
    public async Task Handle(Connection con, string req)
    {
        Console.WriteLine($"req_echo={req}");
        RequestModel? value = null;
        try
        {
            value = JsonConvert.DeserializeObject<RequestModel>(req);

        }
        catch (Exception e)
        {
            Console.WriteLine(e);
            throw;
        }
        Console.WriteLine(value);
        var total = value.Coffee + value.Croissant + value.Tea;

        var count = 0;
        var data = Encoding.UTF8.GetBytes($"total={total}\r\n");
        while (count < data.Length)
        {
            count += await con.Sock.SendAsync(data);
        }
    }
}