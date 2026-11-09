namespace CoffeeServer;

public class StoreService
{
    public static readonly StoreService Instance = new StoreService();
    private readonly StoreRepository _repo =  new StoreRepository();
    //TODO: Add a list for the stock notifiers and handle the removal as well!!
    private StoreService() {}
    
    public void AddProvider(Connection connection)
    {
        new LowStockNotifier(connection, _repo);
    }
}