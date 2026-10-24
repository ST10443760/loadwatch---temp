using System.Net.Sockets;
using LoadWatch.Api.Data;
using Microsoft.Data.SqlClient;
using Microsoft.Extensions.Logging;

namespace LoadWatch.Api.Tests;

public class StartupRetryTests
{
    private readonly FakeClock _clock = new();
    private readonly ListLogger _logger = new();
    private readonly List<TimeSpan> _delays = [];

    // Waiting only moves the fake clock forward, so three minutes of retries take no real time
    private StartupRetry NewRetry() => new(_logger, _clock, (wait, _) =>
    {
        _delays.Add(wait);
        _clock.Advance(wait);
        return Task.CompletedTask;
    });

    // What SqlClient throws on Linux when Azure drops the connection to a paused database
    private static IOException ConnectionReset() =>
        new("Unable to read data from the transport connection: Connection reset by peer.", new SocketException((int)SocketError.ConnectionReset));

    [Fact]
    public async Task A_step_that_fails_twice_with_a_transient_error_then_succeeds_is_retried_until_it_works()
    {
        var attempts = 0;

        await NewRetry().RunAsync("Database migration", _ =>
        {
            attempts++;
            // Error 40613: the serverless database is paused and resuming
            return attempts <= 2 ? throw FakeSqlErrors.Create(40613, "Database is not currently available.") : Task.CompletedTask;
        });

        Assert.Equal(3, attempts);
        Assert.Equal([TimeSpan.FromSeconds(2), TimeSpan.FromSeconds(4)], _delays);
        var warnings = _logger.Entries.Where(e => e.Level == LogLevel.Warning).Select(e => e.Message).ToList();
        Assert.Equal(2, warnings.Count);
        Assert.Contains("attempt 1", warnings[0]);
        Assert.Contains("attempt 2", warnings[1]);
        Assert.DoesNotContain(_logger.Entries, e => e.Level == LogLevel.Critical);
    }

    [Fact]
    public async Task A_permanent_error_such_as_a_wrong_password_is_not_retried()
    {
        // Error 18456: login failed, which waiting will never fix
        var wrongPassword = FakeSqlErrors.Create(18456, "Login failed for user 'loadwatch'.");
        var attempts = 0;

        var thrown = await Assert.ThrowsAsync<SqlException>(() => NewRetry().RunAsync("Database migration", _ =>
        {
            attempts++;
            throw wrongPassword;
        }));

        Assert.Same(wrongPassword, thrown);
        Assert.Equal(1, attempts);
        Assert.Empty(_delays);
        Assert.Contains(_logger.Entries, e => e.Level == LogLevel.Critical && e.Message.Contains("permanent"));
    }

    [Fact]
    public async Task A_real_permanent_error_from_SQL_Server_is_not_retried()
    {
        var attempts = 0;

        var thrown = await Assert.ThrowsAsync<SqlException>(() => NewRetry().RunAsync("Database seeding", async ct =>
        {
            attempts++;
            await using var connection = new SqlConnection(ApiFactory.ServerConnectionString);
            await connection.OpenAsync(ct);
            await using var command = new SqlCommand("SELECT * FROM dbo.TableThatDoesNotExist", connection);
            await command.ExecuteNonQueryAsync(ct);
        }));

        Assert.Equal(208, thrown.Number);
        Assert.Equal(1, attempts);
        Assert.Empty(_delays);
    }

    [Fact]
    public async Task A_database_that_never_comes_back_fails_loudly_within_three_minutes()
    {
        var attempts = 0;

        var thrown = await Assert.ThrowsAsync<TimeoutException>(() => NewRetry().RunAsync("Database migration", _ =>
        {
            attempts++;
            throw ConnectionReset();
        }));

        Assert.IsType<IOException>(thrown.InnerException);
        Assert.True(_clock.Elapsed <= StartupRetry.MaxDuration, $"Waited {_clock.Elapsed}");
        Assert.True(attempts > 3);
        // The wait grows each time and is capped
        Assert.Equal(TimeSpan.FromSeconds(2), _delays[0]);
        Assert.True(_delays.Zip(_delays.Skip(1)).All(pair => pair.Second >= pair.First));
        Assert.All(_delays, wait => Assert.True(wait <= StartupRetry.MaxDelay));
        Assert.Contains(_logger.Entries, e => e.Level == LogLevel.Critical && e.Message.Contains("giving up"));
    }

    [Theory]
    [InlineData(40613)]
    [InlineData(40197)]
    [InlineData(40501)]
    [InlineData(49918)]
    [InlineData(4060)]
    [InlineData(10054)]
    public void Azure_SQL_resume_and_connection_reset_errors_are_transient(int number) =>
        Assert.True(TransientSqlErrors.IsTransientNumber(number));

    [Theory]
    [InlineData(18456)] // Login failed, for example a wrong password
    [InlineData(208)]   // Invalid object name
    [InlineData(2627)]  // Unique constraint violation
    public void Permanent_errors_are_not_transient(int number) =>
        Assert.False(TransientSqlErrors.IsTransientNumber(number));

    [Fact]
    public void A_connection_reset_is_recognised_even_when_wrapped()
    {
        Assert.True(TransientSqlErrors.IsTransient(new InvalidOperationException("Migration failed", ConnectionReset())));
        Assert.False(TransientSqlErrors.IsTransient(new InvalidOperationException("Bad SQL")));
    }

    private sealed class FakeClock : TimeProvider
    {
        private static readonly DateTimeOffset Start = new(2026, 10, 23, 6, 0, 0, TimeSpan.Zero);
        private DateTimeOffset _now = Start;

        public TimeSpan Elapsed => _now - Start;

        public override DateTimeOffset GetUtcNow() => _now;

        public void Advance(TimeSpan by) => _now += by;
    }

    private sealed class ListLogger : ILogger
    {
        public List<(LogLevel Level, string Message)> Entries { get; } = [];

        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;

        public bool IsEnabled(LogLevel logLevel) => true;

        public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception, Func<TState, Exception?, string> formatter) =>
            Entries.Add((logLevel, formatter(state, exception)));
    }
}
