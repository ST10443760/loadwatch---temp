using FirebaseAdmin;
using FirebaseAdmin.Messaging;
using Google.Apis.Auth.OAuth2;

namespace LoadWatch.Api.Services;

// Sends stage-change alerts through Firebase Cloud Messaging with the Admin SDK. The service account key comes
// from the FIREBASE_SERVICE_ACCOUNT_JSON environment variable (an App Service setting on Azure) and is never
// committed. Without it the API still runs: each alert is logged and skipped instead.
public sealed class FirebaseStageAlertSender : IStageAlertSender
{
    public const string ServiceAccountVariable = "FIREBASE_SERVICE_ACCOUNT_JSON";

    // A named app, so it never clashes with another FirebaseApp in the same process
    private const string AppName = "loadwatch-stage-alerts";

    private readonly FirebaseMessaging? _messaging;
    private readonly ILogger<FirebaseStageAlertSender> _logger;

    public FirebaseStageAlertSender(IConfiguration configuration, ILogger<FirebaseStageAlertSender> logger)
    {
        _logger = logger;
        var json = configuration[ServiceAccountVariable];
        if (string.IsNullOrWhiteSpace(json))
        {
            logger.LogWarning("{Variable} is not set, so stage change alerts will be logged but not sent", ServiceAccountVariable);
            return;
        }

        try
        {
            var credential = CredentialFactory.FromJson<ServiceAccountCredential>(json).ToGoogleCredential();
            var app = FirebaseApp.GetInstance(AppName) ?? FirebaseApp.Create(new AppOptions { Credential = credential }, AppName);
            _messaging = FirebaseMessaging.GetMessaging(app);
            logger.LogInformation("Stage change alerts will be sent through Firebase Cloud Messaging");
        }
        catch (Exception ex)
        {
            // The message never includes the key itself
            logger.LogWarning("{Variable} could not be read as a service account key ({Error}), so stage change alerts will not be sent",
                ServiceAccountVariable, ex.GetType().Name);
        }
    }

    public async Task<StageAlertSendResult> SendAsync(
        string topic, IReadOnlyDictionary<string, string> data, CancellationToken ct)
    {
        if (_messaging is null)
        {
            _logger.LogWarning("Skipped the stage change alert for topic {Topic}: Firebase is not configured", topic);
            return StageAlertSendResult.Skipped;
        }

        var message = new Message
        {
            Topic = topic,
            Data = data,
            // High priority wakes the phone so the alert shows straight away; a stage change an hour old is no
            // longer worth showing
            Android = new AndroidConfig { Priority = Priority.High, TimeToLive = TimeSpan.FromHours(1) },
        };

        try
        {
            var messageId = await _messaging.SendAsync(message, ct);
            _logger.LogInformation("Sent the stage change alert to topic {Topic} ({MessageId})", topic, messageId);
            return StageAlertSendResult.Sent;
        }
        catch (FirebaseException ex)
        {
            _logger.LogError(ex, "Firebase did not accept the stage change alert for topic {Topic}", topic);
            return StageAlertSendResult.Failed;
        }
    }
}
