using Serilog;

namespace CoffeeServer;

public class ProviderDispatcher : CommandDispatcher
{

    public ProviderDispatcher()
    {
        On<MenuDto>("delivery", Delivery);
    }

    private static void Delivery(Connection con, MenuDto items)
    {
        var toAdd = items.Where(kvp => kvp.Value > 0).ToDictionary(kvp => kvp.Key, kvp => kvp.Value);
        StoreService.Instance.AddItems(toAdd);
        con.WriteObject(new
        {
            Message = "Delivery accepted.",
        });
        Log.Information("Delivery accepted, delivery={Dictionary}, accepted={ToAdd}", items, toAdd);
    }
    
    
}