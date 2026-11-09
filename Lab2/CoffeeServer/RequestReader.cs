using System.Net.Sockets;
using System.Text;
using Serilog;

namespace CoffeeServer;

public class RequestReader(Connection con, StringBuilder sb)
{
    private const int BufferSize = 1024;
    private readonly Decoder _decoder = Encoding.UTF8.GetDecoder();
    private readonly byte[] _buffer = new byte[BufferSize];
    private readonly char[] _chars = new char[Encoding.UTF8.GetMaxCharCount(BufferSize)];
    private bool _crSeen = false;
    private readonly Queue<string> _readyStrings = new();
    private bool Ready => _readyStrings.Count > 0;

    public async Task<bool> Read()
    {
        
        if (Ready)
            return true;
        
        while (true)
        {
            var charCount = 0;
            try
            {
                var count = await con.Sock.ReceiveAsync(_buffer);
                charCount = _decoder.GetChars(_buffer, 0, count, _chars, 0);
                if (count <= 0)
                {
                    return false;
                }
            }
            catch (Exception e) when (e is SocketException or ObjectDisposedException)
            {
                Log.Logger.Debug(e, $"Connection {con.EndPoint} closed.");
                return false;
            }

            for (var i = 0; i < charCount; i++)
            {
                var ch = _chars[i];

                sb.Append(ch.ToString());
                if (ch == '\r')
                {
                    _crSeen = true;
                    continue;
                }

                if (_crSeen && ch == '\n')
                {
                    // Return true to tell the connection is still going
                    MakeString();
                }

                _crSeen = false;
            }

            if (Ready)
                return true;

            if (sb.Length > 1024 * 1024) //1MiB
            {
                await con.WriteErrorAndFinish("The message is larger than 1Mib bigger than allowed!");
                return false;
            }
        }
    }

    private void MakeString()
    {
        var str = sb.ToString().Trim();
        sb.Clear();
        _readyStrings.Enqueue(str);
    }
    
    public string GetString()
    {
        return _readyStrings.Dequeue();
    }
    
}