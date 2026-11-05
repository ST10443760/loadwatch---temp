using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using LoadWatch.Api.Controllers;
using LoadWatch.Api.Services;

namespace LoadWatch.Api.Tests;

// The demo endpoint and the checker together: a stage change reaches the area's topic once, with the data the
// phone needs to build its own notification
public class StageAlertTests(ApiFactory factory) : IClassFixture<ApiFactory>
{
    private static string Route(string areaId) => $"/api/stages/areas/{areaId}/test-change";

    [Fact]
    public async Task A_test_stage_change_sends_one_alert_to_the_area_topic()
    {
        var response = await PostAsync("eth-umhlanga", stage: 6);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var result = await response.Content.ReadFromJsonAsync<JsonElement>();
        Assert.Equal(6, result.GetProperty("stage").GetInt32());
        Assert.Equal("Sent", result.GetProperty("alert").GetString());

        var alert = Assert.Single(factory.StageAlerts.Sent, a => a.Topic == "area-eth-umhlanga");
        Assert.Equal(StageAlertMessage.Type, alert.Data["type"]);
        Assert.Equal("Umhlanga", alert.Data["areaName"]);
        Assert.Equal("6", alert.Data["stage"]);
        Assert.Equal(result.GetProperty("previousStage").GetInt32().ToString(), alert.Data["previousStage"]);
    }

    [Fact]
    public async Task Setting_the_same_stage_twice_alerts_only_once()
    {
        await PostAsync("eth-la-lucia", stage: 7);
        var second = await PostAsync("eth-la-lucia", stage: 7);

        Assert.Equal("NoChange", (await second.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("alert").GetString());
        Assert.Single(factory.StageAlerts.Sent, a => a.Topic == "area-eth-la-lucia");
    }

    [Fact]
    public async Task Without_the_key_or_with_the_wrong_one_the_endpoint_refuses()
    {
        var client = factory.CreateClient(token: null);

        var missing = await client.PostAsJsonAsync(Route("eth-berea"), new { stage = 4 });
        var request = new HttpRequestMessage(HttpMethod.Post, Route("eth-berea")) { Content = JsonContent.Create(new { stage = 4 }) };
        request.Headers.Add(StagesController.TestKeyHeader, "wrong-key");
        var wrong = await client.SendAsync(request);

        Assert.Equal(HttpStatusCode.Forbidden, missing.StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, wrong.StatusCode);
        Assert.DoesNotContain(factory.StageAlerts.Sent, a => a.Topic == "area-eth-berea");
    }

    [Fact]
    public async Task An_unknown_area_is_404_and_a_stage_above_8_is_400()
    {
        Assert.Equal(HttpStatusCode.NotFound, (await PostAsync("eth-nowhere", stage: 3)).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await PostAsync("eth-westville", stage: 9)).StatusCode);
    }

    private Task<HttpResponseMessage> PostAsync(string areaId, int? stage)
    {
        var request = new HttpRequestMessage(HttpMethod.Post, Route(areaId)) { Content = JsonContent.Create(new { stage }) };
        request.Headers.Add(StagesController.TestKeyHeader, ApiFactory.StageTestKey);
        return factory.CreateClient(token: null).SendAsync(request);
    }
}
