namespace CoffeeServer;

public static class DispatchRegistry
{
    private static readonly Dictionary<ERole, CommandDispatcher> Handlers = new()
    {
        [ERole.NoRole] = new NoRoleDispatcher(),
        [ERole.Client] = new ClientDispatcher(),
        [ERole.Provider] = new ProviderDispatcher(),
    };

    public static CommandDispatcher Get(ERole role)
    {
        return Handlers.TryGetValue(role, out var handler) ? handler : throw new KeyNotFoundException();
    }
}