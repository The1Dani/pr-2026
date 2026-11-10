using CommandLine;

namespace CoffeeServer;

public class Options
{
    [Option("port", Required = false, Default = 4000)]
    public int Port { set; get; }
    
    [Option("host", Required = false, Default = "127.0.0.1")]
    public string Host { set; get; }
    
    [Option("backlog", Required = false, Default = 256)]
    public int Backlog { set; get; }
}