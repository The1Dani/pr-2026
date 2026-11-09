using System.Diagnostics;

namespace CoffeeServer;

public class LowStockNotifier : IDisposable
{
    private readonly Connection _con;
    private readonly StoreRepository _repo;
    private const int AskSupplyTo = 30;

    public LowStockNotifier(Connection providerConnection, StoreRepository repo)
    {
        repo.LowStock += OnLowStock;
        _con = providerConnection;
        _repo = repo;
    }

    private void OnLowStock(IReadOnlyDictionary<EProducts, int> products)
    {
        
        MenuDto askAmount = new();
        foreach (var kvp in products)
        {
            if (kvp.Value < AskSupplyTo)
                askAmount[kvp.Key] = AskSupplyTo - kvp.Value;
        }
        
        _con.WriteObject(askAmount);
        
    }

    public void Dispose() => _repo.LowStock -= OnLowStock;
}