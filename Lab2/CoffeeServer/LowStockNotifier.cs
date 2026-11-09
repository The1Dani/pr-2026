using System.Diagnostics;

namespace CoffeeServer;

public class LowStockNotifier : IDisposable
{
    private readonly Connection _con;
    private readonly StoreRepository _repo;
    
    public LowStockNotifier(Connection providerConnection, StoreRepository repo)
    {
        repo.LowStock += OnLowStock;
        _con = providerConnection;
        _repo = repo;
    }

    private void OnLowStock(IReadOnlyDictionary<EProducts, int> products)
    {
        
    }
    
    public void Dispose() => _repo.LowStock -= OnLowStock;
}