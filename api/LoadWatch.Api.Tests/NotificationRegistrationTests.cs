using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Tests;

public class NotificationRegistrationTests(ApiFactory factory) : IClassFixture<ApiFactory>
{
    private const string Route = "/api/notifications/registrations";

    [Fact]
    public async Task A_new_token_is_stored_for_the_signed_in_user_with_201()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-reg-new"));

        var response = await client.PostAsJsonAsync(Route, new { fcmToken = "token-new", platform = "android" });

        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        await using var db = factory.CreateDbContext();
        var stored = await db.DeviceTokens.AsNoTracking().SingleAsync(d => d.Token == "token-new");
        Assert.Equal("user-reg-new", stored.UserId);
        Assert.Equal("android", stored.Platform);
    }

    [Fact]
    public async Task Sending_the_same_token_again_is_200_and_keeps_one_row()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-reg-again"));

        await client.PostAsJsonAsync(Route, new { fcmToken = "token-again", platform = "android" });
        var second = await client.PostAsJsonAsync(Route, new { fcmToken = "token-again", platform = "android" });

        Assert.Equal(HttpStatusCode.OK, second.StatusCode);
        await using var db = factory.CreateDbContext();
        Assert.Equal(1, await db.DeviceTokens.CountAsync(d => d.Token == "token-again"));
    }

    [Fact]
    public async Task A_token_registered_by_another_account_moves_to_the_new_user()
    {
        await factory.CreateClient(ApiFactory.CreateToken("user-reg-first"))
            .PostAsJsonAsync(Route, new { fcmToken = "token-shared-phone", platform = "android" });

        var response = await factory.CreateClient(ApiFactory.CreateToken("user-reg-second"))
            .PostAsJsonAsync(Route, new { fcmToken = "token-shared-phone", platform = "android" });

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        await using var db = factory.CreateDbContext();
        Assert.Equal("user-reg-second", (await db.DeviceTokens.AsNoTracking().SingleAsync(d => d.Token == "token-shared-phone")).UserId);
    }

    [Theory]
    [InlineData("", "android", "fcmToken")]
    [InlineData("token-ios", "ios", "platform")]
    public async Task An_empty_token_or_unknown_platform_is_400_naming_the_field(string token, string platform, string field)
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-reg-invalid"));

        var response = await client.PostAsJsonAsync(Route, new { fcmToken = token, platform });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains(field, await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task Registering_without_signing_in_is_401()
    {
        var response = await factory.CreateClient(token: null)
            .PostAsJsonAsync(Route, new { fcmToken = "token-anonymous", platform = "android" });

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }
}
