using System.Globalization;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace LoadWatch.Api.Json;

// Writes every instant as ISO-8601 UTC with a Z suffix (e.g. 2026-08-23T20:04:11Z), matching the API contract.
// Any ISO-8601 offset is accepted on input.
public class UtcDateTimeOffsetConverter : JsonConverter<DateTimeOffset>
{
    private const string Format = "yyyy-MM-dd'T'HH:mm:ss.FFF'Z'";

    public override DateTimeOffset Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options) =>
        reader.GetDateTimeOffset();

    public override void Write(Utf8JsonWriter writer, DateTimeOffset value, JsonSerializerOptions options) =>
        writer.WriteStringValue(value.UtcDateTime.ToString(Format, CultureInfo.InvariantCulture));
}
