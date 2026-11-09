namespace CoffeeServer;

public class HandlerRegistry
{
    private static readonly Dictionary<ERole, IRequestHandler> _handlers = new()
    {
        [ERole.NoRole] = new RequestHandler(),
        [ERole.Client] = new ClientHandler(),
        [ERole.Provider] = new ProviderHandler(),
    };

    public static IRequestHandler Get(ERole role)
    {
        return _handlers.TryGetValue(role, out var handler) ? handler : throw new KeyNotFoundException();
    }
}