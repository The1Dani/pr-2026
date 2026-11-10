using System.Collections.Concurrent;
using System.Text.Json.Serialization;
using Newtonsoft.Json.Converters;

namespace CoffeeServer;

[JsonConverter(typeof(StringEnumConverter))]
public enum EProducts
{
    Coffee,
    Tea,
    Croissant,
}

public class StoreRepository
{
    private readonly MenuDto _repo = new()
    {
        [EProducts.Coffee] = Random.Shared.Next(10, 50 + 1),
        [EProducts.Tea] = Random.Shared.Next(10, 50 + 1),
        [EProducts.Croissant] = Random.Shared.Next(10, 50 + 1),
    };

    private readonly Lock _lock = new();
    public readonly LowStockNotifier LowStockNotifier;
    private const int LowThreshold = 5;
    private const int UpperLimit = 1000;

    public StoreRepository()
    {
        LowStockNotifier = new LowStockNotifier(this);
    }
    
    public IReadOnlyDictionary<EProducts, int> GetAllProducts()
    {
        lock (_lock)
        {
            return new MenuDto(_repo);
        }
    }

    public bool TrySubtractProducts(IReadOnlyDictionary<EProducts, int> products)
    {
        bool lowStock = false;
        Dictionary<EProducts, int>? lowDict = null;
        lock (_lock)
        {
            foreach (var (product, qty) in products)
            {
                if (qty <= 0 || !_repo.TryGetValue(product, out var stock) || stock < qty)
                {
                    return false;
                }
            }

            foreach (var (product, qty) in products)
            {
                _repo[product] -= qty;
                if (_repo[product] < LowThreshold)
                {
                    lowStock = true;
                    lowDict = new MenuDto(_repo);
                }
            }
        }

        if (lowStock && lowDict is not null)
            LowStockNotifier.Notify(lowDict);

        return true;
    }

    public void AddProducts(IReadOnlyDictionary<EProducts, int> products)
    {
        lock (_lock)
        {
            foreach (var (product, qty) in products)
            {
                if (qty > 0)
                    _repo[product] = (_repo[product] + qty) > UpperLimit ? UpperLimit : _repo[product] + qty;
            }
        }
    }
}