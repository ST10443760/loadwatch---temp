using System.Text.Json;
using System.Text.Json.Serialization;

namespace LoadWatch.Api.Json;

// Reads and writes enums as their exact names (e.g. POWER_OFF), the format the API contract uses.
// Unlike JsonStringEnumConverter it rejects numbers and differently-cased names, and its error message lists
// the allowed values instead of .NET type names. ValidationResponses prefixes the message with the field name.
public class StrictEnumConverterFactory : JsonConverterFactory
{
    public override bool CanConvert(Type typeToConvert) => typeToConvert.IsEnum;

    public override JsonConverter CreateConverter(Type typeToConvert, JsonSerializerOptions options) =>
        (JsonConverter)Activator.CreateInstance(typeof(StrictEnumConverter<>).MakeGenericType(typeToConvert))!;
}

public class StrictEnumConverter<T> : JsonConverter<T> where T : struct, Enum
{
    private static readonly string[] Names = Enum.GetNames<T>();

    private static readonly string AllowedValues = Names.Length == 2
        ? $"{Names[0]} or {Names[1]}"
        : $"one of {string.Join(", ", Names)}";

    public override T Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType == JsonTokenType.String && reader.GetString() is { } value && Names.Contains(value))
        {
            return Enum.Parse<T>(value);
        }

        throw new JsonException($"{ValidationResponses.FieldMessagePrefix}{AllowedValues}");
    }

    public override void Write(Utf8JsonWriter writer, T value, JsonSerializerOptions options) =>
        writer.WriteStringValue(value.ToString());
}
