using System.Collections.Concurrent;
using System.Diagnostics.CodeAnalysis;

namespace CoffeeServer;

public record OrderLease(int Id, MenuDto Items)
{
    private static readonly Dictionary<EProducts, int> PrepTimes = new()
    {
        [EProducts.Coffee] = 4,
        [EProducts.Tea] = 3,
        [EProducts.Croissant] = 2,
    };

    public int CalculateTime()
    {
        var time = 0;
        foreach (var kvp in Items)
        {
            if (kvp.Value <= 0) continue;
            time += kvp.Value * PrepTimes[kvp.Key];
        }

        return time;
    }
}

public class StoreService
{
    public static readonly StoreService Instance = new();
    private readonly StoreRepository _repo = new();
    private readonly Lock _lock = new();

    private int _idCounter = 0;
    private readonly ConcurrentDictionary<int, OrderLease> _leases = new();

    private StoreService()
    {
    }

    public void AddProvider(Connection connection)
    {
        _repo.LowStockNotifier.AddConnection(connection);
    }

    public void RemoveProvider(Connection connection)
    {
        _repo.LowStockNotifier.RemoveConnection(connection);
    }
    
    public IReadOnlyDictionary<EProducts, int> GetMenu() => _repo.GetAllProducts();

    public bool ReserveForOrder(MenuDto menu, [NotNullWhen(true)] out OrderLease? lease)
    {
        if (!_repo.TrySubtractProducts(menu))
        {
            lease = null;
            return false;
        }

        lease = LeaseOrder(menu);
        return true;
    }

    private OrderLease LeaseOrder(MenuDto menu)
    {
        Interlocked.Increment(ref _idCounter);
        var lease = new OrderLease(_idCounter, menu);

        _leases.TryAdd(lease.Id, lease);

        return lease;
    }

    public void AbortLease(OrderLease? lease)
    {
        if (lease is null) return;
        _repo.AddProducts(lease.Items);
        ReleaseLease(lease);
    }

    public void ReleaseLease(OrderLease lease)
    {
        _leases.TryRemove(lease.Id, out _);
    }

    public void AddItems(MenuDto items) => _repo.AddProducts(items);
}