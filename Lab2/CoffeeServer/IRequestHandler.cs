namespace CoffeeServer;

public interface IRequestHandler
{
    Task Handle(Connection con, string req);
}
