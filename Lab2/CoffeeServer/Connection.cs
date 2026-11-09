using System.Net.Sockets;
using System.Text;
using System.Threading.Channels;
using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Serilog;

namespace CoffeeServer;

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
        Channel.CreateBounded<ReadOnlyMemory<byte>>(new BoundedChannelOptions(1024)
            { SingleReader = true, FullMode = BoundedChannelFullMode.Wait });

    private readonly Task _writeLoop;
    private int _disposed;
    private readonly CancellationTokenSource _cts = new();
    public CancellationToken CancelToken => _cts.Token;

    public void SetRoleFromJson(string firstRequest)
    {
        var req = JsonConvert.DeserializeObject<RoleModel>(firstRequest,
            new StringEnumConverter { AllowIntegerValues = false });

        if (req is null || !Enum.IsDefined(req.Role))
            throw new InvalidOperationException("Invalid role");

        SetRole(req.Role);
        Log.Information("Role {ReqRole} has been set for {SockRemoteEndPoint}", req.Role, Sock.RemoteEndPoint);
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
        catch (Exception e) when (e is SocketException or ObjectDisposedException)
        {
            Log.Information("The connection was closed");
        }
        catch (Exception e)
        {
            Log.Error(e, "An exception occured while writing message");
        }
        finally
        {
            _outbox.Writer.TryComplete();
        }
    }

    public void Write(string data)
    {
        if (!_outbox.Writer.TryWrite(ConvertStringToByteArray(data)) && !_cts.IsCancellationRequested)
            _cts.Cancel();   // client isn't reading: disconnect it
    }

    private static ReadOnlyMemory<byte> ConvertStringToByteArray(string data)
    {
        var bytes = Encoding.UTF8.GetBytes(data + "\r\n");
        return new ReadOnlyMemory<byte>(bytes);
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
        var obj = JsonConvert.SerializeObject(new
        {
            Error = error,
        });
        Write(obj);
    }

    public async Task WriteErrorAndFinish(string error)
    {
        WriteError(error);
        _outbox.Writer.TryComplete();
        await _writeLoop;
    }

    public void Dispose()
    {
        if (Interlocked.Exchange(ref _disposed, 1) == 1) return;

        _outbox.Writer.TryComplete(); // no new messages
        _writeLoop.Wait(Math.Min(20 * _outbox.Reader.Count, 500));

        try
        {
            if (Role is ERole.Provider)
                StoreService.Instance.RemoveProvider(this);
        }
        catch (Exception e)
        {
            Log.Error(e, "Failed to unsubscribe provider {EndPoint}", EndPoint);
        }
        finally
        {
            try
            {
                _cts.Cancel();
            }
            catch
            {
            }

            try
            {
                Sock.Shutdown(SocketShutdown.Both);
            }
            catch
            {
            }

            Sock.Close();
        }
    }
}