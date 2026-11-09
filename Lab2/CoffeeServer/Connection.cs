using System.Net.Sockets;
using System.Text;
using System.Threading.Channels;
using Newtonsoft.Json;

namespace CoffeeServer;

class ErrorDto
{
    public ErrorDto(string error)
    {
        Error = error;
    }

    public string Error { get; set; }
}

public class Connection : IDisposable
{
    public Connection(Socket client)
    {
        Sock = client;
        _writeLoop = WriteLoopAsync();
        EndPoint = Sock.RemoteEndPoint?.ToString() ?? "None";
    }

    private class RoleModel
    {
        [JsonProperty(Required = Required.Always)]
        public ERole Role { get; set; }
    }

    public Socket Sock { get; }
    public ERole Role;
    public readonly string EndPoint;

    private readonly Channel<ReadOnlyMemory<byte>> _outbox =
        Channel.CreateUnbounded<ReadOnlyMemory<byte>>(new UnboundedChannelOptions { SingleReader = true });

    private readonly Task _writeLoop;
    private int _disposed;
    public readonly CancellationToken CancelToken = new();
    
    public void SetRoleFromJson(string firstRequest)
    {
        var req = JsonConvert.DeserializeObject<RoleModel>(firstRequest);
        if (req is not null)
        {
            SetRole(req.Role);
            Console.WriteLine($"Role {req.Role} has been set for {Sock.RemoteEndPoint}");
        }
        else
        {
            throw new Exception("Invalid role");
        }
    }

    public void SetRole(ERole role)
    {
        Role = role;
    }

    private async Task WriteLoopAsync()
    {
        try
        {
            await foreach (var message in _outbox.Reader.ReadAllAsync())
            {
                await WriteToSocketAsync(message);
            }
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
            _outbox.Writer.TryComplete();
        }
    }

    public void Write(string data)
    {
        ReadOnlyMemory<byte> bytes = Encoding.UTF8.GetBytes(data + "\r\n").AsMemory();
        _outbox.Writer.TryWrite(bytes);
    }

    private async Task WriteToSocketAsync(ReadOnlyMemory<byte> data)
    {
        for (var sent = 0; sent < data.Length;)
            sent += await Sock.SendAsync(data[sent..], SocketFlags.None);
    }

    public void WriteObject(object obj)
    {
        Write(JsonConvert.SerializeObject(obj));
    }

    public void WriteError(string error)
    {
        var obj = JsonConvert.SerializeObject(new ErrorDto(error));
        Write(obj);
    }

    public void Dispose()
    {
        if (Interlocked.Exchange(ref _disposed, 1) == 1) return;

        _outbox.Writer.TryComplete();                       // no new messages
        try { _writeLoop.Wait(TimeSpan.FromSeconds(2)); }   // flush what's queued
        catch { }

        try { Sock.Shutdown(SocketShutdown.Both); } catch { }
        Sock.Close();
    }
}