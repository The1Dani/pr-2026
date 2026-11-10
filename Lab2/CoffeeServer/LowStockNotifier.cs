using Serilog;

namespace CoffeeServer;

public class LowStockNotifier(StoreRepository repo)
{
    private readonly List<Connection> _connections = [];
    private const int AskSupplyTo = 30;
    private readonly Lock _lock = new();

    public void AddConnection(Connection con)
    {
        lock (_lock)
        {
            _connections.Add(con);
        }
    }

    public void RemoveConnection(Connection con)
    {
        lock (_lock)
        {
            _connections.Remove(con);
        }
    }

    public void Notify(MenuDto products)
    {
        MenuDto askAmount = new();
        foreach (var kvp in products)
        {
            if (kvp.Value < AskSupplyTo)
                askAmount[kvp.Key] = AskSupplyTo - kvp.Value;
        }

        lock (_lock)
        {
            try
            {
                _connections[Random.Shared.Next(_connections.Count)].WriteObject(askAmount);
            }
            catch (Exception e)
            {
                Log.Error("Couldn't deliver the order to the provider.\nError: {Message}", e.Message);
            }
        }
    }
}