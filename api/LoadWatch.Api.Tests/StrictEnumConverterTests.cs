using System.Text.Json;
using LoadWatch.Api.Json;
using LoadWatch.Api.Models;

namespace LoadWatch.Api.Tests;

public class StrictEnumConverterTests
{
    private static readonly JsonSerializerOptions Options = new() { Converters = { new StrictEnumConverterFactory() } };

    [Fact]
    public void Exact_names_round_trip()
    {
        Assert.Equal(SyncStatus.RETRYING, JsonSerializer.Deserialize<SyncStatus>("\"RETRYING\"", Options));
        Assert.Equal("\"POWER_BACK_ON\"", JsonSerializer.Serialize(OutageEventType.POWER_BACK_ON, Options));
    }

    [Theory]
    [InlineData("\"BOGUS\"")]
    [InlineData("\"synced\"")]
    [InlineData("1")]
    public void Anything_else_lists_the_allowed_sync_statuses(string json)
    {
        var exception = Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<SyncStatus>(json, Options));

        Assert.Equal("must be one of PENDING, SYNCED, RETRYING, REJECTED", exception.Message);
    }

    [Fact]
    public void A_two_value_enum_reads_as_either_or()
    {
        var exception = Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<OutageEventType>("\"OFF\"", Options));

        Assert.Equal("must be POWER_OFF or POWER_BACK_ON", exception.Message);
    }
}
