namespace CoffeeServer;

public class ClientDispatcher : CommandDispatcher
{
    private readonly StoreService _service = StoreService.Instance;

    public ClientDispatcher()
    {
        On("menu", HandleMenu);
        On<MenuDto>("order", HandleOrder);
    }

    private void HandleMenu(Connection con)
    {
        var menu = _service.GetMenu();
        con.WriteObject(menu);
    }

    private void HandleOrder(Connection con, MenuDto payload)
    {
        // verify positive number order!   
        foreach (var kvp in payload)
        {
            if (kvp.Value <= 0)
            {
                con.WriteError("You cannot order negative or zero items!");
                return;
            }
        }

        // Call Service
        
        if (!_service.ReserveForOrder(payload, out var lease))
        {
            con.WriteError("Not enough products!");
            return;
        }

        var reg = con.CancelToken.Register(() =>
        {
            _service.AbortLease(lease);
            Console.WriteLine($"The connection {con.EndPoint} has died during the order");
        });

        var ttr = lease.CalculateTime();

        con.WriteObject(new { TimeToReady = ttr });

        con.CancelToken.WaitHandle.WaitOne(TimeSpan.FromSeconds(ttr)); // Release the thread if the client is gone

        if (reg.Unregister())
        {
            _service.ReleaseLease(lease);
            con.WriteObject(new { Message = "Your order is ready!" });
        }
    }
}