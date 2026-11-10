using Serilog;

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
        Log.Information("Action :menu: Connection({ConEndPoint})", con.EndPoint);
    }

    private static void InfoLog(Connection con, string message)
    {
        Log.Information("Action :order: Connection({ConEndPoint}) {message}", con.EndPoint, message);
    }

    private void HandleOrder(Connection con, MenuDto payload)
    {
        // verify positive number order!   
        foreach (var kvp in payload)
        {
            if (kvp.Value <= 0)
            {
                con.WriteError("You cannot order negative or zero items!");
                InfoLog(con, "Client tried to order zero or negative items");
                return;
            }
        }

        // Call Service
        if (!_service.ReserveForOrder(payload, out var lease))
        {
            con.WriteError("Not enough products!");
            InfoLog(con, "Not enough products");
            return;
        }

        var reg = con.CancelToken.Register(() =>
        {
            _service.AbortLease(lease);
            InfoLog(con, "died during the order");
        });

        var ttr = lease.CalculateTime();

        con.WriteObject(new { TimeToReady = ttr });
        InfoLog(con, $"order will ready in {ttr}s");

        Task.Run(() => { PrepareOrder(reg, con, lease); });
    }

    private void PrepareOrder(CancellationTokenRegistration reg, Connection con, OrderLease lease)
    {
        con.CancelToken.WaitHandle.WaitOne(TimeSpan.FromSeconds(lease.CalculateTime()));

        if (!reg.Unregister())
            return;

        if (con.CancelToken.IsCancellationRequested)
        {
            _service.ReleaseLease(lease);
            return;
        }

        con.WriteObject(new { Message = "Your order is ready!" });
        InfoLog(con, "Order ready!");
    }
}