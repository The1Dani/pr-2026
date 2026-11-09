using System.Diagnostics;

namespace CoffeeServer;

public class LowStockNotifier : IDisposable
{
    public readonly Connection Con;
    private readonly StoreRepository _repo;
    private const int AskSupplyTo = 30;

    public LowStockNotifier(Connection providerCon, StoreRepository repo)
    {
        repo.LowStock += OnLowStock;
        Con = providerCon;
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
        
        Con.WriteObject(askAmount);
        
    }
    
    public void Dispose() => _repo.LowStock -= OnLowStock;
}