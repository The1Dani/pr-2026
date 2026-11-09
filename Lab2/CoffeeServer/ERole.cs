using System.Text.Json.Serialization;
using Newtonsoft.Json.Converters;

namespace CoffeeServer;

[JsonConverter(typeof(StringEnumConverter))]
public enum ERole
{
    NoRole,
    Client,
    Provider,
}
